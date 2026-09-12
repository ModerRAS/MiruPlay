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

For each subsystem your change could plausibly touch, confirm its happy path still passes. Check items marked ✦ are **regression items that must be extended when related features change**.

### Playback (`player-core`, `player-mpv-android`)
- ✦ Audio pipeline: DSP enabled / disabled / toggled at runtime; SRC bypass modes (SYSTEM / SOFTWARE) with 48 kHz and 44.1 kHz sources; empty-buffer drain; end-of-stream
- ✦ Subtitles: libass pipeline init; subtitle without audio-stream episode; external .ass/.srt load
- ✦ Backend selection: Exo / IJK / mpv paths still pick their renderers factory correctly
- ✦ Playback progress: start position resume (`start_position_ms`), save on stop
- Device smoke (when playback or player-core changed): install debug APK on HK1 via the ADB skill, play one episode from a WebDAV source and one local file, confirm no `Player error` in OpenObserve/logcat

### Library & metadata (`data`, `scanner`, `scraper`, `metadata`)
- ✦ Library scan on Local + WebDAV sources completes; episode counts deduplicate by (season, episode)
- ✦ Bangumi metadata fetch and episode-comment lookup still resolve `bangumiEpisodeId`

### Web control (`web-control-core`, `web-control`, frontend)
- ✦ Parity checklist followed: `docs/agents/settings-web-control-parity-checklist.md` (TV settings ↔ WebAPI ↔ WebUI)
- ✦ Server starts and responds on port 9978; existing routes not silently renamed or removed

### Sync & cloud (`sync-engine`, `cloud-drive`)
- ✦ RSS scheduler starts; subscription add/delete round-trip works

### Data layer (`data`, `core/model`)
- ✦ Room schema change ⇒ migration added and tested; do not bump version without migration
- ✦ Repository contracts unchanged unless intentionally, with all `*Impl` and callers updated

## 4. Line-ending hygiene

- [ ] Keep each file's existing CRLF/LF style; never flip endings across a whole file
- [ ] After editing, `git diff --stat` must show only the lines you intended — a whole-file diff means endings were rewritten; normalize before committing (see the Line Endings rule in `AGENTS.md`)

## 5. PR description requirements

- [ ] State what changed and **what was verified** (tests run, device smoke, manual flows)
- [ ] State which cross-check items from section 3 were exercised
- [ ] If the change is intentionally narrow (e.g. CI-only, docs-only), say so explicitly

## 5. How to extend this checklist

Every feature PR should append to this file, in the same PR:

1. One or more **own check items** — how to verify the new feature actually works (not just compiles)
2. One or more **cross-check items** — what existing feature the new feature shares state/chain/config with, and how to prove that feature is unaffected
3. Mark them with ✦ in the matching subsystem section, or add a new subsection if the feature creates a new subsystem

The target: when the next person opens a PR, the checklist tells them everything that must still work — so "it compiled and its own tests passed" is never the bar.
