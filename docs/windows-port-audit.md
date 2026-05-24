# Windows Port Completion Audit

Objective: keep Android TV working while extending MiruPlay to Windows desktop,
preserve media-management capabilities, use mpv as the Windows playback backend,
support a bundled RIFE-capable mpv runtime based on mpv_PlayKit, keep both
Android TV and Windows UI on Compose-family frameworks, and make the Windows UI
visually match the TV UI.

Status: not complete. The current implementation is a usable desktop foundation
with a real mpv_PlayKit RIFE-capable runtime prepared locally, but live
CloudDrive2 end-to-end QA, target-hardware RIFE validation, and deeper
desktop-vs-TV UI parity for less-traveled keyboard/DPAD focus paths remain open.
The latest Windows Library pass now opens scanned content with the poster wall
as the first media surface instead of a source-management tool panel, and the
latest WebDAV and SMB GUI smokes prove scanned remote sources also return to
the poster wall. The latest source-management pass also replaced the saved
source dropdown with a TV-style focused source card that compacts long paths,
uses TV-facing source type labels plus Chinese source-management controls and
status copy, and now has both unit-covered and real-window keyboard directional switching,
and split WebDAV/SMB setup into remote source cards with compact endpoint
previews. Local source, WebDAV, and SMB editor fields now also bridge into
their adjacent action rows with TV-style focus movement. Cloud/RSS settings now also render as TV-style overview/config/
subscription/scheduler cards instead of one dense automation form, and those
cards now use TV-facing Chinese labels, previews, empty states, and common
status copy. The shared `core:model` module owns the Cloud/RSS run summary and
TV-facing status wording now reused directly by Android TV Settings, the Windows
Cloud/RSS panel, desktop linked-source rescan flow, desktop scheduler display,
and scheduler smokes. CloudDrive2 credential fields, sync-path fields, and RSS edit
fields now move through their form rows and bridge into adjacent actions or
toggles; RSS subscription rows now page in six-row TV-style windows, keep
restored selected rows visible, and support `Up`/`Down` key movement across the
full saved subscription list; the empty subscription state still bridges into
the RSS action and scheduler rows. The Settings category
menu and desktop route rail now support `Up`/`Down` key navigation with TV-list edge stops and
TV-facing `探索`/`详情`/`播放`/`设置` route copy,
and the Library poster wall supports directional movement plus `Enter` to open
Details. The Library empty media state is focusable, with source controls moving
`Down` into it and `Up` returning to the media source form. The Library highest-heat and recently-added poster shelves, desktop switches/toggles, and RIFE backend picker now also
support horizontal `Left`/`Right` focus movement where applicable plus shared `Enter`/numpad Enter/DPAD Center activation. Compose Desktop now treats `Esc`, TV remote `Back`, and navigation back/out keys as Back equivalents for Player -> Details -> Library and Settings -> Library while leaving `Backspace` to text fields. The remote browser list now supports `Up`/`Down` row movement, first-row
`Up` parent navigation, `Enter` without opening directories during focus
movement, and a focusable empty state that returns to the `上级` action with `Up`.
Settings summary quick-action rows now also have TV-style horizontal
focus movement with row-edge stops, and `Up` bridges the row back to the
selected Settings category. Real Windows GUI
smokes prove Sources to CloudDrive, Details to Player, poster-wall Right to
Enter, CloudDrive RSS subscription row Down, route rail Details to Player and `Esc` back to Details/Library, Details hero Right to Left to
Enter, Details hero stat pills, Details hero Down to the episode shelf, episode shelf Up back to hero,
episode shelf Down to Bangumi then Up back to episode shelf, Bangumi action
grid Right/Right into results, Left back to Apply, and Right to Clear,
Details hero Down to Continue watching Enter, Details hero Down to Bangumi Use selected when no recents exist,
WebDAV remote-browser Up to parent plus Down to Enter, saved-source card Up, and
Player transport paths by keyboard input.
Release packaging now has a packaged runtime gate that builds `desktop-app.zip`,
launches the packaged runtime source with `mpv.exe --version`, and verifies the
zip contains the mpv executable, runtime manifest, and NVIDIA/DIRECTML RIFE
scripts. It also has a JDK `jpackage --type app-image` gate that builds the
Windows app-image directory, verifies the native launcher, validates generated
launcher config/classpath entries, checks the bundled `runtime/mpv` payload plus
NVIDIA/DIRECTML scripts, and starts the generated `MiruPlay.exe` in a headless
desktop-entry smoke mode to verify the app-image resolves its own runtime before
installer creation. The opt-in Windows installer gate preflights WiX, builds
MSI/EXE artifacts from that verified app image, records SHA256/size/version and
signing mode evidence, and can sign plus verify with explicit signtool/PFX
inputs. `tools/assert-windows-installer-report.ps1` validates that evidence
against the generated installer path, file size, SHA256, installer type, app
version, and expected signing mode. The unified verifier now passes Windows
installer signing options through to that gate, runs the installer-report
assertion after packaging, and redacts token/password-like arguments when native
commands fail, so signed installer QA can use the same release checklist
without leaking credentials in error text. `tools/verify-windows-port.ps1` now wraps the local port gate with
safe defaults: it selects JDK 21 when available, Gradle/JVM/desktop install,
Cloud/RSS scheduler elapsed-time smoke, and Android debug build run by default,
while GUI smokes, the real `D:\Software\dufs` library, Android TV emulator
smoke, SMB share smoke, mpv runtime payload checks, native app-image packaging,
live CloudDrive/RSS checks, and RIFE target hardware checks require explicit
switches. CI now also runs the base Android/desktop/shared JVM gate plus the
local Cloud/RSS scheduler smoke on `codex/**` push branches, so the active port
branch is checked automatically after pushes. A separate `windows-latest` job
builds and uploads a versioned lightweight Windows desktop ZIP with
`:desktop-app:test :desktop-app:distZip -PwindowsPackageVersion=... -PbundleMpvRuntime=false`;
nightly main/master releases attach the same-version ZIP alongside the Android APK.
Full bundled mpv/RIFE ZIPs and signed MSI/EXE installers remain opt-in release
gates, not default CI artifacts.

Latest shared-boundary update: `:media-source-api` now owns the platform-neutral
media-source interfaces and range-capable stream contract, while Android keeps
its implementation in `:media-source` and Windows keeps its implementation in
`:media-source-desktop`. `:metadata-core` now owns XML NFO parsing/writing used
by both Android scanner/metadata code and Windows scanner code. `:scraper-core`
now owns the shared `MetadataScraper` contract plus Bangumi API request,
paging, collection update, and JSON mapping behavior, while Android/Windows
wrappers implement the same scraper contract and provide only token source,
User-Agent, and query-normalization differences. Preferred Bangumi candidate
promotion also lives in `:scraper-core`, so Android Detail rescrape and Windows
single-item/batch metadata search share the same weak-direct-match fallback
instead of carrying separate alias loops. `:sync-engine-shared` now also owns
the platform-neutral CloudDrive directory-browser contract used by both Android
TV Settings and Windows Cloud/RSS Settings, including token-root preparation,
scoped navigation, visible-folder filtering, loading state, and selection
status shaping. It also owns `CloudDriveDirectoryBrowserCoordinator`, so Android
TV Settings and Windows Cloud/RSS Settings share directory-picker form
validation, opening, loading, stale-result filtering, error propagation, and
browsing-status orchestration instead of rebuilding that flow in each UI shell.
`CloudDriveRssActionCoordinator` now also owns CloudDrive login and API-token
verification form validation, status mapping, and normalized-token handoff
through `CloudDriveActionResult`, leaving Android TV Settings and Windows
Settings to mutate only busy flags and local UI state around the shared action
result.
The same coordinator now also owns RSS subscription validation/save/delete
status mapping and manual run started/completed/failure status mapping through
`RssSubscriptionActionResult` and `CloudDriveRunActionResult`; Windows keeps
only its platform-specific subscription refresh/form reset and post-run
linked-source rescan around the shared results.
Android TV WebUI and Windows WebUI now call that same coordinator for
CloudDrive config save, login, API-token verification, manual run, and RSS
subscription save/update/delete; `web-control-core` keeps only DTO/HTTP error
shaping while shared actions preserve token-info responses, repository-assigned
RSS ids, and existing subscription `lastCheckedAt` across Android Room and
desktop JSON repositories. The focused shared/WebUI/desktop gate passed in
Gradle MCP build `b-163` with 282 tests passed, and Android debug assemble
passed in `b-164`.
`:repository-api` now also owns WebUI indexed-entry-to-episode mapping and
Local/WebDAV/SMB playable URI resolution. Android TV WebUI no longer keeps a
private `URLEncoder` path builder, Windows WebUI no longer keeps a private
indexed-entry episode mapper, and both WebUI services use
`toIndexedEpisode`/`toIndexedEpisodes` with one WebDAV/SMB encoded remote-path
rule. `PlayableUriResolverTest` covers WebDAV, SMB, already-playable URLs,
repository source-id resolution, and ordered indexed episodes; `:repository-api:test`
passed in Gradle MCP build `b-167` with 74 tests passed, and the
Android/WebUI/desktop compile gate passed in `b-166`.
`:web-control-core` now also owns `WebControlLibraryLoader` for WebUI
library/search/detail/continue-watching shaping and playback episode lookup
over cached metadata plus indexed Local/WebDAV/SMB entries. Android TV
`WebControlService` and Windows `DesktopWebControlService` delegate those flows
to the shared loader and keep only platform-specific playback launch/control
work around it. `WebControlLibraryLoaderTest` covers indexed fallback library
rows, cached metadata priority, Chinese-title search, same-anime merge
preference, WebDAV playable detail episodes, continue-watching fallback, and
cached-episode lookup; `:web-control-core:test` passed in Gradle MCP build
`b-171` with 74 tests passed, and the combined repository/WebUI/desktop/Android
gate passed in `b-172`.
`:repository-api` now also owns `LibraryEpisodeResolver` and
`MediaIndexPosterGroup.toIndexedAnime()` for shared continue-watching and
playback episode resolution. Android TV Library, WebUI library loading, and the
Windows Details continue-watching panel now use the same cached-episode
priority, indexed Local/WebDAV/SMB path lookup, completion filtering,
progress-field attachment, same-anime merge handling, fallback-anime shaping,
and result/error-preserving lookup instead of duplicating path/name inference in
their UI layers. `LibraryEpisodeResolverTest` covers cached lookup priority,
WebDAV playable indexed lookup, completed-progress filtering, progress field
attachment, indexed continue-watching fallback, and progress-error preservation;
desktop detail tests cover the Windows recent-playback display adapter and
selection retention; the focused repository/desktop gate passed in Gradle MCP
build `b-180` with 296 tests passed.
`core:model` owns the Cloud/RSS form normalization helpers used
by both Android TV Settings and Windows Cloud/RSS Settings for config trimming,
interval/proxy-port bounds, RSS subscription fallback names, blank URL
rejection, same-URL subscription update identity/last-check preservation, and
CloudDrive login/API-token request validation. Windows no longer keeps a
`sync-engine-shared` display-forwarding layer for those status helpers, source
labels, or legacy-status fallback text; desktop call sites now import the shared
model helpers directly, while
`sync-engine-shared` retains only real Cloud/RSS runtime contracts such as the
directory browser and scheduler state adapter. CloudDrive directory picker
endpoint/token readiness checks now also live in `core:model`, so Android TV
and Windows report the same missing-endpoint vs missing-login/token states
before opening the shared directory browser. `core:model` also owns the
Bangumi token save-form result shared by Android TV Settings and Windows
metadata settings, including trimming, blank-input preservation, configured
state, and TV-facing status copy. `:sync-engine-shared` also owns Bangumi metadata refresh/cache merge behavior through `BangumiMetadataRefreshCore`,
including index-entry-to-local-episode/cache-id mapping, so Android Detail
rescrape, Windows single-item apply, and Windows sync cache preparation use the
same details/episodes fetch, cached-episode-id check, local episode merge,
cache id rewrite, and scraper-error propagation paths. `:repository-api` also
owns media-index replacement, selected-entry preservation helpers, the
playback-preference contract used by Android TV Player/Settings and Windows
playback settings, and the scan-preference contract used by Android TV
Library/Details/Settings plus Windows Settings, keeping those state-merge,
playback-end preference, auto-scan interval, last-scan, and same-anime merge
rules out of platform UI shells. Android business modules were tightened so
`metadata`, `scanner`, `sync-engine`, `web-control`, `scraper`, and
`player-core` depend on shared API modules or repository interfaces instead of
leaking Android data/media-source implementations through unrelated module
boundaries. `SettingsPreferenceActionCoordinator` now also sits in
`:repository-api`, so Android TV Settings and Windows Settings share
scan-preference loading, auto-scan toggle persistence, interval
hour-to-millisecond conversion, same-anime merge persistence, and playback-end
action persistence through the same refreshed snapshots/results; the focused
shared/desktop gate passed in Gradle MCP build `b-146`, and Android debug
assemble passed in `b-147`. `MediaSourceActionCoordinator` now also sits in
`:repository-api`, so Android TV Settings and WebUI source APIs share source
add/update/remove orchestration, password preservation on edits,
connection-state and last-scan preservation, and post-add connection-state
updates from one repository action layer; the focused shared/desktop gate
passed in Gradle MCP build `b-153` with 358 tests passed, and Android debug
assemble passed in `b-154`. Windows Local/WebDAV/SMB open actions now also
route through `MediaSourceActionCoordinator` with `DesktopMediaSourceFactory`,
so newly opened desktop sources use the same post-add connection-state update
instead of being marked connected unconditionally; `DesktopSourceActivationTest`
covers connected and disconnected open results, the focused desktop gate passed
in Gradle MCP build `b-155` with 214 tests passed, the shared/Android/WebUI
compile gate passed in `b-156`, and Android debug assemble passed in `b-157`.
Windows now also starts desktop-opened Local/WebDAV/SMB sources as disconnected
before the shared add/test/update action writes the final connection state, and
desktop plus WebUI source removal now route through
`MediaSourceActionCoordinator.removeSource`; the focused desktop/WebUI gate
passed in Gradle MCP build `b-158` with 288 tests passed, and Android debug
assemble passed in `b-159`.
`SyncEngineImplTest` also locks imported NFO resume positions as milliseconds,
matching the shared model. `core:model` now also owns shared
Episode ordering, season grouping/selection/filtering, distinct episode counts,
and the Android TV Detail continue-play target and button-label rule, keeping
the chosen episode and visible label aligned instead of leaving that decision in
the UI. `:repository-api` now also owns
media-index poster grouping, poster-group titles, same-anime merge keys, and
detail episode ordering/selection, so the Windows Library poster wall and
Details episode shelf consume reusable index presentation rules instead of
carrying them inside Compose panels.

## Prompt-to-Artifact Completion Matrix

| Objective deliverable | Concrete artifacts inspected | Verification evidence | Remaining gap |
|---|---|---|---|
| Keep the original Android TV app working | `app/src/main/kotlin/com/miruplay/tv/MainActivity.kt`, `ui-tv/`, Android Gradle modules, `tools/smoke-android-tv-ui.ps1` | `:app:assembleDebug` passed in the latest preservation check. `tools/smoke-android-tv-ui.ps1` installed the debug APK on emulator `<android-tv-device-id>`, generated seven playable MP4/NFO fixture shows, pushed them under `/sdcard/Movies`, launched with `test_local_path`, clicked scan, verified Library content requests poster focus, used DPAD Right/Left plus Center to open a fixture poster, used DPAD Down from Details Play to focus the first episode row, used DPAD Center on that focused episode row to open Player, verified Android Back returns from Player to Details and from Details to the poster-focused Library wall, used DPAD Up/Right/Center from that returned poster wall to open Settings, used DPAD Down/Center inside Settings to open the media-source panel, verified the auto-added local source and source form, verified DPAD Right focuses the auto-added source card, Left returns to the media-source menu, Center opens the edit-source form without losing card focus, Right focuses the source delete button, Center removes the source while keeping the media-source empty state visible, then continues DPAD Down through Playback, CloudDrive, Scan, and Metadata settings with matching menu focus, and captured `build/android-tv-qa/run-20260520-190519/android-tv-library.png`, `android-tv-library-dpad-poster.png`, `android-tv-details.png`, `android-tv-details-episode-focus.png`, `android-tv-player.png`, `android-tv-library-return.png`, `android-tv-settings.png`, `android-tv-settings-sources.png`, `android-tv-settings-source-card-focus.png`, `android-tv-settings-source-edit.png`, `android-tv-settings-source-delete-focus.png`, `android-tv-settings-source-deleted.png`, `android-tv-settings-playback.png`, `android-tv-settings-cloud-drive.png`, `android-tv-settings-scan.png`, `android-tv-settings-metadata.png`, XML dumps, and `android-tv-smoke-report.json`. | Broader TV-device regression still needs manual coverage for less-traveled focus paths. |
| Add a Windows desktop entry | `desktop-app/build.gradle.kts`, `MiruPlayDesktopComposeApp.kt`, `.github/workflows/ci.yml` | `mainClass` points to `com.miruplay.tv.desktop.MiruPlayDesktopComposeAppKt`; the native window title now uses the TV-facing `MiruPlay 桌面版` copy; the app-image gate launches `MiruPlay.exe --miruplay-desktop-smoke` and validates a JSON report for entry point, window title, start section, and bundled runtime paths; the new installer gate can build MSI/EXE artifacts from that verified app image when WiX is available and records SHA256/size/signing evidence, with `tools/assert-windows-installer-report.ps1` checking that report against the generated artifact; CI now uploads a versioned lightweight Windows desktop ZIP from `windows-latest`, and nightly main/master releases attach the same-version ZIP alongside the Android APK; `:desktop-app:test`, lightweight `:desktop-app:installDist -PbundleMpvRuntime=false`, lightweight `:desktop-app:distZip -PwindowsPackageVersion=2026.05.24 -PbundleMpvRuntime=false`, full `:desktop-app:smokePackagedMpvRuntime -PmpvRuntimeSource=runtime\mpv -PrequireMpvRuntime=true -PrequiredRifeBackends=NVIDIA,DIRECTML`, and native `:desktop-app:smokeNativeAppImageRuntime -PmpvRuntimeSource=runtime\mpv -PrequireMpvRuntime=true -PrequiredRifeBackends=NVIDIA,DIRECTML` pass. | Signed release installer still needs release signing inputs and toolchain evidence. |
| Use Compose-family UI on both targets | Android remains Compose/Compose TV; desktop default entry is Compose Multiplatform Desktop | `desktop-app` applies Compose plugins, `application.mainClass` points at `MiruPlayDesktopComposeAppKt`, screenshot QA launches the Compose entry, the old Swing shell has been removed from production sources, and root `checkDesktopComposeOnly` fails if Swing UI imports/classes or `coroutines-swing` return. | Covered structurally; full Android-TV-vs-desktop screen parity is tracked separately. |
| Preserve media management capabilities on desktop | `media-source-desktop`, `scanner-desktop`, `repository-api`, `repository-desktop`, `scraper-desktop`, `sync-engine-desktop`, `cloud-drive-desktop`, Compose Library/Details/Settings panels | Unit and integration coverage now includes shared display/subtitle/index/batch planning helpers, desktop details rows, playback progress, RSS scheduler, CloudDrive gRPC client, RSS offline submission through real `GrpcCloudDriveClient`, and remote playback command security that keeps WebDAV/SMB hosts and credentials out of mpv command lines. | Real CloudDrive2 server live QA remains open for token validation, offline submission, torrent staging, and organization. |
| Use mpv as Windows playback backend | `player-mpv`, `MpvProcessPlayer`, `MpvIpcClient`, Compose Player panel, `tools/smoke-desktop-mpv-launch-ui.ps1` | `:player-mpv:test` passes; desktop app launches `MpvProcessPlayer`; progress sync polls mpv `time-pos` while playing; RIFE is opt-in by default on desktop so low-capability hosts can launch plain mpv first; missing mpv/RIFE launch failures now render TV-facing Chinese guidance for checking the runtime, preparing a backend, or turning RIFE off, and the `Check runtime` verifier output localizes runtime readiness, manifest, source, required backend, and missing-file details. The Player `全屏` toggle still feeds mpv `--fs`, and now also drives Compose Desktop's native `WindowPlacement.Fullscreen` only while the Player route is active, restoring the previous floating/maximized placement when leaving Player or disabling fullscreen; `DesktopSectionContractTest` covers the route-gated decision. The GUI mpv launch smoke generates a local Y4M sample, fills it into the Windows Player, disables RIFE for host-safe playback, clicks Play, confirms an `mpv.exe` child process containing the sample path, exercises Pause, -10s, +30s, and Stop through keyboard focus, verifies a persisted progress record, and captures settings-focus/runtime-focus/launched/keyboard-control/stopped Player screenshots. `MpvProcessPlayer.stop()` escalates from IPC quit to destroy and force-destroy, including descendant process cleanup when available. | External-process mpv mode is covered; embedded libmpv is intentionally deferred. |
| Plan and integrate a bundled RIFE-capable mpv runtime | `runtime/mpv`, `tools/prepare-mpv-runtime.ps1`, `desktop-app:verifyMpvRuntimePayload`, `smokeMpvRuntime`, `smokePackagedMpvRuntime`, `smokeNativeAppImageRuntime`, `packageWindowsAppImage`, `tools/smoke-mpv-rife.ps1`, `tools/assert-mpv-rife-report.ps1`, `docs/mpv-runtime-packaging.md` | Runtime verifier and Gradle smoke pass for NVIDIA/DIRECTML scripts; `MpvRuntimeVerifier` now cross-checks `runtime-manifest.json` declared files/directories and required RIFE backends, rejects unknown backend names plus absolute/`..` manifest entries as incomplete evidence, and the desktop runtime panel localizes that guidance. Gradle runtime gates now use the same manifest-evidence checks for source runtime payloads, packaged zip entries, and native app-image runtime content. `:desktop-app:smokePackagedMpvRuntime -PmpvRuntimeSource=runtime\mpv -PrequireMpvRuntime=true -PrequiredRifeBackends=NVIDIA,DIRECTML` builds `desktop-app.zip`, launches the packaging runtime with `mpv.exe --version`, and verifies the bundled zip contains `runtime/mpv/mpv.exe`, `runtime-manifest.json`, the NVIDIA/DIRECTML scripts, and manifest-declared entries. `:desktop-app:smokeNativeAppImageRuntime -PmpvRuntimeSource=runtime\mpv -PrequireMpvRuntime=true -PrequiredRifeBackends=NVIDIA,DIRECTML` builds the Windows `jpackage` app image, verifies `MiruPlay.exe`, validates `app/MiruPlay.cfg` main class/classpath entries, checks the app-image `runtime/mpv` payload and manifest-declared entries, and launches the app-image entry in headless smoke mode to verify the resolved runtime paths before installer/signing work. Prior DirectML VapourSynth/RIFE smoke proved the payload path; `-Backend ALL -AllowFailures` reports the backend matrix. `tools/smoke-mpv-rife.ps1 -ReportPath ...` writes a JSON evidence bundle with mpv version, host OS/CPU/GPU diagnostics, backend statuses, exit codes, log paths, and runtime manifest evidence for target-host QA. `tools/assert-mpv-rife-report.ps1 -RequireRuntimeManifest` validates those JSON reports for host diagnostics, clip shape, backend entries, required backend PASS status, and a present/problem-free manifest that declares the required backend(s); `tools/verify-windows-port.ps1 -Rife` now runs that stricter validation immediately after generating the report. | Local RIFE playback is non-blocking on this host; backend performance/compatibility must be validated on target hardware. |
| Make Windows UI visually match the TV UI | `ui-design`, `ui-tv/.../theme/Theme.kt`, `MiruPlayDesktopComposeApp.kt`, root `checkUiPaletteDrift`, `tools/capture-desktop-ui.ps1`, `tools/smoke-android-tv-ui.ps1`, `build/desktop-ui-qa/*.png`, `build/android-tv-qa/run-20260520-190519/*.png` | TV and desktop now derive the MiruPlay red/dark/blue/text/card palette from the same `MiruPlayPalette` constants. `checkUiPaletteDrift` fails if TV or desktop UI reintroduces raw shared palette literals outside `:ui-design`. Android TV smoke captures the media-first Library, poster-focused DPAD traversal, Details hero, focused Details episode row, full-screen Player overlay, Android Back return to the poster-focused Library wall, DPAD entry into Settings, DPAD entry into the Settings media-source panel, source-card Right/Left focus return, source-card Center edit form, source-card Right delete-button focus, delete-button Center empty-source state, and continued menu-category traversal through Playback, CloudDrive, Scan, and Metadata against a playable fixture. Windows Library uses the TV-style full-width `探索` header with the Android TV action pair `扫描`/`设置`, TV empty state, and a 6-column poster-wall first screen after scanning or loading a saved index; the desktop route chrome now also uses `探索`/`详情`/`播放`/`设置` labels and the native window title now uses `MiruPlay 桌面版` instead of English desktop labels. The Library header action row now has unit-covered TV-style Left/Right movement plus Down focus bridging into the media/source content, and the first content row can move Up back to the header action row while preserving the last focused header action. Highest-heat/recent rows plus search/source controls sit below the wall, and those horizontal rows now share the same Enter activation and unit-covered left/right plus wall-to-shelf/search/source Up/Down movement as TV-style poster shelves. Source management now shows the active saved source as a two-line TV-style focus card with visible source name/type, TV-facing labels and status messages, middle-compacted local/WebDAV/SMB paths, local source fields and action buttons with unit-covered row movement, and unit-covered plus real-window directional saved-source movement; the saved-source picker now owns source-switch keys instead of relying on a panel-wide key handler. WebDAV and SMB setup now render as separate TV-style remote source cards with compact endpoint previews beside a remote-browser panel that also compacts long paths; their editor fields plus open/scan actions now have unit-covered focus movement from WebDAV fields/actions down into the SMB field/action rows, Right-to-browser exits from the rightmost editor fields/actions, and browser-row/browser-up Left returns to the last editor target while keeping first-row Up as parent navigation. Cloud/RSS settings now render as TV-style overview, CloudDrive2, sync-path, subscription, and scheduler cards with localized Chinese labels, previews, empty states, common status copy, unit-covered credential/sync/RSS edit field movement into adjacent actions/toggles, and unit-covered action-grid movement across credential, login/verify, scan-source, sync, RSS, subscription, and scheduler controls; RSS subscription rows now handle `Up`/`Down` selection; `tools/capture-desktop-ui.ps1` now captures both `settings.png` and `settings-cloud.png` and asserts visual quality/distinctness. The latest Settings keyboard smoke writes its isolated JSON store as UTF-8 without BOM for Windows PowerShell compatibility, preloaded two RSS subscriptions, selected the first visible row, sent `Down`, and captured `build/desktop-keyboard-focus-ui/run-20260521-175654/keyboard-settings-cloud.png`, `keyboard-settings-rss-first.png`, `keyboard-settings-rss-second.png`, `keyboard-settings-scheduler-started.png`, and `keyboard-settings-scheduler-stopped.png` to prove localized fixture loading, row navigation, and scheduler action activation. The latest source-management GUI smoke preloaded two deep local fixture sources, used keyboard `Up` on the saved-source card to switch from Season 02 back to Season 01, verified the subsequent scan indexed the keyboard-selected source id, and captured `build/desktop-source-management-ui/run-20260520-212601/source-management-saved-source-keyboard.png`, `source-management-scanned.png`, `source-management-controls.png`, `source-management-cleared.png`, and `source-management-removed.png`; `source-management-controls.png` shows the scan status rendered as `扫描完成：1 个视频，2 个目录。`. The latest WebDAV GUI smoke captured `build/desktop-webdav-source-ui/run-20260520-121024/webdav-source-opened.png`, `webdav-source-browsed.png`, `webdav-source-keyboard-up.png`, `webdav-source-keyboard-browse.png`, `webdav-source-keyboard-select.png`, `webdav-source-poster-wall.png`, `webdav-source-details.png`, and `webdav-source-player.png` with the remote card layout plus keyboard parent/row navigation. The latest SMB GUI smoke captured `build/desktop-smb-source-ui/run-20260520-090324/smb-source-opened.png`, `smb-source-poster-wall.png`, `smb-source-details.png`, and `smb-source-player.png` against the approved `临时文件\测试` directory with redacted credentials. The latest generated local-source smoke captured `build/desktop-local-source-ui/run-20260520-162054/local-source-poster-keyboard.png`, `local-source-details.png`, `local-source-details-episodes.png`, `local-source-details-episode-back-to-hero.png`, `local-source-details-episode-selected.png`, `local-source-details-episode-to-bangumi.png`, `local-source-details-bangumi-back-to-episode.png`, and `local-source-player.png`, proving poster-wall Right -> Enter keyboard selection before Details, Details hero stat pills, Details hero Down to the TV-style episode shelf, episode shelf Up back to the hero, episode-row Down to Bangumi, Bangumi Up back to the episode shelf, and episode-row Enter Player handoff. The latest Bangumi metadata GUI smoke captured `build/desktop-bangumi-metadata-ui/run-20260520-213906/bangumi-focus-bangumi.png`, `bangumi-search-results.png`, `bangumi-metadata-applied.png`, and `bangumi-metadata-cleared.png`, proving the Bangumi action grid can enter the search-result list, return to Apply match, and clear metadata by keyboard. The latest real-library smoke against `D:\Software\dufs` captured `build/desktop-local-source-ui/run-20260520-080904/local-source-scanned.png`, which indexed 22 videos and opens directly on the poster wall. Poster selection routes directly to Details, Details starts with a TV-style hero plus indexed episode shelf, Player opens without the desktop rail into a TV-like playback stage with top return, centered transport controls, bottom timeline/status chips, localized mpv status chips, and TV-facing `媒体 URI 或文件路径`/`起播秒数`/`外挂字幕路径` settings labels below the stage. Screenshot QA covers Library/Details/Player/Settings/Cloud settings and asserts window size, dark palette, red accent, readable light text, visual diversity, and per-section distinctness. | Android TV Settings category/page traversal, Settings summary quick-action rows, Cloud/RSS credential/sync/RSS edit fields, action grids, toggles, and subscription rows, route rail, Library header action parity/focus bridge, poster-wall, poster shelves, search row, and source bridge movement, source-management local/remote fields/actions, remote editor/browser focus bridge, and saved-source card, Details hero actions/stat pills, Details episode shelf boundary exits, Continue watching recents, remote-browser list, Player transport, and Bangumi metadata-list/action grid are now covered by GUI or unit tests; remaining less-traveled keyboard/DPAD paths still need more TV-parity work. |
| Settings and route rail keyboard/DPAD navigation | `DesktopCloudRssPanel.kt`, `DesktopNavigation.kt`, `MiruPlayDesktopComposeApp.kt`, `DesktopKeyEvents.kt`, `tools/smoke-desktop-keyboard-focus-ui.ps1`, `build/desktop-keyboard-focus-ui/run-20260521-175654/*.png` | The Settings category menu, Settings summary quick-action rows, Cloud/RSS credential/sync/RSS edit fields, Cloud/RSS action grids, Cloud/RSS RSS subscription rows, the empty RSS subscription state, and desktop route rail now keep focus on the selected row/control and handle directional keys with TV-style edge stops instead of wraparound; the desktop root also handles `Esc`, TV remote `Back`, and navigation back/out keys as Back equivalents from Player to Details and from Details/Settings to Library without swallowing `Backspace` text editing. TV action buttons, desktop selectable rows, saved-source picker, Settings category rows, route-rail rows, and custom desktop activation handlers now share keyboard Enter, numpad Enter, and TV DPAD Center confirm semantics across saved-source cards, poster cards, remote rows, Bangumi rows, Player round buttons, common TV-style buttons/cards, and the Settings section menu. Navigation-only field/action focus bridges now also share one KeyDown gate across Library/source/search, remote-source, Cloud/RSS, Details, Bangumi, route-rail, Player stage-return, playback-setting, and runtime rows; remaining explicit desktop key-down branching is centralized in the shared key helpers or the root Back handler, and desktop selectable rows expose the same confirm-or-navigation fallback so Cloud/RSS subscription rows, CloudDrive directory rows, Library empty states, remote-browser rows, Bangumi match/result rows, Details episode rows, Continue watching rows, and media-detail rows do not need separate stacked key handlers for Up/Down movement. `DesktopSettingsPanelTest` covers Settings category edge stops, Settings category row confirm semantics, Settings summary quick-action row movement, Cloud/RSS credential/sync/RSS edit field movement into adjacent actions/toggles, Cloud/RSS action-grid movement, RSS-to-scheduler focus bridging, RSS subscription edge stops, empty-subscription entry/exit, and null-selection entry, while `DesktopSectionContractTest` covers route rail edge stops, accepted Back key aliases, Backspace exclusion, and the route Back hierarchy; `DesktopChromeTest` covers shared confirm-key semantics including DPAD Center, disabled-control behavior, and selectable-row confirm/navigation fallback; `DesktopSourcePickerTest` covers saved-source picker confirm/navigation key semantics through the same helper; `DesktopBangumiNavigationTest` covers Bangumi list movement through the same row surface; `DesktopDetailHeroTest` covers Details episode, recent, and media-detail focus targets through the same row surface. The Windows GUI smoke opens Settings, sends two `Down` key presses from Sources to CloudDrive, selects a preloaded RSS row, sends `Down` to move from Beta to Alpha, then focuses Details and sends `Down` to Player, captures `keyboard-nav-details.png` and `keyboard-nav-player.png`, sends `Esc` back to Details and `Esc` back to Library, captures `keyboard-back-details.png` and `keyboard-back-library.png`, and asserts the content region changed for these key paths. | Remaining less-traveled keyboard/DPAD paths outside these menus still need more TV-parity work. |
| Library poster-wall keyboard/DPAD navigation | `DesktopLibraryPanels.kt`, `DesktopPosterGroupingTest.kt`, `DesktopSourcePickerTest.kt`, `DesktopSectionContractTest.kt`, `tools/smoke-desktop-local-source-ui.ps1`, `build/desktop-local-source-ui/run-20260520-162054/*.png` | The poster wall now keeps focus on the selected poster, moves selection with directional keys, clamps Down navigation to the nearest poster when the next row is short, exits Up from the top row to the Library header action row, and opens Details on `Enter`; the Library header action row moves between `扫描` and `设置` with Left/Right, moves Down into Library content, and preserves the last focused header action when content returns upward. The Library media area now also moves downward from the poster-wall bottom row into `最高热度` or directly into `最近添加` when no featured row exists, moves between the featured and recent shelves with `Up`/`Down`, exits into the search row, and the search row moves horizontally between field/action while bridging `Up` back to media and `Down` into the source panel. `DesktopPosterGroupingTest` covers poster-wall short-row movement, top-row Up exit, shelf horizontal/vertical movement, media-to-search exit, and search row movement; `DesktopSectionContractTest` covers the header action row; `DesktopSourcePickerTest` covers the source panel `Up` bridge back to the search row. The generated local-source GUI smoke scans two fixture shows, sends `Right`, captures `local-source-poster-keyboard.png` with the second poster selected, sends `Enter`, and verifies Details and Player handoff. | Broader GUI traversal across header/search/source bridges can still be expanded later. |
| Details hero keyboard/DPAD navigation | `DesktopDetailsPanels.kt`, `DesktopDetailHeroTest.kt`, `DesktopBangumiNavigationTest.kt`, `tools/smoke-desktop-local-source-ui.ps1`, `build/desktop-local-source-ui/run-20260520-162054/*.png` | The Details hero now requests focus on the primary Play action, moves between Play and Back to poster wall with `Left`/`Right`, and activates the focused action with `Enter`; it also mirrors the Android TV `DetailStats` style with indexed episode/season/metadata stat pills. The episode shelf now pages current-season episode rows in six-row TV-style windows so `Down` from episode 6 moves to episode 7 on the next page, `Up` moves back across the page boundary, and longer seasons show a Chinese range summary instead of pushing the downstream Bangumi/media-detail panels out of reach. The episode shelf still exits upward through the multi-season selector when multiple seasons exist, otherwise back to the hero, and exits downward to Bangumi; the season selector moves with `Left`/`Right`, `Down` returns to the selected episode row, and `Up` returns to the hero. When no related episode rows exist, the shelf exposes a focusable TV empty state that returns to the hero with `Up` and continues to the next Details panel with `Down`. Continue watching still moves downward into Bangumi, Bangumi bottom actions and lists can now move downward into media details, media-detail rows page in six-row TV-style windows while preserving two-column `Left`/`Right` movement and showing a Chinese range summary for long detail sets, and an empty media-detail panel still exposes a focusable TV empty state with `Up` returning to Bangumi. Bangumi top action `Up` returns focus to the episode shelf. The generated local-source GUI smoke opens Details from the poster wall, captures `local-source-details.png`, sends `Down`, captures `local-source-details-episodes.png`, sends `Up`, captures `local-source-details-episode-back-to-hero.png`, re-enters the shelf, moves to Frieren episode 2, captures `local-source-details-episode-selected.png`, sends `Down`, captures `local-source-details-episode-to-bangumi.png`, sends `Up`, captures `local-source-details-bangumi-back-to-episode.png`, then presses `Enter` and verifies Player receives Frieren episode 2. | Deeper Details GUI traversal can still be expanded later. |
| Continue watching keyboard/DPAD navigation | `DesktopDetailsPanels.kt`, `MiruPlayDesktopComposeApp.kt`, `DesktopDetailHeroTest.kt`, `tools/smoke-desktop-mpv-launch-ui.ps1`, `build/desktop-mpv-launch-ui/run-20260521-123909/*.png` | The Details hero now moves focus down into Continue watching when recent records exist; recent rows move with `Up`/`Down`, activate with `Enter`, and first-row `Up` returns to the `刷新`/`清除条目` action pair. Those actions now move horizontally with `Left`/`Right`, move back into the first recent row with `Down`, and exit upward to the previous Details panel with `Up`; when no records exist, action `Down` exits to the next Details panel. The mpv GUI smoke launches a generated Y4M sample, stops playback, returns to Details, sends `Down` then `Enter` on the recent row, captures `mpv-recent-keyboard-selected.png`, and verifies the same sample progress remains persisted with play count and saved position. | Broader GUI traversal across the action pair can still be expanded later. |
| Remote browser keyboard/DPAD navigation | `DesktopLibraryPanels.kt`, `DesktopSourcePickerTest.kt`, `tools/smoke-desktop-webdav-source-ui.ps1`, `build/desktop-webdav-source-ui/run-20260520-121024/*.png` | The remote browser now keeps focus on the selected row, moves with `Up`/`Down` without opening directories, opens/selects with `Enter`, uses first-row `Up` to navigate to the parent/root path, and uses `Left` to return focus to the last remote editor field/action. When a remote directory is empty, the browser also exposes a focusable TV empty state: `Down` from `上级` lands on the empty row, `Up` returns to `上级`, and `Left` returns to the editor side. The remote editor side also exits into the browser with `Right` from rightmost WebDAV/SMB fields and actions. `DesktopSourcePickerTest` covers editor-to-browser focus targets, browser-row return-to-editor targets, row movement, empty-state entry/exit, and preservation of first-row parent navigation. The loopback WebDAV GUI smoke opens the fixture directory, sends `Up`, captures `webdav-source-keyboard-up.png` at the root, sends `Enter` back into the fixture directory, sends `Down`, captures `webdav-source-keyboard-browse.png`, sends `Enter`, captures `webdav-source-keyboard-select.png`, and then scans/opens Details/Player from the same remote source. | Deeper multi-level remote browsing can still be expanded later. |
| Player transport keyboard/DPAD navigation | `DesktopPlaybackPanels.kt`, `tools/smoke-desktop-mpv-launch-ui.ps1`, `build/desktop-mpv-launch-ui/run-20260521-123909/*.png` | The Player stage now keeps focus on the primary transport, moves across active controls with `Left`/`Right`, activates with `Enter`, moves `Up` from any active transport control to `返回详情`, moves `Down` from `返回详情` back to the primary transport, and moves `Down` from transport into the playback settings. Playback settings now use explicit TV-style movement from media path to start seconds, down to subtitle, down to fullscreen/keep-open/RIFE/backend toggles, then down into the runtime card; runtime `Up` returns to the backend toggle and the runtime form still moves down through `mpv.exe`, `portable_config`, and `检查运行时`. The mpv GUI smoke captures `mpv-settings-focus.png` and `mpv-runtime-focus.png`, launches a generated Y4M sample, sends `Enter`, `Left+Enter`, `Right+Right+Enter`, and `Right+Enter` to pause, seek, and stop, then verifies mpv exited and progress persisted; `DesktopPlaybackPanelTest` covers the Player-stage Up/Down focus topology, settings field/toggle/runtime bridging, runtime return-to-settings, and left/right edge stops. | Full-screen mpv/window-manager specific key paths can still be expanded later. |
| Bangumi metadata-list keyboard/DPAD navigation | `DesktopBangumiPanel.kt`, `DesktopBangumiNavigationTest.kt`, `tools/smoke-desktop-bangumi-metadata-ui.ps1`, `build/desktop-bangumi-metadata-ui/run-20260520-213906/*.png` | Bangumi batch matches, candidate review rows, search results, and action buttons now share deterministic key handling: `Up`/`Down` moves through visible result rows, `Right` enters candidate review or enters the first visible match list from the action grid, `Left` returns to batch matches or exits search results back to Apply match, action buttons form a two-column grid, and `Enter` selects the focused row/button. The Bangumi panel now also localizes action labels, section labels, empty states, batch chips, candidate labels, and repository status strings to TV-facing Chinese copy at the desktop display layer. `:desktop-app:test` covers row ordering, horizontal candidate review entry/exit, visible-row clamping, edge stops, action-grid movement, action-to-list handoff, list-to-action handoff, top-action `Up` exit, and the localized Bangumi display text. `:scraper-desktop:smokeBangumiLive` now verifies live Bangumi search/details/episodes and writes token-free JSON evidence. The Windows GUI smoke opens Details, uses keyboard `Use selected`, `Search`, result-list handoff, `Apply match`, and `Clear metadata`, verifies Bangumi source/id/title are persisted then cleared, and captures `bangumi-details-ready.png`, `bangumi-focus-bangumi.png`, `bangumi-search-results.png`, `bangumi-metadata-applied.png`, and `bangumi-metadata-cleared.png`; `bangumi-focus-bangumi.png` shows the localized `Bangumi 元数据`, `使用当前条目`, and `当前索引` controls in the real window. | Broader live-service regression can be repeated with the smoke tasks as needed. |
| Provide auditable verification gates | Gradle MCP build records, `.github/workflows/ci.yml`, scripts under `tools/`, this audit document | CI now runs the Android debug build plus desktop/shared JVM checks on mainline branches and `codex/**` push branches: `checkDesktopComposeOnly`, `checkDesktopPresenterSeparation`, `checkUiPaletteDrift`, `:core:model:test`, `:media-source-api:test`, `:metadata-core:test`, `:repository-api:test`, `:cloud-drive-api:test`, `:sync-engine-shared:test`, `:media-source-desktop:test`, `:scanner-desktop:test`, `:repository-desktop:test`, `:scraper-desktop:test`, `:player-mpv:test`, `:cloud-drive-desktop:test`, `:sync-engine-desktop:test`, `:sync-engine-desktop:smokeCloudDriveRssScheduler`, `:desktop-app:test`, and lightweight `:desktop-app:installDist -PbundleMpvRuntime=false`. A Windows runner also builds and uploads a versioned lightweight ZIP through `:desktop-app:test :desktop-app:distZip -PwindowsPackageVersion=... -PbundleMpvRuntime=false`, and nightly main/master releases attach that same-version ZIP alongside the Android APK. `tools/verify-windows-port.ps1` now auto-selects JDK 21 when launched from a newer JDK shell and the safe default gate includes the token-free Cloud/RSS scheduler elapsed-time smoke. `DesktopRepositoriesTest` now recursively removes its own temporary store directory, avoiding Windows `DirectoryNotEmptyException` cleanup flakes in the repository persistence gate. Latest local commands are listed below with passing evidence for Android build, desktop tests, mpv tests, CloudDrive loopback tests, scheduler smoke, runtime smoke, packaged runtime zip smoke, DirectML RIFE smoke, and screenshot QA. | Hardware/cloud/live-service checks are intentionally tracked as not achieved. |

Completion decision: do not mark complete. The project has a usable Windows
Compose Desktop port and preserved Android debug build evidence, but the
objective requires real-world confidence across bundled RIFE backends and full
CloudDrive2 behavior. Those still depend on target GPU/driver/plugin stacks and
a live CloudDrive2 environment.

## Checklist

| Requirement | Current evidence | Status |
|---|---|---|
| Android TV remains buildable | `.\gradlew.bat :app:assembleDebug` passed in the latest preservation check after the desktop port work. `.\tools\smoke-android-tv-ui.ps1` also installed and launched the debug APK on emulator `<android-tv-device-id>`, recording `build/android-tv-qa/run-20260520-190519`. | Covered for debug build and current device smoke |
| Android TV uses Compose TV | Existing Android app remains `MainActivity` + Compose navigation and `ui-tv` Compose/TV screens. | Covered structurally |
| Windows desktop entry exists | `:desktop-app` JVM application now points `mainClass` at `com.miruplay.tv.desktop.MiruPlayDesktopComposeAppKt`; `MiruPlayDesktopComposeApp.kt` is a Compose Desktop window. | Covered structurally |
| Windows UI uses Compose Desktop | `desktop-app` applies `org.jetbrains.compose` and `org.jetbrains.kotlin.plugin.compose`; the default entry renders local library source/scan/search, WebDAV/SMB open/browse/scan, single-item Bangumi search/apply/clear, batch Bangumi preview/apply/undo, continue-watching recents, mpv runtime, RIFE, command preview, Launch/Stop controls, and CloudDrive2/RSS automation with Compose Material 3. | Covered for core desktop workflow |
| Windows visual language matches TV | `ui-design` now owns the shared MiruPlay palette; Android TV `Theme.kt` and Compose Desktop both derive `AnimeRed`, `DarkBg`, `DarkSurface`, `AccentBlue`, `TextPrimary`, `TextSecondary`, and `CardBg` from `MiruPlayPalette`. The root `checkUiPaletteDrift` task guards against raw palette literal drift in `ui-tv/src` and `desktop-app/src`. Android TV Library/Details/Player were refreshed on emulator `<android-tv-device-id>` by `tools/smoke-android-tv-ui.ps1`, including poster focus, DPAD poster -> Details, Details Play -> episode row, and episode row -> Player activation. Compose Desktop Library now puts 6-column poster-wall cards first after scan/load instead of opening on a tool/control panel; saved index entries are restored on startup/source switch so a scanned library opens directly to media. Compose Desktop source management now uses a focused two-line saved-source card that keeps source name/type visible, compacts long paths, and has unit-covered plus real-window directional movement between saved sources; the deep-path GUI smoke covers save, keyboard source switching, scan, clear, and remove in a real window. Compose Desktop remote source setup now uses separate WebDAV/SMB source cards with endpoint previews and a remote-browser panel instead of one dense form; the remote-browser rows now support `Up`/`Down` focus movement, first-row `Up` parent navigation, and `Enter` selection. Compose Desktop Cloud/RSS settings now uses overview/config/subscription/scheduler cards instead of one dense automation form, with credential/sync/RSS edit fields, action grids, enable/proxy/RSS toggle rows, and RSS subscription rows all supporting unit-covered TV-style focus movement. Compose Desktop Details opens directly from a poster click and starts with a TV-style hero for poster/backdrop, title, context, plot, Play, Back-to-poster-wall actions, unit-covered episode shelf plus season-selector focus, and unit-covered media-detail row focus. Compose Desktop Bangumi metadata controls now use TV-facing Chinese labels/status copy while keeping the same action-grid focus behavior. Compose Desktop Player hides the desktop rail and shows a TV-style playback stage with top return, centered play/seek/stop controls, bottom timeline, localized mpv status chips, and RIFE/subtitle chips before exposing TV-facing mpv/RIFE controls below; its media/start/subtitle fields now bridge into the fullscreen/keep-open/RIFE/backend row, those controls bridge down into the runtime card, the runtime `mpv.exe`/`portable_config`/`检查运行时` controls form a unit-covered vertical TV form with `Up` returning to playback settings, and the playback placeholder and route rail subtitle also use TV-facing Chinese copy instead of desktop/runtime English. Compose Desktop Settings now uses focused category rows, quick-action rows, plus TV-style status cards for media sources, playback, scan, metadata, and Cloud/RSS automation instead of generic placeholder summaries, and Settings cross-page references now say playback/details pages in Chinese. Local screenshot QA covers Library, Details, Player, Settings, and Cloud settings screens; keyboard smokes now cover Settings category movement, Cloud/RSS subscription row movement, route rail movement, poster-wall movement/open, Details hero action movement/open, Continue watching row selection, WebDAV remote-browser row/parent movement/open, saved-source card movement, Bangumi action-grid apply/clear, and Player transport movement/actions; unit tests cover Settings summary quick-action row movement, source-management local/remote field movement, saved-source card movement, RSS subscription row movement, Cloud/RSS credential/sync/RSS edit field and action/toggle movement, Bangumi match/candidate/result row navigation and localized labels/status copy, Bangumi/Details bottom-panel focus bridging, Details hero action movement, Details episode shelf season-selector movement, Details media-detail row movement, recent-playback row movement, route rail chrome copy, playback placeholder copy, Settings page-reference copy, Player settings/status localization, Player playback-setting field/toggle/backend row movement, Player Stage-to-settings-to-runtime bridging, and Player runtime form/check movement back to settings. These checks assert minimum TV-style window size, non-tiny PNG output, sampled visual diversity, dark-theme coverage, MiruPlay red accent pixels, readable light text pixels, distinct images, and visible keyboard-driven content changes. Remaining less-traveled keyboard/DPAD paths still need more TV-parity work. | Partial |
| Settings category, Cloud/RSS subscription row, and route rail keyboard navigation | `DesktopCloudRssPanel.kt` and `DesktopNavigation.kt` request focus for the selected rows and handle `Up`/`Down` to move categories/routes/subscriptions with TV-style edge stops. The RSS subscription list now renders six-row TV-style pages while navigation helpers still target the full saved subscription list, keep restored selected subscriptions visible, and show a Chinese range summary when more rows exist; `tools/smoke-desktop-keyboard-focus-ui.ps1` verifies Sources -> CloudDrive, RSS row Down, and Details -> Player in the real desktop window. | Covered for the Settings category menu, RSS subscription rows, and desktop route rail |
| Library poster-wall keyboard navigation | `DesktopLibraryPanels.kt` requests focus for the selected poster and handles directional keys plus `Enter`; `tools/smoke-desktop-local-source-ui.ps1` verifies poster-wall Right -> Enter against generated fixtures. | Covered for generated local poster-wall flow |
| Shared logic is not trapped in the desktop UI shell | `core:model` owns reusable display formatting, external subtitle-track parsing, settings source labels, Cloud/RSS status text including legacy English fallback, Cloud/RSS form validation, Episode ordering/season grouping, and the Detail continue-play target/label rule; `repository-api` owns media-index display helpers, poster grouping/titles/anime ids, same-anime merge keys, detail episode ordering/selection, metadata batch planning, `PlaybackPreferencesRepository`, `ScanPreferencesRepository`, and `buildNextPlaybackSource`, so Android TV `PlayerViewModel`/`SettingsViewModel` and Windows playback/settings now share playback-end preference access, scan auto-run/interval/last-run/same-anime merge preferences, plus the same next-episode lookup, ordering, resume-position, completed-progress reset, and playable-URI hook instead of duplicating that flow. `web-control-core` owns WebUI source request shaping/password redaction/add-test-update-remove persistence, source-test response wording, source-scan response mapping, shared Result error handling, local-directory DTO mapping, progress/detail DTO mapping, CloudDrive automation DTO mapping, CloudDrive/RSS request validation, config/login/token/run service glue, RSS subscription persistence flow, and response shaping, CloudDrive directory browse flow and DTO mapping over the shared directory-browser state, library DTO shaping, search filtering, play-request start-position/source payload mapping, and playback status DTO mapping and command normalization/default seek rules as well as shared NanoHTTPD routing/auth/DTOs, so Android TV and Windows WebUI use one server-info/IP response contract, one Local/WebDAV/SMB source request/add-test-update-remove persistence contract, one source-test success/error message contract, one source-scan response contract, one shared Result-to-WebUI-error contract, one local directory response contract, one progress/detail DTO response contract, one CloudDrive automation summary contract, one CloudDrive config/login/token/RSS subscription persistence/run-response contract, one CloudDrive directory browse/response contract, one all-anime de-dupe/sort/recent-window contract, one query/id/title/Chinese-title filter contract, one explicit-request-vs-resume-position playback start/source payload contract, and one playback status DTO contract and one command/default seek contract. `sync-engine-shared` now owns the CloudDrive directory-browser state/target/entry contract plus prepare/load/select helpers used by Android TV Settings, Android WebUI, Windows Cloud/RSS Settings, and Windows WebUI, and it now owns the shared `CloudDriveRssActionCoordinator` plus `CloudDriveRssAutomationRunner` port used by Android TV Settings, Windows Settings, and WebUI automation handlers for config save, credential save/clear, login, token verification, run-once, and subscription save/delete. `desktop-app` keeps compatibility wrappers and desktop-specific presenter logic in `Desktop*Presenters.kt`; the previous desktop-only Cloud/RSS display-forwarding layer has been removed. `DesktopPlaybackPresenters.kt` now owns command preview, runtime config validation, remote playback bridging, playback-source construction, `MpvProcessPlayer` creation, and launch status mapping instead of keeping that logic in `MiruPlayDesktopComposeApp.kt`. `DesktopSourceActivation.kt` now also owns the persisted-source open result for Local/WebDAV/SMB, including source factory selection, restored form state, ready status, and remote-root-open intent, so the Compose shell no longer repeats that activation wiring for each source type. `DesktopSourceScan.kt` now owns the shared scan-and-index path used by both manual source scans and Cloud/RSS linked-source rescans, keeping scanner execution, index rebuild, video filtering, and scan/rescan status shaping out of the Compose shell. Windows Settings now exposes the same scan preference controls as Android TV Settings, and the desktop poster wall/details episode shelf plus desktop WebUI library/detail responses respect the shared same-anime merge preference through those shared index presentation helpers. `DesktopWebControlPlaybackBridge.kt` now owns WebUI playback handler defaults, command-status text, source selection, and next-episode temporary-source inheritance, leaving Compose to mutate UI state and launch playback. The behavior is tested in shared modules and desktop presenter tests and can be reused by Android TV or future KMP surfaces. | Covered for the extracted helpers and mpv/source launch/config/scan/CloudDrive directory-browser/CloudDrive-RSS action coordinator/WebUI server-info/WebUI source requests/WebUI source persistence/WebUI source-test/WebUI source-scan/WebUI Result error/WebUI local-directory/WebUI progress/detail/WebUI CloudDrive-automation/WebUI CloudDrive/RSS requests/WebUI CloudDrive/RSS service glue/WebUI RSS-subscription-persistence/WebUI CloudDrive-directory-browse/WebUI playback-status/WebUI playback/WebUI-library/WebUI-search/WebUI-start-position/WebUI-playback-source/WebUI-status/WebUI-command/playback-preferences/scan-preferences/index-presentation/WebUI-index-presentation/episode-season/detail-continue/next-playback-source logic; more desktop UI state can still be split into shared use cases later |
| Windows playback uses mpv | `:player-mpv` builds mpv commands, starts an external process, supports IPC pause/seek/quit/time-position queries, and `desktop-app` launches `MpvProcessPlayer` through `DesktopPlaybackLauncher`; latest `:player-mpv:test :desktop-app:test` passed with 15 desktop tests including launch preparation, remote bridge preservation, runtime validation errors, and launch status/session output. | Covered for external process mode |
| Real mpv executable can launch | mpv_PlayKit `20260510` assets were downloaded into `.gradle/mpv-playkit-20260510`; `runtime/mpv` was prepared from `mpv-lazy-20260510.exe` plus the `mpv-lazy-20260510-vsNV.7z.001` overlay, then the default `:desktop-app:smokeMpvRuntime -PrequireMpvRuntime=true` gate passed with `mpv v0.41.0-615-g7b057f66f` and required RIFE `NVIDIA, DIRECTML`. | Covered |
| Bundled RIFE runtime is supported | Runtime layout expects `portable_config/vs/MEMC_RIFE_NV.vpy` and `MEMC_RIFE_DML.vpy` for the default release gate; app can also select the optional Standard script when present. The verifier blocks launch when a selected script is missing, and the runtime manifest is now checked against actual packaged relative files/directories instead of being trusted as display-only metadata. The current local manifest records the standard `.exe` base plus `vsNV` overlay and the default NVIDIA/DirectML requirements. | Covered structurally |
| Real RIFE payload works | DirectML RIFE smoke previously passed through `tools/smoke-mpv-rife.ps1 -Backend DIRECTML` with `runtime/mpv/mpv.exe`, `MEMC_RIFE_DML.vpy`, and a generated two-frame 1440x810 Y4M clip: mpv initialized VapourSynth and exited with playback success. The `-Backend ALL -AllowFailures` matrix mode reports all three backends in one run; `-ReportPath` can persist the same run plus host diagnostics and runtime manifest evidence as JSON. `tools/assert-mpv-rife-report.ps1 -RequireRuntimeManifest` can now turn that JSON into an explicit pass/fail assertion for required target backends and manifest evidence without rerunning playback. On this host, RIFE playback is treated as non-blocking because the machine is not expected to run interpolation well. | Covered structurally; target hardware validation remains |
| Runtime preparation is repeatable | `tools/prepare-mpv-runtime.ps1` accepts extracted directories or `.7z/.7z.001`, supports `-OverlaySource` for patching a base runtime with a RIFE/VapourSynth payload, optionally validates SHA256 before extraction including `filename=sha256` lists for split payloads, validates required RIFE scripts, copies to `runtime/mpv`, and writes `runtime-manifest.json`; tested with fake base/overlay payloads and with real mpv_PlayKit `20260510` standard + `vsNV` assets. | Covered |
| Desktop distribution runtime copy is controllable | `desktop-app/build.gradle.kts` bundles exactly one runtime source: explicit `-PmpvRuntimeSource` when present, otherwise repository `runtime/mpv`. `bundleMpvRuntime` defaults to `true` for self-contained artifacts, while `-PbundleMpvRuntime=false` skips the large runtime copy for UI-only development installs; verified with `:desktop-app:installDist -PbundleMpvRuntime=false` and `:desktop-app:smokePackagedMpvRuntime -PmpvRuntimeSource=runtime\mpv -PrequireMpvRuntime=true -PrequiredRifeBackends=NVIDIA,DIRECTML`, which builds `desktop-app.zip`, smokes `mpv.exe --version`, and checks the packaged runtime entries plus manifest-declared files/directories. | Covered |
| Runtime provenance is visible | `MpvRuntimeVerifier` reads `runtime-manifest.json`; `Check runtime` dialog shows localized source, verified time, required RIFE backends, files, and manifest-entry mismatch guidance when declared runtime evidence is missing or invalid. | Covered |
| Branch CI does not publish releases | `.github/workflows/ci.yml` keeps push validation enabled for `codex/**`, but the `nightly` job now only runs for scheduled or manual workflows on `refs/heads/main` or `refs/heads/master`; `build-release` remains restricted to main/master. Recent recorded `codex/windows-mpv-rife` `CI Build` run `26217656025` passed build/lint on commit `160f656`, with both publishing jobs skipped. | Covered |
| Local/WebDAV/SMB sources are available on desktop | `:media-source-desktop` implements local, WebDAV, and SMB sources. Compose Desktop exposes local source add/scan/search, saved-source switching, current-source index clearing/removal, WebDAV/SMB source open, directory browsing, current-source scanning, loopback bridge playback for remote media, and selected-media details for local index entries, remote browser entries, and recent playback records. `tools/smoke-desktop-local-source-ui.ps1` starts the Windows GUI with an isolated store, adds a local source from either a generated fixture or `-LibraryRoot`, scans it, validates the persisted source/index JSON, records scan/search/details/player screenshots, verifies poster-wall search can filter to a target anime, verifies poster click opens Details, and verifies Player handoff. `tools/smoke-desktop-webdav-source-ui.ps1` starts a loopback Basic Auth WebDAV fixture, adds the WebDAV source through the TV-style remote source card, verifies authorized PROPFIND/GET traffic, browses the remote directory, scans sibling NFO metadata into the desktop index, returns to the TV-style poster wall, opens Details from the remote poster, and verifies Player receives the remote media path. `tools/smoke-desktop-smb-source-ui.ps1` creates a timestamped fixture only under the approved SMB test directory, opens the authenticated SMB URL through the TV-style remote source card, scans one NFO-backed `Fixture SMB` video, verifies Details/Player handoff, deletes the fixture directory, defaults to the approved `ynsz` smoke credentials when env vars are absent, and redacts username/password/domain from the stored evidence. `DesktopSmbLiveShareTest` is opt-in and passed against `smb://smb.ynz.local/share/临时文件/测试` when provided live credentials. `DesktopRemotePlaybackSecurityTest` verifies WebDAV and SMB playback are converted to loopback bridge URLs before mpv command construction and that source host/user/password/domain fragments are absent from the command. | Covered for local/WebDAV/SMB GUI flows and remote command credential isolation |
| Desktop library indexing exists | `:scanner-desktop` scans desktop sources, filters videos, infers metadata, reads sibling `.nfo` and `tvshow.nfo`, and rebuilds `repository-desktop` index. The local-source GUI smoke validates that a generated NFO fixture appears in the desktop index as `Fixture Frieren` episode 2, verifies repository search can isolate that anime, and verifies poster-wall selection/detail/player handoff. The WebDAV GUI smoke validates that a remote NFO fixture appears as `Fixture WebDAV` episode 2 and that scanning returns the Library first screen to a poster wall. The SMB GUI smoke validates that a real authenticated SMB fixture appears as `Fixture SMB` episode 2 and that scanning returns to the poster wall. `tools/smoke-desktop-source-management-ui.ps1` proves saved-source keyboard switching, Clear index, and Remove source update the isolated desktop store: the active source index is cleared, the keyboard-selected source is removed, and the untouched second source remains. The local smoke also passes against the real anime library at `D:\Software\dufs`, with `build/desktop-local-source-ui/run-20260520-080904/local-source-scanned.png` showing the Library first screen as a poster wall. | Covered |
| Desktop media metadata can be inspected | Compose Desktop has a TV-style Details hero backed by selected index media plus a localized `媒体详情` panel backed by `DesktopMediaDetailRows`, showing active source, indexed title/type/anime/season/episode/title, Bangumi metadata source/id/title, indexed size/modified time, browser item kind/MIME/size/modified time/path, plot when present, and recent playback resume/play count/last watched. Unit tests cover the row model, hero title/subtitle helpers, Details chrome labels, episode-shelf subtitle, and Continue watching labels; the local-source GUI smoke now opens the Details route by selecting a scanned local poster. | Covered |
| Bangumi metadata is available on desktop | `:scraper-desktop` provides JVM Bangumi search/details/episodes; `:scraper-desktop:smokeBangumiLive` verifies the live search/details/episodes path and writes token-free JSON evidence; Compose Desktop has localized `使用当前条目`, `搜索`, `应用匹配`, and `清除元数据` controls to write or clear selected match source/id/title on one index entry. `tools/smoke-desktop-bangumi-metadata-ui.ps1` now proves the real Details screen flow with store assertions and screenshots for ready/search/applied/cleared states. | Covered for live scraper path and GUI single-item apply/clear flow |
| Batch metadata workflows | Compose Desktop has `Batch preview`, `Apply batch`, `Accept review`, and `Undo batch`; batch plans split matches into ready/review/conflict, render the preview as a selectable review queue, retain alternate Bangumi candidates per query, let the user switch the selected candidate before applying or manually accepting a reviewed match, skip existing-metadata conflicts instead of overwriting them, apply only high-confidence ready updates automatically, and persist the last rollback list in the desktop JSON store so undo survives restart. | Covered |
| Metadata batch planning is reusable | `repository-api/src/main/kotlin/com/miruplay/tv/repository/MetadataBatchPlanner.kt` owns query derivation, ready/review/conflict splitting, conflict isolation, preview text, and plan summaries; `desktop-app` delegates through thin compatibility wrappers. `:repository-api:test :desktop-app:test --rerun` passed after extraction. | Covered for planner logic |
| Index and subtitle display helpers are reusable | `repository-api/src/main/kotlin/com/miruplay/tv/repository/MediaIndexDisplay.kt` owns `MediaIndexEntry` display names/lines/browser conversion. `core/model/src/main/kotlin/com/miruplay/tv/model/SubtitleTracks.kt` owns external subtitle path parsing and format detection. Shared module tests cover both. | Covered for extracted helpers |
| Media-source display conventions are shared | `core:model/src/main/kotlin/com/miruplay/tv/model/MediaSourceDisplayConventions.kt` owns TV-facing source type labels, default source names, generic source names, source hints, location field labels, source display names, and connection status labels. Android TV `AddSourceScreen` now delegates source names/type labels/hints/location/status labels to those shared helpers, while Windows source picker/settings use the same type labels and generic fallback names. `MediaSourceDisplayConventionsTest`, `DesktopSourcePickerTest`, and `DesktopSettingsPanelTest` cover the shared contract and desktop usage. | Covered for source labels/defaults/status |
| Media-source connection fields are shared | `core:model/src/main/kotlin/com/miruplay/tv/model/MediaSourceInfoConventions.kt` owns source connection keys, local/WebDAV/SMB source construction, shared form connection-map creation, local display-name reading, credential helpers, and source location fallback. Android TV `AddSourceScreen` and `SettingsViewModel` now use those shared helpers instead of duplicating `url`/`path`/`uri`/`username`/`password`/`displayName` string handling, while desktop already consumes the same source-location and credential helpers. `MediaSourceInfoConventionsTest` covers the extracted contract. | Covered for shared source connection fields |
| Settings section contract is shared | `core:model/src/main/kotlin/com/miruplay/tv/model/SettingsSectionDisplayConventions.kt` owns the shared settings section enum plus Android TV and desktop orderings, section stepping helpers, WebUI summary/status tiles, and settings page copy. Android TV `AddSourceScreen` and Windows `DesktopCloudRssPanel` now read the same section contract and shared titles/descriptions instead of carrying separate settings enums; Windows includes the same first WebUI category and starts Settings there, while its status text now reflects that the JVM listener can manage media sources and remote playback when enabled. `SettingsSectionDisplayConventionsTest` and `DesktopSettingsPanelTest` cover the shared contract and platform-specific navigation order. | Covered for settings menu copy/order; broader WebControl live/device QA remains partial |
| Desktop WebUI access state | `repository-desktop` provides `FileBackedWebControlAccessManager`, implementing the shared `WebControlAccessManager` interface with persisted enable state, URL-safe token generation/rotation, and enabled-change listeners in the desktop JSON store. `core:common` owns `findWebControlLocalIps()` and `buildWebControlAccessUrls()`, and Android TV `SettingsViewModel` reuses the same URL builder instead of keeping its own `NetworkInterface`/token-encoding logic. `:web-control-core` now owns shared NanoHTTPD routing, auth/cookie handling, API envelopes/DTOs, static serving, request-encoding helpers, server-info/IP DTO mapping, source request shaping/password redaction/add-test-update-remove persistence, source-test response wording, source-scan response mapping, shared Result error handling, local-directory DTO mapping, progress/detail DTO mapping, CloudDrive automation DTO mapping, CloudDrive/RSS request validation, config/login/token/run service glue, RSS subscription persistence flow, and response shaping, CloudDrive directory browse flow and DTO mapping over the shared directory-browser state, library DTO shaping, library search filtering, WebUI play-request start-position/source payload mapping, and WebUI playback status DTO mapping and command normalization/default seek rules, so Android `WebControlServer` is a thin asset-backed wrapper and Windows `DesktopWebControlServer` reuses the same HTTP surface with classpath WebUI assets. Compose Desktop Settings starts/stops that server from the shared enable state; the desktop service currently serves token-protected info, source list, local directory browsing, library search/detail, CloudDrive config summary, playback status, playback start/control, static WebUI bootstrap, basic source/RSS/config writes, real local/WebDAV/SMB source connection tests with shared user-facing response text, source scans through the shared `scanAndIndexDesktopSource` helper used by manual desktop scans and Cloud/RSS linked rescans, CloudDrive login/token verification through the shared directory-browser prepare/load flow plus `DesktopCloudDriveRssAutomationEngine`, scoped CloudDrive directory browsing through the shared directory-browser helper, and WebUI-triggered CloudDrive/RSS manual runs. WebUI library/detail grouping now delegates to shared media-index poster groups and honors the persisted same-anime merge preference, matching the Windows poster wall behavior for externally matched seasons. Playback requests are injected by Compose Desktop through `DesktopWebControlPlaybackBridge.kt` into `DesktopPlaybackLauncher` and `desktopWebControlPlaybackCommand`, preserving episode ids for progress while retaining real media paths and remote source lifetimes for mpv loopback streaming. `DesktopWebControlPlaybackBridgeTest` covers WebUI command status text, source selection/reuse/ownership, and next-episode temporary-source inheritance. `DesktopWebControlServerTest` covers auth, static cookie, disabled state, source redaction, repository-backed library/detail data, shared poster merge preference for WebUI library/detail, single-source WebUI scan, scan-all WebUI scan, post-scan library visibility, CloudDrive login/token persistence, directory scoping/filtering, run-once RSS submission through the shared desktop engine, and injected WebUI playback play/command handling. The latest focused WebUI gate `:web-control-core:test :web-control:compileDebugKotlin :desktop-app:test checkDesktopPresenterSeparation checkDesktopComposeOnly -PbundleMpvRuntime=false` passed in Gradle MCP build `b-123`, and Android debug assemble passed in `b-124`. | Partial |
| Playback progress continues on desktop | Compose Desktop saves progress when mpv launches, polls mpv `time-pos` every 10 seconds while playback is active, saves a session-estimated position immediately on Stop so process shutdown is not blocked by IPC, shows continue-watching records, can clear a selected recent item, provides TV-style Play/Pause/-10s/+30s/Stop controls in the playback stage with left/right focus movement, Enter activation, and Up/Down movement to/from `返回详情`, adds unit-covered movement from playback-setting fields through toggles/backend into the runtime card and back, adds unit-covered vertical movement through runtime path fields and `检查运行时`, drives native desktop fullscreen from the Player `全屏` toggle while preserving route-based restore behavior, and stores original remote path rather than loopback URL. Android TV and Windows now both resolve Play-next targets through `repository-api` `buildNextPlaybackSource`; `NextPlaybackSourceResolverTest` covers shared ordering, resume, completed-progress reset, null handling, and platform-specific playable URI mapping. `tools/smoke-desktop-mpv-launch-ui.ps1` now proves the GUI can launch a generated local sample through mpv, exercise Pause/-10s/+30s/Stop, confirm the mpv process exits, and record `mpv-settings-focus.png`, `mpv-runtime-focus.png`, `mpv-launch-ready.png`, `mpv-launched.png`, `mpv-keyboard-controls-used.png`, and `mpv-stopped.png` evidence with a 30s saved position. | Covered |
| True mpv position tracking | `MpvIpcClient` can request `get_property time-pos`, `MpvProcessPlayer.queryTimePositionMs()` exposes it, and Compose Desktop uses `syncPlaybackProgressFromMpv` to re-anchor the session and persist observed mpv positions during playback and at stop. Unit tests cover session re-anchoring and sync helper success/null/error behavior. | Covered for Compose Desktop |
| Remote playback byte-range handling is shared | `core:model` owns `StreamRange`, HTTP `Range` header formatting, byte-range parsing/resolution, `Content-Range` formatting, and response planning. Desktop WebDAV uses `StreamRange.toHttpRangeHeader()` for upstream ranged reads, and `DesktopPlaybackBridge` uses `HttpByteRangeRequest`/`HttpStreamResponsePlan` for mpv loopback streaming so WebDAV/SMB credential isolation does not duplicate HTTP byte-range string rules. Shared tests cover case/whitespace-tolerant `Range` parsing, malformed numeric range rejection, constructor invariants, suffix/open-ended ranges, reversed-range invalid responses without metadata, and response plans; the desktop bridge test proves malformed `Range: bytes=abc-5` is served as a full stream instead of being mistaken for a suffix range. Verified with `:core:model:test --tests com.miruplay.tv.model.HttpByteRangeTest`, `:media-source-desktop:test --tests com.miruplay.tv.mediasource.desktop.DesktopPlaybackBridgeTest`, `:core:model:test :media-source-desktop:test`, and `:desktop-app:test`. | Covered for shared/desktop loopback playback |
| Cloud/RSS sync parity on desktop | `repository-desktop` persists `CloudDriveAutomationConfig`, RSS subscriptions, processed items, download tasks, and file-backed CloudDrive/Bangumi credentials in the JSON store. `:cloud-drive-desktop` provides a JVM CloudDrive2 gRPC client, `:sync-engine-desktop` provides a JVM RSS runner plus scheduler with feed fetch, filtering, processed-item dedupe, CloudDrive offline submission, torrent-to-magnet staging, organizer moves, `lastRunAt` updates, `runIfDue`, scheduler state flow, and a desktop post-sync source rescan hook. `core:model` now owns `CloudDriveRssRunSummary` plus the TV-facing Cloud/RSS status wording, so Android TV Settings, the Windows Cloud/RSS panel, desktop scheduler display, and smokes share the same run summary and Chinese status contract; the desktop UI keeps a compatibility adapter for older English status inputs. `:sync-engine-shared` owns `CloudDriveRssActionCoordinator`, so Android TV Settings, Windows Settings, and WebUI now reuse the same action contract for CloudDrive config save, token/password save/clear, login, API-token verification, RSS run-once, and subscription save/delete while each platform keeps only UI state changes, scheduler integration, and Windows post-run source rescan. Compose Desktop now exposes CloudDrive2/RSS config, token/password save/clear, `Login`, `Verify token`, inbox/library `选择目录`, subscription add/update/delete, `Run sync now`, `Start scheduler`, and `Stop scheduler` in TV-style overview/config/subscription/scheduler cards with compact endpoint/path/subscription previews, keyboard-moving credential/sync/RSS edit fields, path picker actions, action rows, enable/proxy/RSS toggles, and RSS subscription rows. The Android TV and Windows CloudDrive path pickers now share `sync-engine-shared`'s directory-browser state plus prepare/load/select helpers, so token verification, token-root scoping, outside-path clamping, visible-directory filtering, loading state, listing-error propagation, and selection normalization no longer diverge between platforms. The desktop UI still wraps that shared browser state with Windows-specific paging/focus behavior: the directory browser action row moves horizontally across `使用当前目录`/`返回上级`/`关闭`, `Down` enters the first visible folder row or focusable loading/empty row, and first-row/empty-row `Up` returns to `使用当前目录` without wrapping. `CloudDriveDirectoryBrowserTest` in `:sync-engine-shared` covers token-root scoping, outside-path clamping before list calls, visible-directory filtering, listing-error propagation, and selection normalization; `CloudDriveRssActionCoordinatorTest` covers shared action config normalization, credential save/clear, runner delegation, and subscription save/delete; desktop presenter tests cover directory action-row movement, directory row/empty-state movement, and path picker focus bridges. Post-sync source rescan now goes through `rescanCloudRssLinkedSource`, a reusable desktop helper that delegates to the same `scanAndIndexDesktopSource` path as manual scans, reports whether Library or remote status should be updated, and returns the refreshed video entries for active-source UI state. `DesktopScanIndexIntegrationTest` creates a local fixture source, writes an old indexed episode, changes the fixture contents, runs the Cloud/RSS linked-source rescan helper, and verifies the repository index is replaced with the new episode. The latest shared Cloud/RSS gate `:sync-engine-shared:test :sync-engine:compileDebugKotlin :web-control-core:test :web-control:compileDebugKotlin :ui-tv:compileDebugKotlin :desktop-app:test checkDesktopPresenterSeparation checkDesktopComposeOnly -PbundleMpvRuntime=false` passed in Gradle MCP build `b-129`, and Android debug assemble passed in `b-130`. `:cloud-drive-desktop:test` starts a real loopback gRPC server against the generated CloudDrive2 stub and covers login, API token info, bearer auth listing, raw-token fallback, token-free live-smoke report JSON, and live-smoke listing summaries. `:sync-engine-desktop:test` now also runs `DesktopCloudDriveRssAutomationEngine` against a loopback CloudDrive2 gRPC server through the real `GrpcCloudDriveClient`, covering RSS offline submission, bearer metadata, processed item persistence, download-task persistence, and organizer list calls. `:cloud-drive-desktop:smokeCloudDrive2` is available for live endpoint/token/path QA without printing the token and can write `-PcloudDriveReportPath=...` token-free JSON evidence; `tools/assert-cloud-drive-report.ps1` validates generatedAtUtc, endpoint/path, permission booleans, listing counts, and preview items, and `tools/verify-windows-port.ps1 -CloudDrive` runs the smoke plus report assertion behind explicit endpoint/token parameters. `:sync-engine-desktop:smokeCloudDriveRssDryRun` verifies a real endpoint, token, inbox/library listing, RSS fetch/parse, filter matching, and would-submit counts without calling CloudDrive offline download APIs; `-PcloudDriveRssReportPath=...` writes a token-free JSON dry-run evidence report with redacted RSS URL evidence plus scheme/host/SHA-256 fingerprints. `:sync-engine-desktop:smokeCloudDriveRssLiveSubmit` is now available as a separate explicit-confirmation task that submits a limited number of live RSS candidates and records submit counts plus post-submit inbox listing in a token-free JSON report. Both desktop RSS smoke tasks can also run the real organizer when `cloudDriveRssOrganize=true` plus an explicit move confirmation string and records moved count plus post-organize folder counts in the token-free JSON report. RSS preview items no longer persist raw GUIDs or submission URLs; they store booleans plus SHA-256/redacted URL evidence, and the console output uses the same redacted form so private RSS passkeys are not leaked into saved QA logs. `tools/assert-cloud-rss-report.ps1` validates dry-run/live-submit/organize reports for inbox/library paths, RSS/candidate counts, submission type totals, token permissions, submit/organize evidence, preview entries, redacted URL evidence, and token/passkey redaction while rejecting raw `guid` or `submissionUrl` fields; `:sync-engine-desktop:smokeCloudDriveRssScheduler` drives the desktop scheduler loop over real elapsed time with a local due-runner, verifies first start/duplicate start, observed checks, due-run summary, stop state, and writes a token-free scheduler report; `tools/assert-cloud-rss-scheduler-report.ps1` validates scheduler timing/state/summary evidence; `tools/verify-windows-port.ps1` and CI now run the scheduler smoke by default, while `-SkipCloudRssScheduler` only skips it for temporary local troubleshooting. Live submit/organize QA still needs to be executed against a real server before completion can be claimed. | Partial |
| Desktop UI maturity | Compose Desktop is now the default entry and follows the TV visual language for local library/source management, remote browser, saved-source switching, media details, Bangumi metadata, recents, playback, runtime, and settings/automation slices. The Library route now uses a TV-style full-width header with Scan/Settings actions and poster-wall primary surface as the first content after scan/load; source management uses a focused saved-source card with long-path compaction, TV-facing status copy, unit-covered saved-source key movement, unit-covered local field/action movement, and unit-covered remote source field/action movement; remote source setup uses WebDAV/SMB cards and a separate browser panel with keyboard row and parent navigation; Cloud/RSS settings uses TV-style overview/config/subscription/scheduler cards with keyboard-moving credential/sync/RSS edit fields, action rows, toggle rows, and RSS subscription rows; Details opens from poster selection and starts with a TV-style hero with stat pills plus an indexed episode shelf before the metadata/Bangumi panels; Player hides the desktop rail and starts with a TV-style playback stage before advanced settings, whose media/start/subtitle fields, toggle/backend row, and runtime form/check path now have unit-covered TV-style focus movement across card boundaries, and the Player fullscreen setting now controls the native desktop window fullscreen mode only on the Player route with restore-on-exit behavior; Settings opens with a TV-style category rail, concrete source/playback/scan/metadata status cards with keyboard-moving quick-action rows, and Cloud/RSS cards. Screenshot QA is scripted via `tools/capture-desktop-ui.ps1` and passes with palette, text, size, diversity, and per-section distinctness checks. Latest Android TV fixture QA generated `build/android-tv-qa/run-20260520-190519/android-tv-library.png`, `android-tv-library-dpad-poster.png`, `android-tv-details.png`, `android-tv-details-episode-focus.png`, `android-tv-player.png`, `android-tv-library-return.png`, `android-tv-settings.png`, `android-tv-settings-sources.png`, `android-tv-settings-source-card-focus.png`, `android-tv-settings-source-edit.png`, `android-tv-settings-source-delete-focus.png`, `android-tv-settings-source-deleted.png`, `android-tv-settings-playback.png`, `android-tv-settings-cloud-drive.png`, `android-tv-settings-scan.png`, `android-tv-settings-metadata.png`, XML dumps, and `android-tv-smoke-report.json`; latest keyboard GUI smoke generated `build/desktop-keyboard-focus-ui/run-20260521-175654/keyboard-settings-sources.png`, `keyboard-settings-cloud.png`, `keyboard-settings-rss-first.png`, `keyboard-settings-rss-second.png`, `keyboard-nav-details.png`, and `keyboard-nav-player.png`; latest generated local-source smoke generated `build/desktop-local-source-ui/run-20260520-162054/local-source-poster-keyboard.png`, `local-source-details.png`, `local-source-details-episodes.png`, `local-source-details-episode-back-to-hero.png`, `local-source-details-episode-selected.png`, `local-source-details-episode-to-bangumi.png`, `local-source-details-bangumi-back-to-episode.png`, and `local-source-player.png`; latest Bangumi metadata smoke generated `build/desktop-bangumi-metadata-ui/run-20260520-213906/bangumi-details-ready.png`, `bangumi-focus-bangumi.png`, `bangumi-search-results.png`, `bangumi-metadata-applied.png`, and `bangumi-metadata-cleared.png`; latest source-management GUI smoke generated `build/desktop-source-management-ui/run-20260520-212601/source-management-saved-source-keyboard.png`, `source-management-scanned.png`, `source-management-controls.png`, `source-management-cleared.png`, and `source-management-removed.png`, with `source-management-controls.png` showing `扫描完成：1 个视频，2 个目录。`; latest WebDAV GUI smoke generated `build/desktop-webdav-source-ui/run-20260520-121024/webdav-source-keyboard-up.png`, `webdav-source-keyboard-browse.png`, `webdav-source-keyboard-select.png`, `webdav-source-poster-wall.png`, `webdav-source-details.png`, and `webdav-source-player.png`; latest SMB GUI smoke generated `build/desktop-smb-source-ui/run-20260520-090324/smb-source-poster-wall.png`, `smb-source-details.png`, and `smb-source-player.png`; latest real-library local-source smoke generated `build/desktop-local-source-ui/run-20260520-080904/local-source-scanned.png`, `local-source-details.png`, and `local-source-player.png` against `D:\Software\dufs`; latest mpv GUI launch smoke generated `build/desktop-mpv-launch-ui/run-20260521-123909/mpv-settings-focus.png`, `mpv-runtime-focus.png`, `mpv-launch-ready.png`, `mpv-launched.png`, `mpv-keyboard-controls-used.png`, `mpv-stopped.png`, and `mpv-recent-keyboard-selected.png`; latest Settings screenshot QA refreshed `build/desktop-ui-qa/settings.png` and `settings-cloud.png`. | Partial |

Additional current UI evidence: `tools/smoke-desktop-keyboard-focus-ui.ps1`
captured `build/desktop-keyboard-focus-ui/run-20260521-175654/keyboard-settings-sources.png`,
`keyboard-settings-cloud.png`, `keyboard-settings-rss-first.png`,
`keyboard-settings-rss-second.png`, `keyboard-settings-scheduler-started.png`,
`keyboard-settings-scheduler-stopped.png`, `keyboard-nav-details.png`,
`keyboard-nav-player.png`, `keyboard-back-details.png`, and
`keyboard-back-library.png`, proving the real Windows GUI can move the Settings
category selection, Cloud/RSS RSS subscription row selection, Cloud/RSS
scheduler Start/Stop controls, desktop route rail, and TV Back-equivalent route
chain by keyboard input.
The same smoke now writes its isolated desktop JSON store as UTF-8 without BOM,
so both Windows PowerShell and PowerShell 7 launches preload the Cloud/RSS
fixture state before screenshot assertions.
Settings summary quick-action rows now return to the selected Settings category
when pressing keyboard/DPAD `Up`, so the concrete source/playback/scan/metadata
cards no longer trap focus inside their action row.
Media-detail rows now page in six-item TV-style windows, keep two-column
`Left`/`Right` movement inside each visible page, and preserve `Up`/`Down`
movement across the full detail set instead of pushing later detail rows off the
bottom of the Details page.
The Cloud/RSS subscription list now pages saved RSS rows in six-item TV-style
windows, keeps restored selected rows visible, and preserves `Up`/`Down`
movement across the full saved subscription list into the scheduler controls.
The CloudDrive directory picker now pages folder rows in six-item TV-style
windows: `Down` from row 6 moves to row 7 on the next page, `Up` moves back
across the page boundary, and the picker shows a Chinese range summary so
large CloudDrive folders are not silently truncated.
The Android TV and Windows CloudDrive directory pickers now share
`CloudDriveDirectoryBrowserCoordinator` for validation, open/load orchestration,
stale-result filtering, listing-error propagation, and browsing status; the
desktop layer keeps only Windows paging/focus behavior around that shared
contract. The focused shared picker gate `:sync-engine-shared:test
:ui-tv:compileDebugKotlin :desktop-app:test checkDesktopPresenterSeparation
checkDesktopComposeOnly -PbundleMpvRuntime=false` passed in Gradle MCP build
`b-131`, and Android debug assemble `:app:assembleDebug
-PbundleMpvRuntime=false` passed in `b-132`.
CloudDrive login/API-token validation and status mapping now also flow through
`CloudDriveActionResult`, and the focused shared credential-action gate
`:sync-engine-shared:test :ui-tv:compileDebugKotlin :desktop-app:test
checkDesktopPresenterSeparation checkDesktopComposeOnly
-PbundleMpvRuntime=false` passed in Gradle MCP build `b-136`; Android debug
assemble `:app:assembleDebug -PbundleMpvRuntime=false` passed in `b-137`.
RSS subscription save/delete and manual Cloud/RSS run status mapping now flow
through `RssSubscriptionActionResult` and `CloudDriveRunActionResult`; the
focused shared subscription/run-action gate `:sync-engine-shared:test
:ui-tv:compileDebugKotlin :desktop-app:test checkDesktopPresenterSeparation
checkDesktopComposeOnly -PbundleMpvRuntime=false` passed in Gradle MCP build
`b-138`, and Android debug assemble `:app:assembleDebug
-PbundleMpvRuntime=false` passed in `b-139`.
Cloud/RSS config save and credential save/clear status mapping now also flow
through `CloudDriveConfigActionResult` and `CloudDriveCredentialActionResult`,
keeping Android TV and Windows on the same normalization/status contract while
the UIs keep only field updates. The focused shared config/credential gate
`:sync-engine-shared:test :ui-tv:compileDebugKotlin :desktop-app:test
checkDesktopPresenterSeparation checkDesktopComposeOnly
-PbundleMpvRuntime=false` passed in Gradle MCP build `b-140`, and Android
debug assemble `:app:assembleDebug -PbundleMpvRuntime=false` passed in `b-141`.
Bangumi token save/clear now goes through `BangumiCredentialActionCoordinator`
in `:sync-engine-shared`, so Android TV and Windows share token persistence,
blank-input, clear, and status-result behavior. The focused shared Bangumi
credential gate `:sync-engine-shared:test :ui-tv:compileDebugKotlin
:desktop-app:test checkDesktopPresenterSeparation checkDesktopComposeOnly
-PbundleMpvRuntime=false` passed in Gradle MCP build `b-142`, and Android debug
assemble `:app:assembleDebug -PbundleMpvRuntime=false` passed in `b-143`.
WebUI enable/disable, token rotation, access-token snapshot, and URL rebuilds
now flow through `WebControlAccessActionCoordinator` in `:repository-api`;
Android TV and Windows share the access-state contract while Windows keeps only
its JVM listener start/stop hook. The focused shared WebUI access gate
`:repository-api:test :ui-tv:compileDebugKotlin :desktop-app:test
checkDesktopPresenterSeparation checkDesktopComposeOnly
-PbundleMpvRuntime=false` passed in Gradle MCP build `b-144`, and Android debug
assemble `:app:assembleDebug -PbundleMpvRuntime=false` passed in `b-145`.
The generated local-source GUI smoke also captured
`build/desktop-local-source-ui/run-20260520-162054/local-source-poster-keyboard.png`,
proving the Library poster wall can move selection with keyboard input before
opening Details, show the Details hero stat pills, then send `Down` from the Details hero into
`local-source-details-episodes.png`, send `Up` back to the hero in
`local-source-details-episode-back-to-hero.png`, re-enter the shelf, move to another episode with
`local-source-details-episode-selected.png`, send `Down` to Bangumi in
`local-source-details-episode-to-bangumi.png`, return with
`local-source-details-bangumi-back-to-episode.png`, and reach Player without mouse
input.
The source-management GUI smoke captured
`build/desktop-source-management-ui/run-20260520-212601/source-management-saved-source-keyboard.png`
and `source-management-controls.png`, proving the real Windows saved-source card
can switch between saved local sources by keyboard input before scanning the
selected source and that the scan status renders as
`扫描完成：1 个视频，2 个目录。`.
The source-management label/status pass also localizes the local scan/search
controls, WebDAV/SMB controls, saved-source type labels, remote-browser
title/status, source-management status messages, scan/search results, and
remote file-kind chips to TV-facing Chinese copy with unit coverage.
The WebDAV GUI smoke captured
`build/desktop-webdav-source-ui/run-20260520-121024/webdav-source-keyboard-up.png`,
`webdav-source-keyboard-browse.png`, and `webdav-source-keyboard-select.png`,
proving remote-browser parent navigation, row movement, and Enter selection by
keyboard input without touching the SMB share.
The remote WebDAV/SMB browser now pages file rows in eight-item TV-style
windows: `Down` from row 8 moves to row 9 on the next page, `Up` moves back
across the page boundary, and the browser shows a Chinese range summary so
large remote folders are not silently truncated.
The Continue watching empty state is now also focusable, with `Up` returning to
the `刷新`/`清除条目` action row and `Down` continuing to the next Details panel.
Continue watching records now page in six-row TV-style windows: `Down` from
record 6 moves to record 7 on the next page, `Up` moves back across the page
boundary, and the panel shows a Chinese range summary so long playback history
lists are not silently truncated.
The mpv GUI smoke captured
`build/desktop-mpv-launch-ui/run-20260521-123909/mpv-settings-focus.png`,
`mpv-runtime-focus.png`, `mpv-keyboard-controls-used.png`, and
`mpv-recent-keyboard-selected.png`, proving playback-setting/runtime focus
movement before launch, Player transport movement while mpv was running, and
Continue watching selection from Details after playback stopped.
`DesktopBangumiNavigationTest` now covers Bangumi batch-match, candidate,
search-result, and empty-result row movement without depending on the live
Bangumi service.
Bangumi metadata match lists now page their fixed-height rows in TV-style
windows too: batch matches and candidates page in four-row windows, search
results page in six-row windows, `Down`/`Up` can cross those page boundaries,
and each section shows a Chinese range summary so longer preview/search lists
are not silently truncated.
`:scraper-desktop:smokeBangumiLive` now covers the live service path for
search, subject details, and regular episode listing while writing token-free
JSON evidence.
The Bangumi metadata GUI smoke captured
`build/desktop-bangumi-metadata-ui/run-20260521-221838/bangumi-details-ready.png`,
`bangumi-focus-bangumi.png`, `bangumi-search-results.png`,
`bangumi-metadata-applied.png`, and `bangumi-metadata-cleared.png`, proving the
real Windows Details panel can move from the episode shelf to Bangumi controls
by keyboard, enter the Bangumi result list from the action grid, return to
Apply match, clear metadata, and verify the isolated desktop store is applied
then cleared.
This covers the previous Details-to-Bangumi action-grid focus gap; broader
multi-panel traversal can still be expanded separately.

Latest shared-input evidence: `ui-design/src/main/kotlin/com/miruplay/tv/design/MiruPlayInputIntent.kt`
now owns the cross-platform activation, Back/navigation-back, directional, and
media playback input semantics. Android TV maps Compose `Key` values through
`ui-tv/src/main/kotlin/com/miruplay/tv/ui/components/TvKeyEvents.kt`, and
Windows maps them through
`desktop-app/src/main/kotlin/com/miruplay/tv/desktop/DesktopKeyEvents.kt`, so
the two platforms share one intent contract while keeping platform-specific key
adapters thin. The Android TV fullscreen player and Windows desktop playback
stage now both dispatch activation and media playback controls through that
shared intent layer. The shared layer also owns direction-to-delta helpers for
horizontal, vertical, and linear focus movement; the Windows route rail and
saved-source picker now consume intent-based navigation handlers so those
high-reuse controls no longer carry raw Compose `Key` branching beyond the
platform adapter; the Library header, local-source fields/action row,
remote-source fields/action row, and remote-browser rows/actions/empty state now
also expose intent-level navigation contracts. The Settings category menu also
steps sections from the shared intent layer instead of branching on desktop key
values. The Library
poster wall, highest-heat/recent shelves, and search row now use the same
intent-level navigation path, reducing raw desktop `Key` branching on the
media-first surface. The Details hero action row, episode-season selector,
episode rows, Continue watching action/record rows, episode/recents empty
states, and two-column media-details list now also expose intent-level focus
contracts. The Windows Player stage transport,
playback-setting form/toggle row, playback-end action row, and runtime form now
also use intent-level navigation contracts, matching the shared playback-input
layer used by Android TV fullscreen playback. Cloud/RSS credential/path/RSS
fields, action rows, toggles, subscription rows, CloudDrive directory browser,
RSS picker, and Settings quick-action rows now also use intent-level navigation
contracts while retaining thin Compose `Key` adapters. The Bangumi action grid,
batch-match/candidate/search-result lists, list exits, and empty-results bridge
now also use those intent-level navigation helpers, so the metadata panel keeps
raw Compose `Key` handling at the adapter boundary. Verified with
`.\gradlew.bat :ui-design:test :ui-tv:test :desktop-app:test checkDesktopPresenterSeparation checkDesktopComposeOnly -PbundleMpvRuntime=false`
on 2026-05-24.

## Latest Verification Commands

```powershell
.\tools\verify-windows-port.ps1

# Uses JDK 21 automatically when available; set JAVA21_HOME or JDK21_HOME to override discovery.

# Optional live/device/runtime gates:
.\tools\verify-windows-port.ps1 -Gui
.\tools\verify-windows-port.ps1 -RealLibrary -RealLibraryRoot 'D:\Software\dufs'
.\tools\verify-windows-port.ps1 -AndroidTv -AndroidDeviceId <android-tv-device-id>
.\tools\verify-windows-port.ps1 -Smb
.\tools\verify-windows-port.ps1 -MpvRuntime -PackagedMpvRuntime
.\tools\verify-windows-port.ps1 -Rife -RifeBackend ALL -AllowRifeFailures

# The -Smb switch is restricted to \\smb.ynz.local\share\临时文件\测试.
# Do not scan or modify unrelated files in that SMB share.

.\tools\prepare-mpv-runtime.ps1 `
  -Source .\.gradle\mpv-playkit-20260510\mpv-lazy-20260510.exe `
  -OverlaySource .\.gradle\mpv-playkit-20260510\mpv-lazy-20260510-vsNV.7z.001 `
  -Destination .\runtime\mpv `
  -RequiredRifeBackends 'NVIDIA,DIRECTML' `
  -Force

.\gradlew.bat :desktop-app:smokeMpvRuntime `
  -PrequireMpvRuntime=true

.\gradlew.bat :desktop-app:installDist -PbundleMpvRuntime=false

.\gradlew.bat :desktop-app:smokeMpvRuntime `
  -PmpvRuntimeSource=runtime\mpv `
  -PrequiredRifeBackends=NVIDIA,DIRECTML

.\gradlew.bat :desktop-app:smokePackagedMpvRuntime `
  -PmpvRuntimeSource=runtime\mpv `
  -PrequireMpvRuntime=true `
  -PrequiredRifeBackends=NVIDIA,DIRECTML

.\gradlew.bat :desktop-app:smokeWindowsInstaller `
  -PmpvRuntimeSource=runtime\mpv `
  -PrequireMpvRuntime=true `
  -PrequiredRifeBackends=NVIDIA,DIRECTML `
  -PrequireWindowsInstallerToolchain=true

.\gradlew.bat :repository-desktop:test :desktop-app:test :data:compileDebugKotlin :scanner:test

.\gradlew.bat :scraper-desktop:test :scraper-desktop:smokeBangumiLive `
  -PbangumiSmokeReportPath=build\bangumi-smoke\live-report.json

.\gradlew.bat :sync-engine-desktop:test :repository-desktop:test :player-mpv:test :desktop-app:test :desktop-app:installDist :app:assembleDebug

.\gradlew.bat checkDesktopComposeOnly `
  checkDesktopPresenterSeparation `
  checkUiPaletteDrift `
  :core:model:test `
  :repository-api:test `
  :cloud-drive-api:test `
  :sync-engine-shared:test `
  :media-source-desktop:test `
  :scanner-desktop:test `
  :repository-desktop:test `
  :scraper-desktop:test `
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
  -PcloudDrivePath=/Downloads `
  -PcloudDriveReportPath=build/cloud-drive-smoke/cloud-drive-report.json

.\tools\assert-cloud-drive-report.ps1 `
  -ReportPath .\build\cloud-drive-smoke\cloud-drive-report.json `
  -RequiredPath /Downloads `
  -RequireOfflinePermission

.\tools\verify-windows-port.ps1 `
  -CloudDrive `
  -CloudDriveEndpoint http://127.0.0.1:19798 `
  -CloudDriveToken <token> `
  -CloudDrivePath /Downloads `
  -RequireCloudDriveOfflinePermission

.\gradlew.bat :sync-engine-desktop:smokeCloudDriveRssDryRun `
  -PcloudDriveEndpoint=http://127.0.0.1:19798 `
  -PcloudDriveToken=<token> `
  -PcloudDriveRssUrl=https://example.test/rss.xml `
  -PcloudDriveInbox=/Downloads `
  -PcloudDriveLibrary=/Library `
  -PcloudDriveRssFilter=Episode `
  -PcloudDriveRssReportPath=build/cloud-rss-smoke/report.json

.\tools\assert-cloud-rss-report.ps1 `
  -ReportPath .\build\cloud-rss-smoke\report.json `
  -RequiredInbox /Downloads `
  -RequiredLibrary /Library `
  -RequireCandidates

.\gradlew.bat :sync-engine-desktop:smokeCloudDriveRssDryRun `
  -PcloudDriveEndpoint=http://127.0.0.1:19798 `
  -PcloudDriveToken=<token> `
  -PcloudDriveRssUrl=https://example.test/rss.xml `
  -PcloudDriveInbox=/Downloads `
  -PcloudDriveLibrary=/Library `
  -PcloudDriveRssFilter=Episode `
  -PcloudDriveRssOrganize=true `
  -PcloudDriveRssOrganizeConfirmation=I_UNDERSTAND_THIS_MOVES_REAL_CLOUDDRIVE_FILES `
  -PcloudDriveRssReportPath=build/cloud-rss-smoke/organize-report.json

.\tools\assert-cloud-rss-report.ps1 `
  -ReportPath .\build\cloud-rss-smoke\organize-report.json `
  -RequiredInbox /Downloads `
  -RequiredLibrary /Library `
  -RequireOrganize

.\gradlew.bat :sync-engine-desktop:smokeCloudDriveRssScheduler `
  -PcloudDriveRssSchedulerDurationMs=2000 `
  -PcloudDriveRssSchedulerCheckIntervalMs=250 `
  -PcloudDriveRssSchedulerRunAfterChecks=2 `
  -PcloudDriveRssSchedulerReportPath=build/cloud-rss-smoke/scheduler-report.json

.\tools\assert-cloud-rss-scheduler-report.ps1 `
  -ReportPath .\build\cloud-rss-smoke\scheduler-report.json `
  -MinRunCount 1 `
  -MinChecksObserved 2

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

.\tools\assert-cloud-rss-report.ps1 `
  -ReportPath .\build\cloud-rss-smoke\live-submit-report.json `
  -RequiredInbox /Downloads `
  -RequiredLibrary /Library `
  -RequireCandidates `
  -RequireLiveSubmit `
  -RequireOfflinePermission

.\gradlew.bat :sync-engine-desktop:test

.\gradlew.bat :player-mpv:test --rerun

.\gradlew.bat :app:assembleDebug

.\gradlew.bat :desktop-app:test

.\gradlew.bat checkDesktopComposeOnly checkUiPaletteDrift :desktop-app:installDist -PbundleMpvRuntime=false

.\tools\smoke-desktop-source-management-ui.ps1

.\tools\capture-desktop-ui.ps1

.\tools\smoke-desktop-keyboard-focus-ui.ps1

.\gradlew.bat :cloud-drive-desktop:test :sync-engine-desktop:test :desktop-app:test

.\tools\smoke-desktop-webdav-source-ui.ps1

.\tools\smoke-desktop-smb-source-ui.ps1

.\gradlew.bat :app:assembleDebug

.\tools\smoke-android-tv-ui.ps1 -DeviceId <android-tv-device-id>

.\tools\assert-android-tv-smoke-report.ps1 `
  -ReportPath .\build\android-tv-qa\run-20260520-190519\android-tv-smoke-report.json `
  -RequiredDeviceId <android-tv-device-id>

.\gradlew.bat checkDesktopComposeOnly

.\gradlew.bat :ui-tv:compileDebugKotlin :desktop-app:test --rerun

.\gradlew.bat checkUiPaletteDrift

.\tools\capture-desktop-ui.ps1

.\tools\smoke-desktop-local-source-ui.ps1

.\tools\smoke-desktop-local-source-ui.ps1 -LibraryRoot 'D:\Software\dufs'

.\tools\smoke-desktop-smb-source-ui.ps1

.\tools\smoke-android-tv-ui.ps1

.\tools\assert-android-tv-smoke-report.ps1 `
  -ReportPath .\build\android-tv-qa\run-YYYYMMDD-HHMMSS\android-tv-smoke-report.json `
  -RequiredDeviceId <android-tv-device-id>

.\gradlew.bat checkDesktopPresenterSeparation `
  checkDesktopComposeOnly `
  checkUiPaletteDrift `
  :player-mpv:test `
  :desktop-app:test `
  :app:assembleDebug

.\gradlew.bat :desktop-app:installDist -PbundleMpvRuntime=false

.\tools\smoke-desktop-local-source-ui.ps1 -LibraryRoot 'D:\Software\dufs'

adb -s <android-tv-device-id> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <android-tv-device-id> shell am start -n com.miruplay.tv/.MainActivity --es test_local_path /sdcard/Movies/MiruPlayTvFixture-20260519134617
adb -s <android-tv-device-id> exec-out screencap -p > build\android-tv-qa\library-fixture-20260519-rife-optional.png

.\tools\smoke-desktop-source-management-ui.ps1

.\tools\smoke-desktop-webdav-source-ui.ps1

.\tools\smoke-desktop-smb-source-ui.ps1

.\tools\smoke-desktop-mpv-launch-ui.ps1

.\tools\smoke-desktop-bangumi-metadata-ui.ps1

adb connect <android-tv-device-id>
adb shell monkey -p com.miruplay.tv -c android.intent.category.LAUNCHER 1
adb exec-out screencap -p > build\android-tv-qa\library-baseline-20260519.png

adb -s <android-tv-device-id> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <android-tv-device-id> shell am start -n com.miruplay.tv/.MainActivity --es test_local_path /sdcard/Movies/MiruPlayTvFixture-20260519134617
adb -s <android-tv-device-id> exec-out screencap -p > build\android-tv-qa\library-fixture-20260519134617.png

.\tools\smoke-mpv-rife.ps1 -Backend DIRECTML `
  -ReportPath .\build\mpv-smoke\rife-directml-report.json

.\tools\smoke-mpv-rife.ps1 -Backend ALL -AllowFailures `
  -ReportPath .\build\mpv-smoke\rife-matrix-report.json

.\tools\assert-mpv-rife-report.ps1 `
  -ReportPath .\build\mpv-smoke\rife-matrix-report.json `
  -RequiredBackends NVIDIA,DIRECTML `
  -RequireRuntimeManifest `
  -AllowFailures
```

## Highest-Risk Remaining Work

1. Continue narrowing TV parity gaps in less-traveled keyboard/DPAD focus paths outside the now-covered Android TV Settings category/page traversal, Settings summary quick-action rows, source-management local/remote fields plus actions and remote editor/browser focus bridge, Cloud/RSS credential/sync/RSS edit fields plus path picker/action/toggle/subscription/scheduler rows, desktop route rail, Library header action row, Library poster wall/highest-heat/recent shelves/search row/source bridge, Details hero actions, Details hero-to-episodes/recents/Bangumi/media-details fallback plus episode-shelf season selector, remote browser list including parent navigation, Player stage/settings/runtime focus bridge, saved-source card movement, and Bangumi metadata action grid/apply-clear flow.
2. Validate RIFE on target Windows hardware that is expected to support
   interpolation, and decide whether the optional Standard backend should ship
   an additional `rife` plugin.
3. Run live CloudDrive2 end-to-end QA for real offline submission, torrent
   staging, and organization.
   Token/path/RSS parsing can now be checked first with the dry-run smoke task.
