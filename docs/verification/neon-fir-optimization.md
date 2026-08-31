# NEON FIR 优化验证（HK1 双 ABI）

**分支** `feature/neon-fir-optimization` → `master` **PR #71**，提交 `932098c8..5008d463`（6 commits，含自适应 + 自动 headroom）

## 1. 变更总览（15 文件，master..HEAD）

* **新增 `audio-dsp-native`**（`com.miruplay.tv.audio.dsp`，`arm64-v8a`+`armeabi-v7a`）：`miruplay_dsp.so`（`arm64 17k / arm 12k`）、`CMake 3.22.1 / C++17 / -O3 -ffast-math`、`posix_memalign(16)`、`maxTaps 4096 × maxChannels 8` 一次分配、`FirContext` arena（`useDouble = taps<4096` 自适应）、`vmlaq_f32(4路)`/`vfmaq_f64(2路)`、`history 2*T` 镜像去 `%`、`max-page-size 16384`、`pickFirst libc++_shared.so`
* **`audio-dsp-core/LinearPhaseFirDesigner.kt`**：`O(n²) IDFT` → `O(n log n) FFT`。`NativeDspBridge.designFir` 优先，`Kotlin FFT` 回落（`fftRadix2 双精度`），`designLegacy` 保留用于基准。要求 `taps power-of-two` 保持。
* **`audio-dsp-core/StreamingDspProcessor.kt`**：新增 `NativeFirEngine`（`handle + channelGain` 池，`posix_memalign` arena），快路径 `LINEAR && AUTO_PRESERVE && 无 limiter/crossfade && 无 biquad && 统一 tapsLen` 时 `one JNI per batch` 批量 `NEON`，否则回落 Kotlin；`queuePlan` 走 `nativeUpdateTaps` 复用 `history` 不重建；新增 `release()`；`FilterState.firHistory` 改 `DoubleArray`（`64f` 精度）
* **`audio-dsp-core/NativeDspBridge.kt`**（新增）：`isAvailable/isNeonAvailable/create/release/reset/processArray/processDirect/updateTaps/designFir`，`loadLibrary("miruplay_dsp")` 失败回落
* **`player-core/DspAudioProcessor.kt`**：`onFlush/onReset` 释放 `native handle` 防泄漏，`player-core/build.gradle.kts` 依赖 `audio-dsp-native`
* **`app/build.gradle.kts` / `settings.gradle.kts`**：双 ABI `pickFirst`，引入新模块
* **`audio-dsp-core/FirBenchmarkTest.kt` + `audio-dsp-native/NativeDspBenchmarkInstrumentedTest.kt`**：设计/流/零分配基准，设备端 `vaddvq_f32/vaddvq_f64` 正确性校验

## 2. 内部精度与采样率

* `96kHz`：`sampleRateHz` 原样进 `compile` 按 `96k` 重算 `taps`，频覆到 `48kHz`，无限制。
* `24bit`：`输入 Float(32f 尾数 24bit) 1:1` 覆盖 `24bit PCM`，`输出 PCM_FLOAT` 到 `AudioTrack` 最短路径。
* **精度自适应（用户要求后调整）**：`LOW 1024 / MED 2048` 走 `double(64f, 2路)`，`HIGH 4096` 走 `float(32f, 4路)` 保实时。`最小相位` 始终 `Biquad Double(64f)`，`线性相位` `FIR` 按上表切换。
* **自动 headroom（用户要求后新增）**：`AudioDspPreset.autoHeadroom=true`（默认）时 `compile` 扫 `64点 log 20..20k` 求 `maxFilterGain+maxChannelGain+preamp`，`peak>0` 则 `preamp -= peak`（`clamp -24..12`），抬升 `+6dB` 自动降 `6dB` 防削波；`false` 时保持原 `preamp`（`LinearPhaseFirDesignerTest` 等 6dB 用例显式 `false` 保 6dB 预期）

## 3. 内存合约

`onConfigure` 一次 `FirContext` 预分配，音频回调内禁止 `new/malloc/vector`；`historyMirror 2*T` 线性点积去 `%`；`GetPrimitiveArrayCritical` 单次 `JNI/batch`；`nativeCreate/Release` 成对 + `Cleaner` 兜底；`dumpsys meminfo` 与 `simpleperf malloc` 验证无增长（`FirBenchmarkTest.benchmarkZeroAlloc` `delta -3MB GC`）。

## 4. 性能（HK1 RBOX K8，`4× Cortex-A53`，同一设备强装单/双 ABI 交叉验证）

> `NativeDspBenchmarkInstrumentedTest` 3/3 SUCCESS，`connectedDebugAndroidTest` 批量

### 4.1 离线设计（1 次 `nativeDesignFir`）

| taps | 64 位 `arm64` legacy→native | 32 位 `arm` legacy→native | 算法 |
|------|-----------------------------|---------------------------|------|
| 1024 | `205ms→0.25ms 835x` (`fft 39x`) | `307ms→0.26ms 1185x` (`52x`) | `1M→11k ops` |
| 2048 | `807ms→0.49ms 1641x` (`253x`) | `1210ms→0.44ms 2724x` (`299x`) | `4M→22k` |
| 4096 | `3278ms→1.0ms 3292x` (`647x`) | `4897ms→1.09ms 4504x` (`761x`) | `16M→49k` |
| `maxErr` | `0 / 1.8e-12 / 7.2e-12` | 同 | 一致 |

Host `x86` 同趋势：`15.8ms→0.4ms 35x / 59ms→0.5ms 111x / 236ms→0.16ms 1489x`。

### 4.2 实时卷积（`48kHz 立体声 1秒=48000帧`）

|  | 64 位标量→native | 64 位 realtime | 32 位标量→native | 32 位 realtime | 状态 |
|--|--|--|--|--|--|
| LOW 1024 | `1791→169ms 10.6x` | `5.89x (0.17s/1s)` | `2018→191ms 10.5x` | `5.22x` | 充裕 |
| MED 2048 | `3303→359ms 9.2x` | `2.78x` | `3745→393ms 9.5x` | `2.54x` | 充裕 |
| HIGH 4096 (自适应 32f) | `6432→745ms 8.6x`·双精度时 `0.73x` 非实时 | `1.34x` | `7319→829ms 8.8x` | `1.21x` | 自适应后实时 |

> `HIGH` 若强制 `64f` 则 `0.73x`（`1.37s/1s`）非实时，因 `NEON f64` 仅 `2路` vs `f32 4路` 腰斩；自适应切 `32f` 拉回 `1.34x`。

### 4.3 效果

` taps对称 peak 511/512 0.636`，`1kHz +6dB 5.99dB`，`脉冲前5 0`，`Streaming vs direct native maxErr 0.0`。

### 4.4 复现

```bash
./gradlew :audio-dsp-core:test --tests "*FirBenchmarkTest.benchmarkDesign" # host 35x-1489x
./gradlew :audio-dsp-native:connectedDebugAndroidTest # 设备端 835x-4504x / 8-10x
adb -s 192.168.63.237:5555 logcat -d | grep MiruDspBench
adb -s 192.168.63.237:5555 shell ls -l /data/app/*/com.miruplay.tv*/lib/*/libmiruplay_dsp.so
```

单 ABI 强装验证：改 `app/build.gradle.kts abiFilters` 单 `armeabi-v7a` 后 `primaryCpuAbi=armeabi-v7a`，`lib/arm 12k JNI_OnLoad abi=armeabi-v7a neon=1`。

## 5. 构建与回归

```
./gradlew :audio-dsp-core:test
./gradlew :audio-dsp-native:assembleDebug  # arm64+arm32 17k/12k
./gradlew :player-core:testDebugUnitTest
./gradlew :app:assembleDebug  # unzip -l | grep miruplay_dsp 双 so
```

`Zidoo 32位用户空间`（`process_is64bit=false`）单 `arm64` 会 `未安装应用`，双包已解。

## 6. 提交

`932098c8 feat(audio): neon fir for 32/64-bit with pre-allocated arena` - 初始 `32f` 全链  
`f134f19c test(audio): instrumented benchmark on HK1`  
`cf90063e feat(audio): fir double precision` - 全 `64f`（`HIGH` 非实时）  
`404ffe9f feat(audio): adaptive fir precision` - `HIGH 32f / LOW,MED 64f`  
`5008d463 feat(audio): auto headroom -抬升自动降增益防削波`  
`01c85576 docs(verification): complete neon fir changelog ...`

> 方案文档未纳入 PR（`docs/superpowers/plans/2026-08-29-neon-fir-optimization.md` 本地保留）。
