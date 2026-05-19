# Windows Port Completion Audit

Objective: keep Android TV working while extending MiruPlay to Windows desktop,
preserve media-management capabilities, use mpv as the Windows playback backend,
support a bundled RIFE-capable mpv runtime based on mpv_PlayKit, keep both
Android TV and Windows UI on Compose-family frameworks, and make the Windows UI
visually match the TV UI.

Status: not complete. The current implementation is a usable desktop foundation
with a real mpv_PlayKit RIFE-capable runtime prepared locally, but live
CloudDrive2 end-to-end QA, target-hardware RIFE validation, and deeper
desktop-vs-TV UI parity beyond the Library, Details, Player, and Settings
first screens remain open.
The latest Windows Library pass now opens scanned content as the primary
poster-wall surface instead of a source-management tool panel.

## Prompt-to-Artifact Completion Matrix

| Objective deliverable | Concrete artifacts inspected | Verification evidence | Remaining gap |
|---|---|---|---|
| Keep the original Android TV app working | `app/src/main/kotlin/com/miruplay/tv/MainActivity.kt`, `ui-tv/`, Android Gradle modules | `:app:assembleDebug` passed in the latest preservation check. | No fresh instrumented TV-device run in this audit. |
| Add a Windows desktop entry | `desktop-app/build.gradle.kts`, `MiruPlayDesktopComposeApp.kt` | `mainClass` points to `com.miruplay.tv.desktop.MiruPlayDesktopComposeAppKt`; `:desktop-app:test` and lightweight `:desktop-app:installDist -PbundleMpvRuntime=false` pass. | Full Windows release packaging with bundled runtime is available but large; release artifact signing/installer is outside this audit. |
| Use Compose-family UI on both targets | Android remains Compose/Compose TV; desktop default entry is Compose Multiplatform Desktop | `desktop-app` applies Compose plugins, `application.mainClass` points at `MiruPlayDesktopComposeAppKt`, screenshot QA launches the Compose entry, the old Swing shell has been removed from production sources, and root `checkDesktopComposeOnly` fails if Swing UI imports/classes or `coroutines-swing` return. | Covered structurally; full Android-TV-vs-desktop screen parity is tracked separately. |
| Preserve media management capabilities on desktop | `media-source-desktop`, `scanner-desktop`, `repository-api`, `repository-desktop`, `scraper-desktop`, `sync-engine-desktop`, `cloud-drive-desktop`, Compose Library/Details/Settings panels | Unit and integration coverage now includes shared display/subtitle/index/batch planning helpers, desktop details rows, playback progress, RSS scheduler, CloudDrive gRPC client, and RSS offline submission through real `GrpcCloudDriveClient`. | Real CloudDrive2 server live QA remains open for token validation, offline submission, torrent staging, organization, scheduler timing, and source rescan. |
| Use mpv as Windows playback backend | `player-mpv`, `MpvProcessPlayer`, `MpvIpcClient`, Compose Player panel, `tools/smoke-desktop-mpv-launch-ui.ps1` | `:player-mpv:test` passes; desktop app launches `MpvProcessPlayer`; progress sync polls mpv `time-pos` while playing; the GUI mpv launch smoke generates a local Y4M sample, fills it into the Windows Player, disables RIFE for host-safe playback, clicks Play, confirms an `mpv.exe` child process containing the sample path, exercises Pause, -10s, +30s, and Stop, verifies a 30s persisted progress record, and captures launched/control/stopped Player screenshots. `MpvProcessPlayer.stop()` escalates from IPC quit to destroy and force-destroy, including descendant process cleanup when available. | External-process mpv mode is covered; embedded libmpv is intentionally deferred. |
| Plan and integrate a bundled RIFE-capable mpv runtime | `runtime/mpv`, `tools/prepare-mpv-runtime.ps1`, `desktop-app:verifyMpvRuntimePayload`, `smokeMpvRuntime`, `tools/smoke-mpv-rife.ps1`, `docs/mpv-runtime-packaging.md` | Runtime verifier and Gradle smoke pass for NVIDIA/DIRECTML scripts; prior DirectML VapourSynth/RIFE smoke proved the payload path; `-Backend ALL -AllowFailures` reports the backend matrix. `tools/smoke-mpv-rife.ps1 -ReportPath ...` writes a JSON evidence bundle with mpv version, host OS/CPU/GPU diagnostics, backend statuses, exit codes, and log paths for target-host QA. | Local RIFE playback is non-blocking on this host; backend performance/compatibility must be validated on target hardware. |
| Make Windows UI visually match the TV UI | `ui-design`, `ui-tv/.../theme/Theme.kt`, `MiruPlayDesktopComposeApp.kt`, root `checkUiPaletteDrift`, `tools/capture-desktop-ui.ps1`, `build/desktop-ui-qa/*.png`, `build/android-tv-qa/library-baseline-20260519.png` | TV and desktop now derive the MiruPlay red/dark/blue/text/card palette from the same `MiruPlayPalette` constants. `checkUiPaletteDrift` fails if TV or desktop UI reintroduces raw shared palette literals outside `:ui-design`. Android TV Library baseline was refreshed from emulator `10.137.32.118:5555`. Windows Library now uses the TV-style full-width Explore header, right-side actions, TV empty state, and a 6-column poster-wall first screen after scanning or loading a saved index; search/source controls are below the media wall. Poster selection routes directly to Details, Details starts with a TV-style hero, Player now opens without the desktop rail into a TV-like playback stage with top return, centered transport controls, bottom timeline/status chips, and advanced mpv/RIFE settings below the stage, and Settings now opens with a TV-like category rail plus summary cards before the Cloud/RSS form. Screenshot QA covers Library/Details/Player/Settings and asserts window size, dark palette, red accent, readable light text, visual diversity, and per-section distinctness. | Deeper Settings/source-management parity and remote/source-management controls remain desktop-specific. |
| Provide auditable verification gates | Gradle MCP build records, `.github/workflows/ci.yml`, scripts under `tools/`, this audit document | CI now runs the Android debug build plus desktop/shared JVM checks: `checkDesktopComposeOnly`, `checkUiPaletteDrift`, `:core:model:test`, `:repository-api:test`, `:player-mpv:test`, `:cloud-drive-desktop:test`, `:sync-engine-desktop:test`, `:desktop-app:test`, and lightweight `:desktop-app:installDist -PbundleMpvRuntime=false`. Latest local commands are listed below with passing evidence for Android build, desktop tests, mpv tests, CloudDrive loopback tests, runtime smoke, DirectML RIFE smoke, and screenshot QA. | Hardware/cloud/live-service checks are intentionally tracked as not achieved. |

Completion decision: do not mark complete. The project has a usable Windows
Compose Desktop port and preserved Android debug build evidence, but the
objective requires real-world confidence across bundled RIFE backends and full
CloudDrive2 behavior. Those still depend on target GPU/driver/plugin stacks and
a live CloudDrive2 environment.

## Checklist

| Requirement | Current evidence | Status |
|---|---|---|
| Android TV remains buildable | `.\gradlew.bat :app:assembleDebug` passed in the latest preservation check after the desktop port work. | Covered for debug build |
| Android TV uses Compose TV | Existing Android app remains `MainActivity` + Compose navigation and `ui-tv` Compose/TV screens. | Covered structurally |
| Windows desktop entry exists | `:desktop-app` JVM application now points `mainClass` at `com.miruplay.tv.desktop.MiruPlayDesktopComposeAppKt`; `MiruPlayDesktopComposeApp.kt` is a Compose Desktop window. | Covered structurally |
| Windows UI uses Compose Desktop | `desktop-app` applies `org.jetbrains.compose` and `org.jetbrains.kotlin.plugin.compose`; the default entry renders local library source/scan/search, WebDAV/SMB open/browse/scan, single-item Bangumi search/apply/clear, batch Bangumi preview/apply/undo, continue-watching recents, mpv runtime, RIFE, command preview, Launch/Stop controls, and CloudDrive2/RSS automation with Compose Material 3. | Covered for core desktop workflow |
| Windows visual language matches TV | `ui-design` now owns the shared MiruPlay palette; Android TV `Theme.kt` and Compose Desktop both derive `AnimeRed`, `DarkBg`, `DarkSurface`, `AccentBlue`, `TextPrimary`, `TextSecondary`, and `CardBg` from `MiruPlayPalette`. The root `checkUiPaletteDrift` task guards against raw palette literal drift in `ui-tv/src` and `desktop-app/src`. Android TV Library was refreshed on emulator `10.137.32.118:5555`. Compose Desktop Library now mirrors that direction and shows 6-column poster-wall cards as the first scanned-library surface instead of a tool/control panel; saved index entries are restored on startup/source switch so a scanned library opens directly to media. Compose Desktop Details opens directly from a poster click and starts with a TV-style hero for poster/backdrop, title, context, plot, Play, and Back-to-poster-wall actions. Compose Desktop Player hides the desktop rail and shows a TV-style playback stage with top return, centered play/seek/stop controls, bottom timeline, and RIFE/subtitle status chips before exposing advanced mpv controls below. Local screenshot QA covers Library, Details, Player, and Settings first screens; it asserts minimum TV-style window size, non-tiny PNG output, sampled visual diversity, dark-theme coverage, MiruPlay red accent pixels, readable light text pixels, and distinct images per section. Settings still needs more TV-parity work. | Partial |
| Shared logic is not trapped in the desktop UI shell | `core:model` owns reusable display formatting and external subtitle-track parsing; `repository-api` owns media-index display helpers and metadata batch planning. `desktop-app` keeps compatibility wrappers in `DesktopPresenters.kt`, but the behavior is now tested in shared modules and can be reused by Android TV or future KMP surfaces. | Covered for the extracted helpers; more desktop UI state can still be split into shared use cases later |
| Windows playback uses mpv | `:player-mpv` builds mpv commands, starts an external process, supports IPC pause/seek/quit/time-position queries, and `desktop-app` launches `MpvProcessPlayer`; `:player-mpv:test --rerun` passed 14 tests. | Covered for external process mode |
| Real mpv executable can launch | mpv_PlayKit `20260510` assets were downloaded into `.gradle/mpv-playkit-20260510`; `runtime/mpv` was prepared from `mpv-lazy-20260510.exe` plus the `mpv-lazy-20260510-vsNV.7z.001` overlay, then the default `:desktop-app:smokeMpvRuntime -PrequireMpvRuntime=true` gate passed with `mpv v0.41.0-615-g7b057f66f` and required RIFE `NVIDIA, DIRECTML`. | Covered |
| Bundled RIFE runtime is supported | Runtime layout expects `portable_config/vs/MEMC_RIFE_NV.vpy` and `MEMC_RIFE_DML.vpy` for the default release gate; app can also select the optional Standard script when present. The verifier blocks launch when a selected script is missing. The current local manifest records the standard `.exe` base plus `vsNV` overlay and the default NVIDIA/DirectML requirements. | Covered structurally |
| Real RIFE payload works | DirectML RIFE smoke previously passed through `tools/smoke-mpv-rife.ps1 -Backend DIRECTML` with `runtime/mpv/mpv.exe`, `MEMC_RIFE_DML.vpy`, and a generated two-frame 1440x810 Y4M clip: mpv initialized VapourSynth and exited with playback success. The `-Backend ALL -AllowFailures` matrix mode reports all three backends in one run; `-ReportPath` can persist the same run plus host diagnostics as JSON. On this host, RIFE playback is treated as non-blocking because the machine is not expected to run interpolation well. | Covered structurally; target hardware validation remains |
| Runtime preparation is repeatable | `tools/prepare-mpv-runtime.ps1` accepts extracted directories or `.7z/.7z.001`, supports `-OverlaySource` for patching a base runtime with a RIFE/VapourSynth payload, optionally validates SHA256 before extraction including `filename=sha256` lists for split payloads, validates required RIFE scripts, copies to `runtime/mpv`, and writes `runtime-manifest.json`; tested with fake base/overlay payloads and with real mpv_PlayKit `20260510` standard + `vsNV` assets. | Covered |
| Desktop distribution runtime copy is controllable | `desktop-app/build.gradle.kts` bundles exactly one runtime source: explicit `-PmpvRuntimeSource` when present, otherwise repository `runtime/mpv`. `bundleMpvRuntime` defaults to `true` for self-contained artifacts, while `-PbundleMpvRuntime=false` skips the large runtime copy for UI-only development installs; verified with `:desktop-app:installDist -PbundleMpvRuntime=false` in 15s and `:desktop-app:smokeMpvRuntime -PmpvRuntimeSource=runtime\mpv -PrequiredRifeBackends=NVIDIA,DIRECTML`. | Covered |
| Runtime provenance is visible | `MpvRuntimeVerifier` reads `runtime-manifest.json`; `Check runtime` dialog shows source, verified time, required RIFE backends, and files. | Covered |
| Local/WebDAV/SMB sources are available on desktop | `:media-source-desktop` implements local, WebDAV, and SMB sources. Compose Desktop exposes local source add/scan/search, saved-source switching, current-source index clearing/removal, WebDAV/SMB source open, directory browsing, current-source scanning, loopback bridge playback for remote media, and selected-media details for local index entries, remote browser entries, and recent playback records. `tools/smoke-desktop-local-source-ui.ps1` now starts the Windows GUI with an isolated store, adds a local source from either a generated fixture or `-LibraryRoot`, scans it, validates the persisted source/index JSON, records scan/search/details/player screenshots for the generated fixture, verifies poster-wall search can filter to a target anime, verifies poster click opens Details, and verifies that selected poster-wall media is handed to the Player media path field. | Covered for local core flow; WebDAV/SMB GUI fixture smokes still open |
| Desktop library indexing exists | `:scanner-desktop` scans desktop sources, filters videos, infers metadata, reads sibling `.nfo` and `tvshow.nfo`, and rebuilds `repository-desktop` index. The local-source GUI smoke validates that a generated NFO fixture appears in the desktop index as `Fixture Frieren` episode 2, verifies repository search can isolate that anime, and verifies poster-wall selection/detail/player handoff. The same smoke now passes against the real local anime library at `D:\Software\dufs`, with `local-source-scanned.png` showing the Library first screen as a poster wall. | Covered |
| Desktop media metadata can be inspected | Compose Desktop has a TV-style Details hero backed by selected index media plus a `Media details` panel backed by `DesktopMediaDetailRows`, showing active source, indexed title/type/anime/season/episode/title, Bangumi metadata source/id/title, indexed size/modified time, browser item kind/MIME/size/modified time/path, plot when present, and recent playback resume/play count/last watched. Unit tests cover the row model and hero title/subtitle helpers, and the local-source GUI smoke now opens the Details route by selecting a scanned local poster. | Covered |
| Bangumi metadata is available on desktop | `:scraper-desktop` provides JVM Bangumi search/details/episodes; Compose Desktop has `Use selected`, `Search`, `Apply match`, and `Clear metadata` to write or clear selected match source/id/title on one index entry. | Covered for manual single-item flow |
| Batch metadata workflows | Compose Desktop has `Batch preview`, `Apply batch`, `Accept review`, and `Undo batch`; batch plans split matches into ready/review/conflict, render the preview as a selectable review queue, retain alternate Bangumi candidates per query, let the user switch the selected candidate before applying or manually accepting a reviewed match, skip existing-metadata conflicts instead of overwriting them, apply only high-confidence ready updates automatically, and persist the last rollback list in the desktop JSON store so undo survives restart. | Covered |
| Metadata batch planning is reusable | `repository-api/src/main/kotlin/com/miruplay/tv/repository/MetadataBatchPlanner.kt` owns query derivation, ready/review/conflict splitting, conflict isolation, preview text, and plan summaries; `desktop-app` delegates through thin compatibility wrappers. `:repository-api:test :desktop-app:test --rerun` passed after extraction. | Covered for planner logic |
| Index and subtitle display helpers are reusable | `repository-api/src/main/kotlin/com/miruplay/tv/repository/MediaIndexDisplay.kt` owns `MediaIndexEntry` display names/lines/browser conversion. `core/model/src/main/kotlin/com/miruplay/tv/model/SubtitleTracks.kt` owns external subtitle path parsing and format detection. Shared module tests cover both. | Covered for extracted helpers |
| Playback progress continues on desktop | Compose Desktop saves progress when mpv launches, polls mpv `time-pos` every 10 seconds while playback is active, saves a session-estimated position immediately on Stop so process shutdown is not blocked by IPC, shows continue-watching records, can clear a selected recent item, provides TV-style Play/Pause/-10s/+30s/Stop controls in the playback stage, and stores original remote path rather than loopback URL. `tools/smoke-desktop-mpv-launch-ui.ps1` now proves the GUI can launch a generated local sample through mpv, exercise Pause/-10s/+30s/Stop, confirm the mpv process exits, and record `mpv-launch-ready.png`, `mpv-launched.png`, `mpv-controls-used.png`, and `mpv-stopped.png` evidence with a 30s saved position. | Covered |
| True mpv position tracking | `MpvIpcClient` can request `get_property time-pos`, `MpvProcessPlayer.queryTimePositionMs()` exposes it, and Compose Desktop uses `syncPlaybackProgressFromMpv` to re-anchor the session and persist observed mpv positions during playback and at stop. Unit tests cover session re-anchoring and sync helper success/null/error behavior. | Covered for Compose Desktop |
| Cloud/RSS sync parity on desktop | `repository-desktop` persists `CloudDriveAutomationConfig`, RSS subscriptions, processed items, download tasks, and file-backed CloudDrive/Bangumi credentials in the JSON store. `:cloud-drive-desktop` provides a JVM CloudDrive2 gRPC client, `:sync-engine-desktop` provides a JVM RSS runner plus scheduler with feed fetch, filtering, processed-item dedupe, CloudDrive offline submission, torrent-to-magnet staging, organizer moves, `lastRunAt` updates, `runIfDue`, scheduler state flow, and a desktop post-sync source rescan hook. Compose Desktop now exposes CloudDrive2/RSS config, token/password save/clear, `Login`, `Verify token`, subscription add/update/delete, `Run sync now`, `Start scheduler`, and `Stop scheduler`. `:cloud-drive-desktop:test` starts a real loopback gRPC server against the generated CloudDrive2 stub and covers login, API token info, bearer auth listing, and raw-token fallback. `:sync-engine-desktop:test` now also runs `DesktopCloudDriveRssAutomationEngine` against a loopback CloudDrive2 gRPC server through the real `GrpcCloudDriveClient`, covering RSS offline submission, bearer metadata, processed item persistence, download-task persistence, and organizer list calls. `:cloud-drive-desktop:smokeCloudDrive2` is available for live endpoint/token/path QA without printing the token. `:sync-engine-desktop:smokeCloudDriveRssDryRun` verifies a real endpoint, token, inbox/library listing, RSS fetch/parse, filter matching, and would-submit counts without calling CloudDrive offline download APIs; `-PcloudDriveRssReportPath=...` writes a token-free JSON dry-run evidence report. `:sync-engine-desktop:smokeCloudDriveRssLiveSubmit` is now available as a separate explicit-confirmation task that submits a limited number of live RSS candidates and records submit counts plus post-submit inbox listing in a token-free JSON report. Live submit/organize/scheduler QA still needs to be executed against a real server before completion can be claimed. | Partial |
| Desktop UI maturity | Compose Desktop is now the default entry and follows the TV visual language for local library/source management, remote browser, saved-source switching, media details, Bangumi metadata, recents, playback, runtime, and settings/automation slices. The Library route now uses a TV-style full-width header and poster-wall primary surface as the first content after scan/load; Details opens from poster selection and starts with a TV-style hero before the metadata/Bangumi panels; Player hides the desktop rail and starts with a TV-style playback stage before advanced settings; Settings opens with a TV-style category rail, summary cards, and quick actions before the Cloud/RSS form. Screenshot QA is scripted via `tools/capture-desktop-ui.ps1` and passes with palette, text, size, diversity, and per-section distinctness checks. Latest local UI QA generated `build/desktop-ui-qa/library.png`, `details.png`, `player.png`, and `settings.png`; latest local-source smoke generated `build/desktop-local-source-ui/run-20260519-131324/local-source-scanned.png`, `local-source-details.png`, and `local-source-player.png` against `D:\Software\dufs`; latest mpv GUI launch smoke generated `build/desktop-mpv-launch-ui/run-20260519-130857/mpv-launch-ready.png`, `mpv-launched.png`, `mpv-controls-used.png`, and `mpv-stopped.png`. | Partial |

## Latest Verification Commands

```powershell
.\tools\prepare-mpv-runtime.ps1 `
  -Source .\.gradle\mpv-playkit-20260510\mpv-lazy-20260510.exe `
  -OverlaySource .\.gradle\mpv-playkit-20260510\mpv-lazy-20260510-vsNV.7z.001 `
  -Destination .\runtime\mpv `
  -RequiredRifeBackends 'NVIDIA,DIRECTML' `
  -Force

$env:JAVA_HOME='C:\Users\adqew\scoop\apps\temurin21-jdk\current'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat :desktop-app:smokeMpvRuntime `
  -PrequireMpvRuntime=true

.\gradlew.bat :desktop-app:installDist -PbundleMpvRuntime=false

.\gradlew.bat :desktop-app:smokeMpvRuntime `
  -PmpvRuntimeSource=runtime\mpv `
  -PrequiredRifeBackends=NVIDIA,DIRECTML

.\gradlew.bat :repository-desktop:test :desktop-app:test :data:compileDebugKotlin :scanner:test

.\gradlew.bat :sync-engine-desktop:test :repository-desktop:test :player-mpv:test :desktop-app:test :desktop-app:installDist :app:assembleDebug

.\gradlew.bat checkDesktopComposeOnly `
  checkUiPaletteDrift `
  :core:model:test `
  :repository-api:test `
  :player-mpv:test `
  :cloud-drive-desktop:test `
  :sync-engine-desktop:test `
  :desktop-app:test `
  :desktop-app:installDist `
  -PbundleMpvRuntime=false

.\gradlew.bat :cloud-drive-desktop:test

.\gradlew.bat :cloud-drive-desktop:test --rerun

.\gradlew.bat :cloud-drive-desktop:smokeCloudDrive2 `
  -PcloudDriveEndpoint=http://127.0.0.1:19798 `
  -PcloudDriveToken=<token> `
  -PcloudDrivePath=/Downloads

.\gradlew.bat :sync-engine-desktop:smokeCloudDriveRssDryRun `
  -PcloudDriveEndpoint=http://127.0.0.1:19798 `
  -PcloudDriveToken=<token> `
  -PcloudDriveRssUrl=https://example.test/rss.xml `
  -PcloudDriveInbox=/Downloads `
  -PcloudDriveLibrary=/Library `
  -PcloudDriveRssFilter=Episode `
  -PcloudDriveRssReportPath=build/cloud-rss-smoke/report.json

.\gradlew.bat :sync-engine-desktop:smokeCloudDriveRssLiveSubmit `
  -PcloudDriveEndpoint=http://127.0.0.1:19798 `
  -PcloudDriveToken=<token> `
  -PcloudDriveRssUrl=https://example.test/rss.xml `
  -PcloudDriveInbox=/Downloads `
  -PcloudDriveLibrary=/Library `
  -PcloudDriveRssFilter=Episode `
  -PcloudDriveRssSubmitConfirmation=I_UNDERSTAND_THIS_SUBMITS_REAL_CLOUDDRIVE_DOWNLOADS `
  -PcloudDriveRssSubmitLimit=1 `
  -PcloudDriveRssReportPath=build/cloud-rss-smoke/live-submit-report.json

.\gradlew.bat :sync-engine-desktop:test

.\gradlew.bat :player-mpv:test --rerun

.\gradlew.bat :app:assembleDebug

.\gradlew.bat :desktop-app:test

.\gradlew.bat checkDesktopComposeOnly

.\gradlew.bat :ui-tv:compileDebugKotlin :desktop-app:test --rerun

.\gradlew.bat checkUiPaletteDrift

.\tools\capture-desktop-ui.ps1

.\tools\smoke-desktop-local-source-ui.ps1

.\tools\smoke-desktop-local-source-ui.ps1 -LibraryRoot 'D:\Software\dufs'

.\tools\smoke-desktop-mpv-launch-ui.ps1

adb connect 10.137.32.118:5555
adb shell monkey -p com.miruplay.tv -c android.intent.category.LAUNCHER 1
adb exec-out screencap -p > build\android-tv-qa\library-baseline-20260519.png

.\tools\smoke-mpv-rife.ps1 -Backend DIRECTML `
  -ReportPath .\build\mpv-smoke\rife-directml-report.json

.\tools\smoke-mpv-rife.ps1 -Backend ALL -AllowFailures `
  -ReportPath .\build\mpv-smoke\rife-matrix-report.json
```

## Highest-Risk Remaining Work

1. Continue narrowing full Android TV screen parity gaps beyond the Library,
   Details, Player, and Settings first screens, especially source management
   detail pages, WebDAV/SMB fixtures, and navigation focus behavior.
2. Validate RIFE on target Windows hardware that is expected to support
   interpolation, and decide whether the optional Standard backend should ship
   an additional `rife` plugin.
3. Run live CloudDrive2 end-to-end QA for real offline submission, torrent
   staging, organization, scheduler behavior over real time, and source rescan.
   Token/path/RSS parsing can now be checked first with the dry-run smoke task.
