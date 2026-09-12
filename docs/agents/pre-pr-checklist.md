# Pre-PR Checklist

Run this before opening (or pushing a branch for) any PR to `master`. Goal: the PR should arrive as a normally usable build — new feature working, existing features unaffected.

This checklist grows over time: **every feature PR adds its own check items and cross-check items** to this document (see "How to extend").

## 1. Hard gates (never skip)

- [ ] `./gradlew assembleDebug` — full build, not just the touched module
- [ ] `./gradlew test` — **all modules**, not only the changed one. Module-scoped tests can pass while another module's tests break
- [ ] `./gradlew lint` — clean or explicitly justified warnings
- [ ] No `@Suppress` or type gymnastics added just to make compilation pass
- [ ] `git status` clean of unrelated files (scratch files, `tmp/`, `adb-artifacts/`, `build/`)

## 2. Composition-level testing (the EMPTY_BUFFER lesson)

v2.10.727 shipped a playback-breaking crash because two components (`DspAudioProcessor`, `MusicSrcBypassProcessor`) were correct in isolation but had never been exercised **together in the runtime chain** with boundary inputs. Unit tests that pass per-component do not protect composed systems.

If the change touches any component that gets **composed at runtime** — audio processor chains (`DspRenderersFactory`), renderer factories, media source adapters, data source factories, DI graphs, repository pipelines — then:

- [ ] Add a test that instantiates the **real composition** (real `AudioProcessingPipeline` / real chain / real adapter wiring), driven the way the production caller drives it (e.g. `DefaultAudioSink.processBuffers` semantics)
- [ ] Include boundary inputs: **empty input**, zero-length buffers, end-of-stream, format change mid-stream, flush/reconfigure, rapid repeated calls
- [ ] Reference example: `player-core/src/test/kotlin/com/miruplay/tv/player/MusicSrcBypassPipelineTest.kt` (regression test for the v2.10.727 crash)

## 3. Cross-check: existing features still work

Full feature inventory of the app. Rule of thumb: **any module you touch, re-verify every feature that consumes it** (column "re-verify when"). Items marked ✦ are regression items that must be extended when related features change.

### 3.1 Playback pipeline (`player-core`, `player-mpv-android`, `player-mpv`, `player-ijkplayer-android`)
Re-verify when: any player-core change, new AudioProcessor, new renderer, DspRenderersFactory, subtitle renderer, or MiruPlayMediaService change.

- ✦ **Video playback via all 3 backends** (Exo / IJK / mpv): play to picture+sound, seek, pause/resume, stop — backend selection (`PlaybackDataSourceFactory`, renderers factory wiring) still routes each source type to the right backend
- ✦ **Audio DSP chain**: DSP enabled + disabled + toggled at runtime; PEQ presets incl. surround downmix and limiter; empty-buffer drain; end-of-stream; format change mid-play (48kHz ↔ 44.1kHz episodes, gapless next-episode)
- ✦ **Music SRC bypass**: modes SYSTEM / SOFTWARE with 48kHz and 44.1kHz sources (see `MusicSrcBypassPipelineTest` for the composed-pipeline test this requires)
- ✦ **Subtitles**: libass/native ASS render for embedded + external .ass/.srt; subtitle monitor; episode with NO subtitle track doesn't break playback
- ✦ **Playback progress**: start position resume (`start_position_ms`), progress save on stop, progress surfaced in library UI
- ✦ **MediaSessionService**: background playback, external control commands (`/api/playback/command`), playback status reporting

### 3.2 Audio DSP & measurement (`audio-dsp-core`, `audio-dsp-native`, `audio-measure-core`, `audio-measure-android`)
Re-verify when: DSP plan compiler, FIR/biquad designers, native bridge, measure pipeline, calibration files, or their WebAPI routes change.

- ✦ **PEQ presets** apply to live playback (compile → stream) and persist
- ✦ **Sweep measurement**: generate sweep → capture → analyze → measured preset apply
- ✦ **Mic calibration** (.cal import/activate/list/download) doesn't disturb existing presets; `/api/audio-dsp/measure/calibration*` round-trips
- ✦ **REW EQ import** (`RewEqParser`, `/api/audio-dsp/import-rew`)
- ✦ DSP off ⇒ bit-transparent passthrough (no resample, no gain change)

### 3.3 Music mode (`ui-tv` music screens, `data`, player-core)
Re-verify when: music screens, music metadata, DSP chain, or audio source handling change.

- ✦ **Music library/album/player**: album list → album detail → play (CUE / 整轨 / single tracks)
- ✦ Music-mode DSP + SRC bypass settings apply to music playback without affecting anime playback and vice versa

### 3.4 Drama / anime modes (`ui-tv` mode, detail, library screens)
Re-verify when: navigation, mode selection, detail/library screens, or metadata join logic change.

- ✦ **App mode selection**: anime ↔ drama ↔ music switching keeps state and doesn't leak sources across modes
- ✦ **Library screen**: grid loads, filter/sort, content refresh; scan does not duplicate entries
- ✦ **Episode dedup**: episode counts use (season, episode) dedup — file-count inflation regression stays fixed
- ✦ **Detail screen**: metadata join, episode list, comment lookup via `bangumiEpisodeId` (PlaybackSource must carry it end-to-end)

### 3.5 Media sources (`media-source`, `scanner`, MainActivity)
Re-verify when: source adapters, scanner, or source config change.

- ✦ **Local / WebDAV / SMB** sources: add, test (`/api/sources/test`), scan; WebDAV URL encoding in `resolvePlayableUri()` unchanged (segments individually URL-encoded, `+`→`%20`)
- ✦ Scanner skips system dirs (`/proc`, `/sys` …) — do not add arbitrary dir scanning
- ✦ `test_local_path` intent hook still works (developer workflow depends on it)

### 3.6 Metadata & library data (`data`, `metadata`, `scraper`, `metadata-core`)
Re-verify when: Room schema, DAOs, repositories, Bangumi/TMDB clients, NFO read/write change.

- ✦ **Room migrations**: schema change ⇒ new migration + test; never bump version without one
- ✦ **Repository contracts**: interface + `*Impl` pairs stay in sync; no DB access outside `data` module
- ✦ **Bangumi**: metadata fetch, episode comments, bangumi-archive sync/upload/download routes
- ✦ NFO metadata read/write round-trip

### 3.7 Cloud sync & RSS (`sync-engine`, `cloud-drive*`)
Re-verify when: RSS scheduler, cloud-drive adapters, token handling change.

- ✦ **CloudDrive login/token/config/directories** routes; RSS subscription add/delete/list + run
- ✦ Scheduler starts on app launch and doesn't double-run after restart

### 3.8 Web control (`web-control-core`, `web-control`, `web-control/frontend`)
Re-verify when: any route, DTO, server, or WebUI change. **Follow `docs/agents/settings-web-control-parity-checklist.md` for settings surfaces.**

- ✦ **Server boots** on port 9978 and `/api/info` responds; no existing route silently renamed/removed (see route list in `web-control-core`)
- ✦ **WebUI (Vue frontend)**: settings forms, playback control, library view still work against the app; rebuild frontend assets if `frontend/` changed
- ✦ **Settings parity** TV ↔ WebAPI ↔ WebUI, including reverse direction
- ✦ `/api/proxy` still proxies media for WebUI playback

### 3.9 App maintenance features (`app`, CI)
Re-verify when: app-update flow, logging, CI workflows change.

- ✦ **App self-update**: check / download / install-permission flow; versionName logic (`baseAppVersionName` + CI run_number)
- ✦ **Log upload** to OpenObserve; `/api/logs`, log-download, startup diagnostics
- ✦ CI builds: `ci.yml` assembleDebug + test + lint green; nightly/release versioning unaffected

### 3.10 Shared/JVM modules (`*-desktop`, `*-core` twins)
Re-verify when: shared core modules (repository, metadata, scraper, media-source, sync-engine) change.

- ✦ `-core`/`-desktop` twin modules stay compiling on both targets: `./gradlew assembleDebug` covers Android; run `./gradlew test` for JVM-side breakage

## 4. Line-ending hygiene

- [ ] Keep each file's existing CRLF/LF style; never flip endings across a whole file
- [ ] After editing, `git diff --stat` must show only the lines you intended — a whole-file diff means endings were rewritten; normalize before committing (see the Line Endings rule in `AGENTS.md`)

## 5. PR description requirements

- [ ] State what changed and **what was verified** (tests run, device smoke, manual flows)
- [ ] State which cross-check items from section 3 were exercised
- [ ] If the change is intentionally narrow (e.g. CI-only, docs-only), say so explicitly

## 6. How to extend this checklist

Every feature PR should append to this file, in the same PR:

1. One or more **own check items** — how to verify the new feature actually works (not just compiles)
2. One or more **cross-check items** — what existing feature the new feature shares state/chain/config with, and how to prove that feature is unaffected
3. Mark them with ✦ in the matching subsystem section, or add a new subsection if the feature creates a new subsystem

The target: when the next person opens a PR, the checklist tells them everything that must still work — so "it compiled and its own tests passed" is never the bar.
