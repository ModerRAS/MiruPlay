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
| **Native runtime packaging** — two copies of a shared C++ runtime get merged and the wrong one wins | #71 added `audio-dsp-native` + `pickFirsts += "**/libc++_shared.so"`; the app then packaged the NDK `libc++_shared.so` instead of `player-mpv-android`'s prebuilt one. `libmpv.so` (the libass host) failed to dlopen and every embedded ASS sample fell back to Media3's `SsaParser`, which drops zlib-escaped dialogue → subtitles silently stopped rendering | Inspect the built APK: `lib/*/libc++_shared.so` must be the player-mpv prebuilt copy (it exports `__from_chars_floating_point`), and there must be exactly one. On device, play an embedded-ASS episode and confirm `MiruLibass: Resolved libass API from packaged libmpv.so` + a visible subtitle |
| **Native lifecycle races** — a native player aborts when its window is invalidated outside the lifecycle it expects; JVM tests cannot see it | Embedded mpv's `mediacodec_embed` VO asserts (`vo_mediacodec_embed.c: WinID != 0 && WinID != -1`) when the surface is detached/EOF tears down while the VO is still active → SIGABRT in the mpv `vo` thread, most easily after EOF or when the next source starts during teardown | On the device with the mpv backend: play a short file to EOF and press BACK mid-play; logcat must contain no `Fatal signal 6` / `vo_mediacodec_embed` assertion and the app process must survive |
| **Cross-surface state** — TV settings ↔ WebAPI ↔ WebUI drift | Settings parity incidents | Change from both surfaces and re-check the other |
| **Container sample mangling** — the extractor (or its version) silently alters sample bytes before the app sees them | Media3 truncates SSA blocks at the first NUL (HandBrake legacy); zlib-compressed ASS bodies legitimately contain NULs, so whole karaoke lines vanished while unit tests (synthetic plaintext) stayed green | Play a real zlib-compressed ASS file on device; `Skipped malformed ASS sample` must stay ~0 and every styled line must render |
| **Priority-list gaps** — a codec/backend priority table covers only the codecs it was written for; the rest fall through to a catch-all ordered by something meaningless | AVC decoders all scored 4 in the default branch, alphabetical tiebreak put software ahead of vendor hardware → all H.264 software-decoded | Play one H.264 + one HEVC file on device; HUD/`MiruHealth` must show vendor `[HW]` decoders with ~0 drops for both |

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
| Topping DAC (USB HID) | Plug the DAC into the HK1, grant USB permission, set volume at a safe level and observe the FRONT PANEL move; push a preset and confirm the display shows the bands |
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
- ✦ **libass native host**: the APK ships exactly one `libc++_shared.so` (the `player-mpv-android` prebuilt one) and `libmpv.so` dlopens — `MiruLibass: Resolved libass API from packaged libmpv.so` in logcat; if it is missing, ASS subtitles render as nothing (SsaParser drops every line)
- ✦ **Embedded mpv backend** (`EXPERIMENTAL_MPV_EMBEDDED`): fresh play renders picture; playing a short file to EOF AND pressing BACK mid-play both leave the app alive (no `Fatal signal 6` / `vo_mediacodec_embed.c` assertion); the picture must still render after re-entering playback
- ✦ **Playback progress**: resume position, save on stop, shown in library UI
- ✦ **MediaSession**: background playback, `/api/playback/command`, status reporting

### 5.2 Audio DSP & measurement (`audio-dsp-core`, `audio-dsp-native`, `audio-measure-core`, `audio-measure-android`)
Re-verify when: DSP plan compiler, FIR/biquad designers, native bridge, measure pipeline, calibration, or their routes change.

- ✦ **PEQ presets** apply to live playback and persist
- ✦ **Sweep measurement**: generate → capture → analyze → measured preset apply
- ✦ **Mic calibration** (.cal import/activate/list/download) doesn't disturb existing presets
- ✦ **REW EQ import** (`RewEqParser`)
- ✦ DSP off ⇒ bit-transparent passthrough (hear no difference, no resample)
- ✦ **Native DSP ABI**: `audio-dsp-native` links libc++ statically on purpose, so it must never contribute a second `libc++_shared.so` to the app merge; after touching its CMake/STL args or the app `packaging.jniLibs` block, rebuild the APK and run `:audio-dsp-native:connectedDebugAndroidTest` (`NativeDspBridge.isAvailable()=true`)
- ✦ **DSP section relocation**: TV 设置「音频 DSP」分区与 WebUI「音频 DSP」视图独立于播放设置；两侧入口都能打开 DSP 开关/预设/测量/校准，播放设置不再包含 DSP
- ✦ **RECORD_AUDIO runtime permission**: 首次扫频测量触发系统权限弹窗；授权后自动开始测量；拒绝后显示引导文案且不崩溃
- ✦ **扫频测量预检**（WebUI）：进入「音频 DSP」后显示麦克风检测结果（检测到/不可用 + 原因）与 4 步操作清单；不可用时「在设备上测量」按钮置灰
- ✦ **扫频全流程**：开始 → 播放双扫频（~10 s，无起播/结尾爆音）→ 分阶段进度文案 → 分析阶段提示 → 结果滚动可见；有效结果提示「应用为预设」，无效显示人话原因（时钟漂移边界/不一致/输入过载/IR 不锐利）
- ✦ **漂移边界 fail-closed**：估计到达 ±15 ppm 搜索边界时测量无效（不再产出 0 滤波器的"有效"退化结果）
- ✦ 交叉检查：测量用的 AudioTrack 独立于播放器 DSP 链；扫频前后正常播放、PEQ 预设应用不受影响
- ✦ **TV 房间校准向导**（音频 DSP →「房间校准向导」）：全屏向导 ① 检查设备页显示输出设备/麦克风/校准状态 + 粉红噪音开/停（停止后无残留音）→ ② 开始扫频（含 RECORD_AUDIO 权限门）→ 分段进度（无逐帧动画，主线程不占满）→ ③ 结果页显示人话原因（漂移边界/不一致等）或频响曲线 + 滤波器列表 + 「一键校准（全部声道）」→ 完成页显示自动命名的校准预设（房间校准 MM-dd HH:mm）
- ✦ 向导重开时若有上次测量结果直接落在结果页；Back 键关向导且噪音自动停止
- ✦ 交叉检查：向导用的测量流与旧「播放扫频并测量」chip、WebAPI measure/run 同源（AudioMeasureController）；改动后两条路径都仍能跑完并正确报告无效原因

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

## 8. Feature log (appended per PR)

### ASS NUL-truncation fix + playback health monitoring + AVC hardware decode (v2.15)

Own checks (all observed on HK1):

- Play an MKV with zlib-compressed embedded ASS (e.g. LV999 S01E02 ASSx2) OP: karaoke wipe + static lines render complete (L0+L1 layers); `Skipped malformed ASS sample` in logcat stays ~0 (one documented all-sentinel residual at most)
- TV settings → 播放 → 播放性能监测 → on: HUD appears top-left during playback; `MiruHealth`/`PlaybackHealth` lines flow in logcat AND persist to files/logs (run-as readable); toggle survives force-stop/restart/reinstall
- Play one H.264 file: HUD must show the vendor decoder `[HW]` (e.g. `c2.rk.avc.decoder`) with ~0 drops (was: `c2.android.avc.decoder` software with majority drops)
- Play one HEVC file: still vendor `[HW]` (e.g. `c2.rk.hevc.decoder`)

Cross-checks:

- Normal dialogue + no-subtitle episodes play unchanged; HDR software-decode preferences still honored (selector unit tests + default HEVC order untouched)
- libass native host: single `libc++_shared.so` per ABI in APK, `MiruLibass: Resolved libass API from packaged libmpv.so` on device
- Settings parity TV ↔ WebAPI ↔ WebUI for the new toggle (WebUI playback form switch; browser interaction not device-verified — no browser in test env)

### App i18n zh-CN/en-US/zh-TW/zh-HK/en-GB + in-app language switch (v2.16)

Own checks (all observed on HK1):

- Settings → Scan → Language: 6 chips render (Follow system/简体中文/繁體中文（台灣）/繁體中文（香港）/English (US)/English (UK)); currently selected chip highlighted
- Manual switch via prefs/WebAPI to en-US + force-stop/restart: home shows Discover/Search/Scan/Settings; settings menu/summaries render English with correct spacing ("Chinese Subtitles", not "ChineseSubtitles")
- Nested UiText resolution verified on device: source rows show "Local · Reachable" (was: "本地 · 可连接" — composable-path nested args fell back to Chinese while unit tests stayed green, see failure class below)
- Manual switch to zh-TW + restart: home shows 探索/搜尋/掃描/設定 (Traditional + TW wording, proves values-zh-rTW region matching)
- Reset to follow-system (delete app_language pref) + restart: back to Simplified on this zh-CN device, no crash (attachBaseContext reads prefs before applicationContext exists — this crashed on first device run: NPE on getSharedPreferences in attachBaseContext)
- Music mode chip in Scan panel: third chip reads Music (was: Anime twice — pre-existing copy bug fixed in passing)
- No AndroidRuntime:E on cold start, settings walk, or locale restart

Cross-checks:

- Existing media sources/scanning/playback untouched (no behavior change, pure copy move); full `./gradlew test` + `lint` green including MissingTranslation
- WebAPI/WebUI parity for the new setting: GET/PUT /api/settings/locale round-trip covered by WebControlSettingsRouteTest; WebUI scan card shows the language selector bound to the same store
- Robolectric UI tests assert via resolve(appContext), never hardcoded copy — locale-independent under any JVM default locale

New failure class (section 1 candidate):

- **Composed-path divergence** — the JVM-tested path and the device-rendered path resolve shared data through different code: UiText.resolve(context) (tested, correct) vs UiText.resolve() composable (untested, silently fell back to Chinese for nested args). Tests green, device half-Chinese. Check: every resolution entry point used in production must be exercised by at least one test with nested args, or all entries must funnel through a single tested implementation (as now: composable delegates to the context overload).

### Generic RSS online source (verified with ANi feed) + proxied playback

Own checks (all observed on HK1, proxy 192.168.63.94:7890):

- Direct HTTPS without proxy: `SocketTimeoutException` on feed fetch (443 filtered); with proxy in source connectionInfo: full scan completes (53 series dirs / 106 entries / 54 videos in ~105s), episode S/E parsed correctly on device (e.g. 黑色五葉草 S02E173)
- Direct playback of an ANi URL through proxy: first frame ~4.5s, `c2.rk.avc.decoder[HW]` 1080p, drop≈0; embedded CHT subs render
- Seek works over proxy (Range supported by origin): jumped 00:0x → 01:15, duration 23:45 known
- Transient proxy throughput dip stalls playback without error; seek-back recovers instantly (Exo buffers, no crash) — environmental, not an app bug
- No AndroidRuntime:E during scan/playback/seek

Cross-checks:

- Existing LOCAL/WEBDAV/SMB sources, scanner, WebDAV warmup, and longest-root matching untouched (id-lookup change scoped to ANI only — a broader change broke `configFor falls back to uri matching...` and was reverted to the scoped version)
- Full `./gradlew test` + `lint` green; new unit tests: ANi RSS/title/season parsing, DashEpisodePattern, AniMediaSource (MockWebServer incl. 206 probe), resolver ANI-proxy test
- Platform `MediaExtractor`/raw `HttpURLConnection` probes must never touch proxied URLs unauthenticated: container probe now opens connections through the configured proxy + UA; runtime extractor probe skips proxied sources (it would otherwise hang for minutes gating Exo start — this exact hang was observed on device before the fix)

New failure class (section 1 candidate):

- **Probe-gates-playback on unreachable networks** — a pre-playback metadata probe using a non-proxy transport (platform MediaExtractor, raw HttpURLConnection) hangs instead of failing fast, blocking the player start behind it with zero log signal. Check: every pre-playback network access must either share the playback transport/proxy config or have a hard timeout + skip path; verify on device with the origin blocked (direct connection must fail fast, proxy path must play).
