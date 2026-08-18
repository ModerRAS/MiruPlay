# PR #70 Local Integration Report

## Scope and safety

- GitHub PR: `https://github.com/ModerRAS/MiruPlay/pull/70`
- Agreed baseline: `2adbeffbdcf037ef94a1cae355df530fb3003d1d`
- `gh pr view 70` remote head: `2adbeffbdcf037ef94a1cae355df530fb3003d1d`
- Explicitly fetched `refs/pull/70/head`: `2adbeffbdcf037ef94a1cae355df530fb3003d1d`
- Isolated worktree: `C:/tmp/MiruPlay-pr70-integration-worker53`
- Isolated branch: `worker53/pr70-integration-20260818`
- No push, merge, tag, PR edit, or remote update was performed.

The first cherry-pick command was accidentally issued without an explicit worktree path. It created four temporary local commits on the main worktree branch. The recorded main HEAD before that command was `2adbeffbdcf037ef94a1cae355df530fb3003d1d`, with no tracked changes and these pre-existing untracked paths: `.workbuddy/`, `NUL`, `d8outtest/`, `d8test2/`, `d8test3/`, `desugar_local.jar`, `head-lv999.mkv`, and `output/`. The error was caught immediately, the main branch was restored to the recorded HEAD, and the same original untracked paths remained. No further main-worktree write was performed.

## Integration order

1. `c331e26763798871c7dee2275ec34b4b717ab18b` - ASS compositor dirty-region fix.
2. `2bcc6e69b21424f998446ed40fa5c85f7fcf503e` - logical episode deduplication across metadata writes.
3. `a3aa909822197f7a2e141aa951f778c85a714ed2` - Bangumi comment chain and raw-subtitle test constructor repair.
4. `a8d80ab313464087f1d8107fd9329dc149e029fd` - translation provider/replay coverage and lint repair.

There were no cherry-pick conflicts. `PlayerViewModel.kt` auto-merged because the comment candidate changes episode-comment resolution near the comment-loading path while the translation candidate changes the later translation-track polling loop. Both semantics are present.

## Native host-test hookup decision

No portable repository pattern exists to register the C++ host test. The only CMake file is `player-mpv-android/src/main/cpp/CMakeLists.txt`; it builds an Android shared library and requires Android `android`, `log`, and `dl` libraries. `player-mpv-android/build.gradle.kts` only exposes Android `externalNativeBuild`. The repository has no `enable_testing`, `add_test`, CTest, host compiler Gradle task, or host C++ CI step. Adding a new host-build subsystem would violate the minimal-hookup constraint, so no infrastructure was invented. The pure C++17 test was compiled and run directly.

## Verification commands and exact outcomes

Environment correction: isolated worktrees do not inherit the ignored `local.properties`. The first three ASS Gradle attempts failed during configuration with `SDK location not found`; the existing machine-local SDK pointer was then copied into the isolated worktree as an ignored file, and all three commands passed on rerun.

### ASS candidate

- `g++ -std=c++17 -Wall -Wextra -pedantic -Iplayer-mpv-android/src/main/cpp player-mpv-android/src/test/cpp/miruplay_ass_compositor_test.cpp -o build/host-tests/miruplay_ass_compositor_test.exe` - exit 0, no warnings.
- `build/host-tests/miruplay_ass_compositor_test.exe` - exit 0, `miruplay_ass_compositor_test: PASS`.
- `./gradlew :player-mpv-android:testDebugUnitTest` - initial exit 1 (`SDK location not found`); rerun exit 0, `BUILD SUCCESSFUL in 11s`.
- `./gradlew :player-mpv-android:assembleDebug` - initial exit 1 (same SDK cause); rerun exit 0, `BUILD SUCCESSFUL in 22s`.
- `./gradlew :app:assembleDebug` - initial exit 1 (same SDK cause); rerun exit 0, `BUILD SUCCESSFUL in 4m 44s`.

### Episode-dedup candidate

- `./gradlew :core:model:test --tests com.miruplay.tv.model.PlaybackSourceTest` - exit 0, `BUILD SUCCESSFUL in 8s` (later cached rerun: 1s); 9 tests, 0 failures/errors.
- `./gradlew :repository-api:test --tests com.miruplay.tv.repository.MediaIndexMetadataCacheTest` - exit 0, `BUILD SUCCESSFUL in 4s`; 4 tests, 0 failures/errors.
- `./gradlew :scanner:test --tests com.miruplay.tv.scanner.MlipLibraryIndexImporterTest --tests com.miruplay.tv.scanner.ScanCoordinatorTest` - exit 1 because Android aggregate task `:scanner:test` does not accept `--tests`.
- Corrected directed command: `./gradlew :scanner:testDebugUnitTest --tests com.miruplay.tv.scanner.MlipLibraryIndexImporterTest --tests com.miruplay.tv.scanner.ScanCoordinatorTest` - exit 0, `BUILD SUCCESSFUL in 6s`; 24 + 19 tests, 0 failures/errors.
- `./gradlew :sync-engine-shared:test --tests com.miruplay.tv.sync.BangumiMetadataRefreshCoreTest` - exit 0, `BUILD SUCCESSFUL in 7s`; 10 tests, 0 failures/errors.
- `./gradlew :core:model:test :repository-api:test :scanner:test :sync-engine-shared:test` - exit 0, `BUILD SUCCESSFUL in 24s`.

### Comment-chain/rawSubtitle candidate

- `./gradlew :core:model:test --tests com.miruplay.tv.model.PlaybackSourceTest` - exit 0, `BUILD SUCCESSFUL in 1s`; 9 tests, 0 failures/errors.
- `./gradlew :repository-api:test --tests com.miruplay.tv.repository.NextPlaybackSourceResolverTest` - exit 0, `BUILD SUCCESSFUL in 3s`; 15 tests, 0 failures/errors.
- `./gradlew :ui-tv:testDebugUnitTest --tests com.miruplay.tv.ui.player.PlayerEpisodeCommentsTest --tests com.miruplay.tv.ui.player.BangumiCommentsPanelTest` - exit 0, `BUILD SUCCESSFUL in 14s`; 2 + 2 tests, 0 failures/errors.
- `./gradlew :app:testDebugUnitTest --tests com.miruplay.tv.LaunchTestSourceContentModeTest` - exit 0, `BUILD SUCCESSFUL in 5s`; 33 tests, 0 failures/errors.
- `./gradlew :app:compileDebugUnitTestKotlin` - exit 0, `BUILD SUCCESSFUL in 2s`.

### Translation candidate

- `./gradlew :translation:test --tests com.miruplay.tv.translation.TranslationProviderTransportTest` - exit 1 because Android aggregate task `:translation:test` does not accept `--tests`.
- Corrected directed command: `./gradlew :translation:testDebugUnitTest --tests com.miruplay.tv.translation.TranslationProviderTransportTest` - exit 0, `BUILD SUCCESSFUL in 11s`; 7 tests, 0 failures/errors.
- `./gradlew :ui-tv:testDebugUnitTest --tests com.miruplay.tv.ui.player.PlayerViewModelTranslationTest` - exit 0, `BUILD SUCCESSFUL in 8s`; 1 test, 0 failures/errors.
- `./gradlew :translation:lint` - exit 0, `BUILD SUCCESSFUL in 35s`.
- `./gradlew :ui-tv:lint` - exit 0, `BUILD SUCCESSFUL in 44s`.

### Final repository gates

- `./gradlew :app:compileDebugUnitTestKotlin` - exit 0, `BUILD SUCCESSFUL in 3s`.
- `./gradlew test` - exit 0, `BUILD SUCCESSFUL in 1m 37s`.
- `./gradlew lint` - exit 0, `BUILD SUCCESSFUL in 2m 15s`.
- `./gradlew assembleDebug` - exit 0, `BUILD SUCCESSFUL in 6s`.
- `./gradlew :player-core:testDebugUnitTest --tests com.miruplay.tv.player.LibassSubtitleSampleTest` - exit 0, `BUILD SUCCESSFUL in 4s`.
- `git diff --check 2adbeff..HEAD` before the report commit - exit 0, clean.

## Prior blocker checks

- `rawSubtitle`: the three affected `LaunchTestSourceContentModeTest` constructions now contain all six `rawSubtitleUri = null` / `rawSubtitleFormat = null` arguments; focused tests and app unit-test compilation pass.
- `UnsafeOptIn`: `SubtitleTranslationService` now uses scoped `@androidx.annotation.OptIn(markerClass = [UnstableApi::class])`; translation/UI lint and repository lint pass.
- Episode overwrite: there are zero `cacheMetadata` calls assigning `episodeCount = episodes.size`. Metadata write paths use `distinctSeasonEpisodeCount`; remaining `episodes.size` occurrences in `ScanCoordinator` are telemetry counts.

## Original five acceptance items

1. **ASS black-screen root cause: PARTIAL/BLOCKED device acceptance.** Host regression coverage passes for transparent clearing/blending, returned dirty expansion, old-pixel clearing, disappearance, unchanged frames, and 1,000 changed frames with zero repeated full-surface dirty requests. On HK1, the candidate APK installed, matched the device hash, selected the real raw ASS track, rendered a first subtitle frame, stayed alive, and produced no new crash/ANR. Sustained HDMI verification remains blocked by MediaSession state 7 (`Bluetooth audio disconnected`), unavailable NanoKVM credentials, and screenrecord encoder error `-38`; sustained transparency, subtitle disappearance clearing, and absence of periodic full-screen black masks are not claimed as verified.
2. **Season/episode deduplication: PASS.** Logical `(season, episode)` counts now survive scan, cache, MLIP import, poster grouping, refresh, and season conversion paths; focused and full affected-module tests pass.
3. **PlaybackSource Bangumi episode ID to comments label: PASS.** Source-first ID with DB fallback reaches the comments API, UI state, and label; model, resolver, ViewModel helper, panel, launch-hook, and compile coverage pass.
4. **Three-provider translation/replay/proxy reuse: PASS.** Google, Bing, and DeepSeek transport/auth/error behavior, RSS proxy reuse, translated SRT, replay injection, selected translated track, and preserved position are covered; translation and UI lint pass.
5. **Malformed/legacy ASS line loss: PASS.** Existing focused coverage for both 10-field `Marked=0` dialogue forms passes in `LibassSubtitleSampleTest`, and the full test suite remains green.

## Release evidence and version decision

`gh release list --limit 20` reported highest non-draft, non-prerelease stable semver release `v2.9.692`. `git ls-remote --tags origin 'refs/tags/v*'` independently found 191 stable semver tags and the same highest tag, `v2.9.692`. Nightly, prerelease, and non-semver tags were ignored. This integration completes user-visible subtitle, comments, and metadata behavior, so AGENTS requires a minor bump from the online `2.9` line. `app/build.gradle.kts` changes `baseAppVersionName` from `2.9.0` to `2.10.0`.

## Exact proposed PR #70 description update

The text below is prepared for a future authorized `gh pr edit 70 --body-file ...` operation. It was not applied.

```markdown
## Summary

This PR integrates the libass subtitle pipeline and closes the five original acceptance items with focused regression coverage.

- **fix(libass):** replace repeated full-surface ASS overlay updates with dirty rectangles covering the union of old and new subtitle bounds. Geometry changes still request the full surface; unchanged frames do not repost.
- **fix(library):** count logical episodes by distinct `(season, episode)` identity throughout scan, metadata cache, MLIP import, poster grouping, Bangumi refresh, and season conversion paths.
- **fix(bangumi):** carry `PlaybackSource.bangumiEpisodeId` through comment loading with source-first selection and cached-episode fallback, and display the actual loaded Bangumi episode ID.
- **feat(translation):** retain the DeepSeek, Bing, and Google subtitle providers, RSS proxy reuse, translated SRT generation, and replay-track injection; add transport, proxy, track-selection, and position-preservation coverage.
- **fix(ass-parser):** retain support for malformed/legacy 10-field `Marked=0` ASS dialogue lines without systematic subtitle loss.
- **fix(test/lint):** provide `rawSubtitleUri` / `rawSubtitleFormat` in launch-test sources and scope the Media3 unstable API opt-in to the private data-source helper.
- **chore(version):** set the release base to `2.10.0` because this is a user-visible feature integration over the published `v2.9.692` line.

The PR also retains the existing R8 debug configuration, libass performance monitor, mpv release gate, ijk external-subtitle overlay, Bangumi collection sync, and Web Control endpoints/real-stop behavior.

## Verification

- Native compositor host test: PASS with C++17, `-Wall -Wextra -pedantic`.
- Candidate-focused model, repository, scanner, sync, app, UI, translation, and legacy ASS tests: PASS.
- `:app:compileDebugUnitTestKotlin`: PASS.
- `./gradlew test`: PASS.
- `./gradlew lint`: PASS.
- `./gradlew assembleDebug`: PASS.

## Original acceptance items

1. **ASS black-screen root cause: PARTIAL/BLOCKED device acceptance.** Host regressions prove transparent clear/blend behavior, dirty expansion, old subtitle clearing, disappearance handling, unchanged-frame suppression, and no repeated full-surface dirty requests across 1,000 changed frames. On HK1, the candidate installed, matched the device APK hash, selected the real raw ASS track, rendered a first subtitle frame, remained alive, and produced no new crash/ANR. Sustained HDMI verification is still blocked by MediaSession state 7 (`Bluetooth audio disconnected`), unavailable NanoKVM credentials, and Android screenrecord encoder error `-38`. Sustained transparency and the absence of periodic full-screen black masks are not claimed as verified.
2. **Season/episode deduplication: PASS.** No metadata write path overwrites the logical count with physical file count.
3. **Bangumi episode ID comment chain: PASS.** Source-first and DB-fallback IDs reach the API, UI state, and label.
4. **Three-provider translation, replay, and proxy reuse: PASS.** Provider transport/auth/error behavior and replay selection/position preservation are covered.
5. **Malformed/legacy ASS line loss: PASS.** Both supported 10-field `Marked=0` forms remain covered.

## Native test note

The repository has no portable host C++ test registration pattern. Its only CMake target is Android-specific and links Android system libraries, so the pure compositor test is run directly with the host C++17 compiler rather than introducing a new build/CI subsystem in this PR.
```
