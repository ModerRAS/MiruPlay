# Pre-PR Checklist

This checklist exists to catch one specific class of bug: **the kind where every unit test passes but the feature is broken in real use** — like v2.10.727, where 194 tests were green and the app could not play a single video (`Unexpected runtime error` on every attempt). Unit tests are CI's job; this manual check is for the gap between "tests green" and "works for the user".

Run it before opening (or pushing a branch for) any PR to `master`. It grows over time: **every feature PR adds its own items** (see the end).

## 1. The principle: test what unit tests cannot see

Unit tests run components in isolation, with tidy inputs, in a JVM. The bugs that survive them share these shapes — check each shape your change could hit:

| Failure class (tests green, feature broken) | Historical incident in this repo | How to check |
|---|---|---|
| **Runtime composition** — components wired together at runtime, tested only apart | #77: `DspAudioProcessor` + `MusicSrcBypassProcessor` both correct alone; the chain crashed on the first empty-buffer drain | Compose the real chain/wiring in a test AND drive it the way production drives it |
| **Boundary inputs** — empty/zero-length, EOF, flush, reconfigure, rapid repeats | #77: crash was on an EMPTY buffer drain before any audio played | Exercise the composed system with empty, EOF, flush, format-change, repeated calls |
| **Real user operation order** — the exact sequence a user does, not the sequence a test does | #77: crash hit every time a user pressed play, never in a test | Walk the real flow on the device: launch → browse → play → seek → exit → resume |
| **Real environment** — real codecs, AudioTrack/HAL, sample rates, network paths | #77: only manifests on-device (device native rate, real MediaCodec) | Verify on real hardware (HK1), not emulator/JVM |
| **Real data shapes** — real files/feeds the tests don't model | Episode-count inflation regression; CUE/整轨 music files | Test with real library content on the device |
| **Real transport quirks** — URL encoding, redirects, timeouts | WebDAV URL encoding (`+`→`%20`, per-segment) | Play through the actual remote source, not a local fixture |
| **Runtime state transitions** — toggling settings mid-play, restart mid-flow | DSP enable/disable while playing; scheduler double-run after restart | Toggle the setting ON DEVICE while the feature is running |
| **Cross-surface state** — TV settings ↔ WebAPI ↔ WebUI drift | Settings parity incidents | Change from both surfaces and re-check the other |

## 2. Real-device verification (mandatory)

A change that affects anything user-visible is **not done** until it has been operated on a real device and observed working. This is the part that catches "tests green but broken".

- [ ] Debug APK installed on the HK1 (`miruplay-adb-debug` skill: connect → install → restart)
- [ ] The feature **actually operated** to completion and the expected result **observed on screen** (ADB screenshot / UI dump for Compose UI; NanoKVM for video-surface evidence)
- [ ] No `Player error`, `AndroidRuntime:E`, or new OpenObserve error rows during the walk
- [ ] Web-facing changes verified from a real browser against the device (WebUI + WebAPI)
- [ ] Only CI/docs/pure-JVM changes are exempt — and the PR must say so explicitly

### Minimum device walk-through (act as the USER, not as the developer)

1. Cold launch → mode selection → navigate with the remote into the touched area
2. Library loads real content (WebDAV/115/local)
3. Play an episode: picture + sound; seek; resume position; back out cleanly
4. Settings round-trip for settings-related changes (toggle on device AND from WebUI; confirm both show it)
5. Restart the app and re-check anything with state (resume, scan results, settings)

### Change type → minimum device verification

| Change touches | Minimum device verification |
|---|---|
| `player-core` / audio / subtitles | Play an episode end-to-end along the affected path (DSP on/off, subtitle on/off); observe picture + sound + seek |
| `ui-tv` screens / navigation | Walk the screen flow with remote keys; screenshot evidence |
| `data` / repositories / Room | Open library + detail + play — data renders and persists after restart |
| `web-control` / WebUI | Drive the WebUI from a browser; hit the affected routes; parity toggle on TV side |
| `scanner` / `media-source` | Add/test/scan a real source on the device; content appears |
| DSP / measure / calibration | Run the flow from WebUI on-device; apply to live playback and observe the change |
| CI / docs / JVM-desktop-only | Exempt — state explicitly |

## 3. Composition-level test (when the change wires things together)

If the change touches anything **composed at runtime** — audio processor chains (`DspRenderersFactory`), renderer factories, media source adapters, data source factories, DI graphs, repository pipelines — add a test that:

- [ ] Instantiates the **real composition** (e.g. real `AudioProcessingPipeline` + `DefaultAudioSink.DefaultAudioProcessorChain`), driven like production (`DefaultAudioSink.processBuffers` semantics)
- [ ] Includes the boundary inputs from section 1: empty, EOF, flush/reconfigure, format change, rapid repeats
- [ ] Reference: `player-core/src/test/kotlin/com/miruplay/tv/player/MusicSrcBypassPipelineTest.kt`

## 4. Hard gates (CI already runs these — know their limits)

- [ ] `./gradlew assembleDebug`, `./gradlew test` (all modules), `./gradlew lint`
- [ ] `git status` clean of unrelated files; no line-ending flips (whole-file diff in `--stat` = endings rewritten, normalize before committing)
- These green only means the code compiles and behaves in isolation — they do **not** clear a user-facing PR. Sections 1–2 do.

## 5. Cross-check: existing features still work

Any module you touch, re-verify on the device every feature that consumes it. Items marked ✦ are regression items that must be extended when related features change.

### 5.1 Playback pipeline (`player-core`, `player-mpv-android`, `player-mpv`, `player-ijkplayer-android`)
Re-verify when: player-core change, new AudioProcessor/renderer, `DspRenderersFactory`, subtitle renderer, `MiruPlayMediaService`.

- ✦ **Video playback on all 3 backends** (Exo / IJK / mpv): picture + sound, seek, pause/resume, stop — source types still route to the right backend
- ✦ **Audio DSP chain**: enabled / disabled / toggled at runtime; PEQ presets incl. surround downmix + limiter; format change mid-play (48k ↔ 44.1k, gapless next episode)
- ✦ **Music SRC bypass**: SYSTEM / SOFTWARE modes with 48kHz and 44.1kHz sources
- ✦ **Subtitles**: libass render (embedded + external .ass/.srt); an episode WITHOUT subtitle tracks still plays
- ✦ **Playback progress**: resume position, save on stop, shown in library UI
- ✦ **MediaSession**: background playback, `/api/playback/command`, status reporting

### 5.2 Audio DSP & measurement (`audio-dsp-core`, `audio-dsp-native`, `audio-measure-core`, `audio-measure-android`)
Re-verify when: DSP plan compiler, FIR/biquad designers, native bridge, measure pipeline, calibration, or their routes change.

- ✦ **PEQ presets** apply to live playback and persist
- ✦ **Sweep measurement**: generate → capture → analyze → measured preset apply
- ✦ **Mic calibration** (.cal import/activate/list/download) doesn't disturb existing presets
- ✦ **REW EQ import** (`RewEqParser`)
- ✦ DSP off ⇒ bit-transparent passthrough (hear no difference, no resample)
- ✦ **DSP section relocation**: TV 设置「音频 DSP」分区与 WebUI「音频 DSP」视图独立于播放设置；两侧入口都能打开 DSP 开关/预设/测量/校准，播放设置不再包含 DSP
- ✦ **RECORD_AUDIO runtime permission**: 首次扫频测量触发系统权限弹窗；授权后自动开始测量；拒绝后显示引导文案且不崩溃
- ✦ **扫频测量预检**（WebUI）：进入「音频 DSP」后显示麦克风检测结果（检测到/不可用 + 原因）与 4 步操作清单；不可用时「在设备上测量」按钮置灰
- ✦ **扫频全流程**：开始 → 播放双扫频（~10 s，无起播/结尾爆音）→ 分阶段进度文案 → 分析阶段提示 → 结果滚动可见；有效结果提示「应用为预设」，无效显示人话原因（时钟漂移边界/不一致/输入过载/IR 不锐利）
- ✦ **漂移边界 fail-closed**：估计到达 ±15 ppm 搜索边界时测量无效（不再产出 0 滤波器的"有效"退化结果）
- ✦ 交叉检查：测量用的 AudioTrack 独立于播放器 DSP 链；扫频前后正常播放、PEQ 预设应用不受影响

### 5.3 Music mode (`ui-tv` music screens, `data`, player-core)
Re-verify when: music screens, music metadata, DSP chain, audio source handling.

- ✦ **Music library/album/player**: album list → detail → play (CUE / 整轨 / single tracks)
- ✦ Music DSP/SRC settings affect music playback without touching anime playback, and vice versa

### 5.4 Drama / anime modes (`ui-tv` mode, detail, library screens)
Re-verify when: navigation, mode selection, detail/library screens, metadata join logic.

- ✦ **Mode switching** anime ↔ drama ↔ music keeps state, doesn't leak sources
- ✦ **Library**: grid loads, refresh, scan doesn't duplicate entries
- ✦ **Episode dedup** by (season, episode) — file-count inflation stays fixed
- ✦ **Detail screen**: metadata join, episode list, comments via `bangumiEpisodeId` end-to-end

### 5.5 Media sources (`media-source`, `scanner`, MainActivity)
Re-verify when: source adapters, scanner, source config.

- ✦ **Local / WebDAV / SMB**: add, test (`/api/sources/test`), scan; WebDAV URL encoding in `resolvePlayableUri()` unchanged
- ✦ Scanner skips system dirs (`/proc`, `/sys` …)
- ✦ `test_local_path` intent hook still works

### 5.6 Metadata & data layer (`data`, `metadata`, `scraper`, `metadata-core`)
Re-verify when: Room schema, DAOs, repositories, Bangumi/TMDB clients, NFO.

- ✦ **Room migrations**: schema change ⇒ new migration + test; never bump version without one
- ✦ **Repository contracts**: interface + `*Impl` pairs in sync; no DB access outside `data`
- ✦ **Bangumi**: metadata fetch, episode comments, bangumi-archive sync routes
- ✦ NFO read/write round-trip

### 5.7 Cloud sync & RSS (`sync-engine`, `cloud-drive*`)
Re-verify when: RSS scheduler, cloud-drive adapters, token handling.

- ✦ **CloudDrive login/token/config/directories**; RSS subscription add/delete/list + run
- ✦ Scheduler starts once per launch, no double-run after restart

### 5.8 Web control (`web-control-core`, `web-control`, frontend)
Re-verify when: any route, DTO, server, or WebUI change. **Follow `docs/agents/settings-web-control-parity-checklist.md` for settings surfaces.**

- ✦ **Server boots** on 9978, `/api/info` responds; no route silently renamed/removed
- ✦ **WebUI (Vue)**: forms, playback control, library work against the real device
- ✦ **Settings parity** TV ↔ WebAPI ↔ WebUI, both directions
- ✦ `/api/proxy` still proxies media for WebUI playback

### 5.9 App maintenance (`app`, CI)
Re-verify when: app-update flow, logging, CI workflows.

- ✦ **App self-update**: check / download / install-permission; versionName logic
- ✦ **App update channel**: switch alpha/beta/stable in TV settings, WebAPI and WebUI; switching re-checks with the selected channel; channel persists across restart
- ✦ **Log upload** to OpenObserve; `/api/logs`, startup diagnostics
- ✦ CI green: `ci.yml` build + test + lint; release versioning unaffected (nightly retired)
- ✦ **Release channel flow**: after an alpha publish, latest.json contains `channels` with all published channels; promote via workflow_dispatch only edits the manifest and does not create a new release

### 5.10 Shared/JVM modules (`*-desktop`, `*-core` twins)
Re-verify when: shared core modules change.

- ✦ Twin modules compile and test on both targets

## 6. PR description requirements

- [ ] State what changed and **what was observed on the device** (flows walked, screenshots, logcat clean) — "tests passed" alone is not verification
- [ ] State which cross-check items from section 5 were exercised on device
- [ ] List any check you could NOT device-verify, with the reason

## 7. How to extend this checklist

Every feature PR appends to this file, in the same PR:

1. **Own check items** — how to operate the new feature on the device and what result to observe
2. **Cross-check items** — which existing feature shares state/chain/config with it, and how to prove that feature still works on the device
3. **New failure classes** — if you hit (or can imagine) a new "tests green but broken" mode, add a row to the table in section 1

The bar: every PR arrives with the feature operated and observed on real hardware, and the checklist keeps collecting every way "tests green" has lied to us.
