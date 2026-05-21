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
status copy. CloudDrive2 credential fields, sync-path fields, and RSS edit
fields now move through their form rows and bridge into adjacent actions or
toggles; RSS subscription rows and the empty subscription state now support
`Up`/`Down` key movement. The Settings category
menu and desktop route rail now support `Up`/`Down` key navigation with TV-list edge stops and
TV-facing `探索`/`详情`/`播放`/`设置` route copy,
and the Library poster wall supports directional movement plus `Enter` to open
Details. The Library empty media state is focusable, with source controls moving
`Down` into it and `Up` returning to the media source form. The Library highest-heat and recently-added poster shelves, desktop switches/toggles, and RIFE backend picker now also
support horizontal `Left`/`Right` focus movement where applicable plus shared `Enter`/numpad Enter/DPAD Center activation. Compose Desktop now treats `Esc`, TV remote `Back`, and navigation back/out keys as Back equivalents for Player -> Details -> Library and Settings -> Library while leaving `Backspace` to text fields. The remote browser list now supports `Up`/`Down` row movement, first-row
`Up` parent navigation, `Enter` without opening directories during focus
movement, and a focusable empty state that returns to the `上级` action with `Up`.
Settings summary quick-action rows now also have TV-style horizontal
focus movement with row-edge stops. Real Windows GUI
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
inputs. `tools/verify-windows-port.ps1` now wraps the local port gate with
safe defaults: it selects JDK 21 when available, Gradle/JVM/desktop install,
Cloud/RSS scheduler elapsed-time smoke, and Android debug build run by default,
while GUI smokes, the real `D:\Software\dufs` library, Android TV emulator
smoke, SMB share smoke, mpv runtime payload checks, native app-image packaging,
live CloudDrive/RSS checks, and RIFE target hardware checks require explicit
switches. CI now also runs the base Android/desktop/shared JVM gate plus the
local Cloud/RSS scheduler smoke on `codex/**` push branches, so the active port
branch is checked automatically after pushes; nightly and release artifact jobs
remain guarded to main/master scheduled or manual runs.

## Prompt-to-Artifact Completion Matrix

| Objective deliverable | Concrete artifacts inspected | Verification evidence | Remaining gap |
|---|---|---|---|
| Keep the original Android TV app working | `app/src/main/kotlin/com/miruplay/tv/MainActivity.kt`, `ui-tv/`, Android Gradle modules, `tools/smoke-android-tv-ui.ps1` | `:app:assembleDebug` passed in the latest preservation check. `tools/smoke-android-tv-ui.ps1` installed the debug APK on emulator `10.137.32.118:5555`, generated seven playable MP4/NFO fixture shows, pushed them under `/sdcard/Movies`, launched with `test_local_path`, clicked scan, verified Library content requests poster focus, used DPAD Right/Left plus Center to open a fixture poster, used DPAD Down from Details Play to focus the first episode row, used DPAD Center on that focused episode row to open Player, verified Android Back returns from Player to Details and from Details to the poster-focused Library wall, used DPAD Up/Right/Center from that returned poster wall to open Settings, used DPAD Down/Center inside Settings to open the media-source panel, verified the auto-added local source and source form, verified DPAD Right focuses the auto-added source card, Left returns to the media-source menu, Center opens the edit-source form without losing card focus, Right focuses the source delete button, Center removes the source while keeping the media-source empty state visible, then continues DPAD Down through Playback, CloudDrive, Scan, and Metadata settings with matching menu focus, and captured `build/android-tv-qa/run-20260520-190519/android-tv-library.png`, `android-tv-library-dpad-poster.png`, `android-tv-details.png`, `android-tv-details-episode-focus.png`, `android-tv-player.png`, `android-tv-library-return.png`, `android-tv-settings.png`, `android-tv-settings-sources.png`, `android-tv-settings-source-card-focus.png`, `android-tv-settings-source-edit.png`, `android-tv-settings-source-delete-focus.png`, `android-tv-settings-source-deleted.png`, `android-tv-settings-playback.png`, `android-tv-settings-cloud-drive.png`, `android-tv-settings-scan.png`, `android-tv-settings-metadata.png`, XML dumps, and `android-tv-smoke-report.json`. | Broader TV-device regression still needs manual coverage for less-traveled focus paths. |
| Add a Windows desktop entry | `desktop-app/build.gradle.kts`, `MiruPlayDesktopComposeApp.kt` | `mainClass` points to `com.miruplay.tv.desktop.MiruPlayDesktopComposeAppKt`; the native window title now uses the TV-facing `MiruPlay 桌面版` copy; the app-image gate launches `MiruPlay.exe --miruplay-desktop-smoke` and validates a JSON report for entry point, window title, start section, and bundled runtime paths; the new installer gate can build MSI/EXE artifacts from that verified app image when WiX is available and records SHA256/size/signing evidence; `:desktop-app:test`, lightweight `:desktop-app:installDist -PbundleMpvRuntime=false`, full `:desktop-app:smokePackagedMpvRuntime -PmpvRuntimeSource=runtime\mpv -PrequireMpvRuntime=true -PrequiredRifeBackends=NVIDIA,DIRECTML`, and native `:desktop-app:smokeNativeAppImageRuntime -PmpvRuntimeSource=runtime\mpv -PrequireMpvRuntime=true -PrequiredRifeBackends=NVIDIA,DIRECTML` pass. | Signed release installer still needs release signing inputs and toolchain evidence. |
| Use Compose-family UI on both targets | Android remains Compose/Compose TV; desktop default entry is Compose Multiplatform Desktop | `desktop-app` applies Compose plugins, `application.mainClass` points at `MiruPlayDesktopComposeAppKt`, screenshot QA launches the Compose entry, the old Swing shell has been removed from production sources, and root `checkDesktopComposeOnly` fails if Swing UI imports/classes or `coroutines-swing` return. | Covered structurally; full Android-TV-vs-desktop screen parity is tracked separately. |
| Preserve media management capabilities on desktop | `media-source-desktop`, `scanner-desktop`, `repository-api`, `repository-desktop`, `scraper-desktop`, `sync-engine-desktop`, `cloud-drive-desktop`, Compose Library/Details/Settings panels | Unit and integration coverage now includes shared display/subtitle/index/batch planning helpers, desktop details rows, playback progress, RSS scheduler, CloudDrive gRPC client, RSS offline submission through real `GrpcCloudDriveClient`, and remote playback command security that keeps WebDAV/SMB hosts and credentials out of mpv command lines. | Real CloudDrive2 server live QA remains open for token validation, offline submission, torrent staging, and organization. |
| Use mpv as Windows playback backend | `player-mpv`, `MpvProcessPlayer`, `MpvIpcClient`, Compose Player panel, `tools/smoke-desktop-mpv-launch-ui.ps1` | `:player-mpv:test` passes; desktop app launches `MpvProcessPlayer`; progress sync polls mpv `time-pos` while playing; RIFE is opt-in by default on desktop so low-capability hosts can launch plain mpv first; missing mpv/RIFE launch failures now render TV-facing Chinese guidance for checking the runtime, preparing a backend, or turning RIFE off, and the `Check runtime` verifier output localizes runtime readiness, manifest, source, required backend, and missing-file details. The Player `全屏` toggle still feeds mpv `--fs`, and now also drives Compose Desktop's native `WindowPlacement.Fullscreen` only while the Player route is active, restoring the previous floating/maximized placement when leaving Player or disabling fullscreen; `DesktopSectionContractTest` covers the route-gated decision. The GUI mpv launch smoke generates a local Y4M sample, fills it into the Windows Player, disables RIFE for host-safe playback, clicks Play, confirms an `mpv.exe` child process containing the sample path, exercises Pause, -10s, +30s, and Stop through keyboard focus, verifies a persisted progress record, and captures settings-focus/runtime-focus/launched/keyboard-control/stopped Player screenshots. `MpvProcessPlayer.stop()` escalates from IPC quit to destroy and force-destroy, including descendant process cleanup when available. | External-process mpv mode is covered; embedded libmpv is intentionally deferred. |
| Plan and integrate a bundled RIFE-capable mpv runtime | `runtime/mpv`, `tools/prepare-mpv-runtime.ps1`, `desktop-app:verifyMpvRuntimePayload`, `smokeMpvRuntime`, `smokePackagedMpvRuntime`, `smokeNativeAppImageRuntime`, `packageWindowsAppImage`, `tools/smoke-mpv-rife.ps1`, `tools/assert-mpv-rife-report.ps1`, `docs/mpv-runtime-packaging.md` | Runtime verifier and Gradle smoke pass for NVIDIA/DIRECTML scripts; `MpvRuntimeVerifier` now cross-checks `runtime-manifest.json` declared files/directories and required RIFE backends, rejects unknown backend names plus absolute/`..` manifest entries as incomplete evidence, and the desktop runtime panel localizes that guidance. Gradle runtime gates now use the same manifest-evidence checks for source runtime payloads, packaged zip entries, and native app-image runtime content. `:desktop-app:smokePackagedMpvRuntime -PmpvRuntimeSource=runtime\mpv -PrequireMpvRuntime=true -PrequiredRifeBackends=NVIDIA,DIRECTML` builds `desktop-app.zip`, launches the packaging runtime with `mpv.exe --version`, and verifies the bundled zip contains `runtime/mpv/mpv.exe`, `runtime-manifest.json`, the NVIDIA/DIRECTML scripts, and manifest-declared entries. `:desktop-app:smokeNativeAppImageRuntime -PmpvRuntimeSource=runtime\mpv -PrequireMpvRuntime=true -PrequiredRifeBackends=NVIDIA,DIRECTML` builds the Windows `jpackage` app image, verifies `MiruPlay.exe`, validates `app/MiruPlay.cfg` main class/classpath entries, checks the app-image `runtime/mpv` payload and manifest-declared entries, and launches the app-image entry in headless smoke mode to verify the resolved runtime paths before installer/signing work. Prior DirectML VapourSynth/RIFE smoke proved the payload path; `-Backend ALL -AllowFailures` reports the backend matrix. `tools/smoke-mpv-rife.ps1 -ReportPath ...` writes a JSON evidence bundle with mpv version, host OS/CPU/GPU diagnostics, backend statuses, exit codes, log paths, and runtime manifest evidence for target-host QA. `tools/assert-mpv-rife-report.ps1 -RequireRuntimeManifest` validates those JSON reports for host diagnostics, clip shape, backend entries, required backend PASS status, and a present/problem-free manifest that declares the required backend(s); `tools/verify-windows-port.ps1 -Rife` now runs that stricter validation immediately after generating the report. | Local RIFE playback is non-blocking on this host; backend performance/compatibility must be validated on target hardware. |
| Make Windows UI visually match the TV UI | `ui-design`, `ui-tv/.../theme/Theme.kt`, `MiruPlayDesktopComposeApp.kt`, root `checkUiPaletteDrift`, `tools/capture-desktop-ui.ps1`, `tools/smoke-android-tv-ui.ps1`, `build/desktop-ui-qa/*.png`, `build/android-tv-qa/run-20260520-190519/*.png` | TV and desktop now derive the MiruPlay red/dark/blue/text/card palette from the same `MiruPlayPalette` constants. `checkUiPaletteDrift` fails if TV or desktop UI reintroduces raw shared palette literals outside `:ui-design`. Android TV smoke captures the media-first Library, poster-focused DPAD traversal, Details hero, focused Details episode row, full-screen Player overlay, Android Back return to the poster-focused Library wall, DPAD entry into Settings, DPAD entry into the Settings media-source panel, source-card Right/Left focus return, source-card Center edit form, source-card Right delete-button focus, delete-button Center empty-source state, and continued menu-category traversal through Playback, CloudDrive, Scan, and Metadata against a playable fixture. Windows Library uses the TV-style full-width `探索` header with the Android TV action pair `扫描`/`设置`, TV empty state, and a 6-column poster-wall first screen after scanning or loading a saved index; the desktop route chrome now also uses `探索`/`详情`/`播放`/`设置` labels and the native window title now uses `MiruPlay 桌面版` instead of English desktop labels. The Library header action row now has unit-covered TV-style Left/Right movement plus Down focus bridging into the media/source content, and the first content row can move Up back to the header action row while preserving the last focused header action. Highest-heat/recent rows plus search/source controls sit below the wall, and those horizontal rows now share the same Enter activation and unit-covered left/right plus wall-to-shelf/search/source Up/Down movement as TV-style poster shelves. Source management now shows the active saved source as a two-line TV-style focus card with visible source name/type, TV-facing labels and status messages, middle-compacted local/WebDAV/SMB paths, local source fields and action buttons with unit-covered row movement, and unit-covered plus real-window directional saved-source movement; the saved-source picker now owns source-switch keys instead of relying on a panel-wide key handler. WebDAV and SMB setup now render as separate TV-style remote source cards with compact endpoint previews beside a remote-browser panel that also compacts long paths; their editor fields plus open/scan actions now have unit-covered focus movement from WebDAV fields/actions down into the SMB field/action rows, Right-to-browser exits from the rightmost editor fields/actions, and browser-row/browser-up Left returns to the last editor target while keeping first-row Up as parent navigation. Cloud/RSS settings now render as TV-style overview, CloudDrive2, sync-path, subscription, and scheduler cards with localized Chinese labels, previews, empty states, common status copy, unit-covered credential/sync/RSS edit field movement into adjacent actions/toggles, and unit-covered action-grid movement across credential, login/verify, scan-source, sync, RSS, subscription, and scheduler controls; RSS subscription rows now handle `Up`/`Down` selection; `tools/capture-desktop-ui.ps1` now captures both `settings.png` and `settings-cloud.png` and asserts visual quality/distinctness. The latest Settings keyboard smoke writes its isolated JSON store as UTF-8 without BOM for Windows PowerShell compatibility, preloaded two RSS subscriptions, selected the first visible row, sent `Down`, and captured `build/desktop-keyboard-focus-ui/run-20260521-175654/keyboard-settings-cloud.png`, `keyboard-settings-rss-first.png`, `keyboard-settings-rss-second.png`, `keyboard-settings-scheduler-started.png`, and `keyboard-settings-scheduler-stopped.png` to prove localized fixture loading, row navigation, and scheduler action activation. The latest source-management GUI smoke preloaded two deep local fixture sources, used keyboard `Up` on the saved-source card to switch from Season 02 back to Season 01, verified the subsequent scan indexed the keyboard-selected source id, and captured `build/desktop-source-management-ui/run-20260520-212601/source-management-saved-source-keyboard.png`, `source-management-scanned.png`, `source-management-controls.png`, `source-management-cleared.png`, and `source-management-removed.png`; `source-management-controls.png` shows the scan status rendered as `扫描完成：1 个视频，2 个目录。`. The latest WebDAV GUI smoke captured `build/desktop-webdav-source-ui/run-20260520-121024/webdav-source-opened.png`, `webdav-source-browsed.png`, `webdav-source-keyboard-up.png`, `webdav-source-keyboard-browse.png`, `webdav-source-keyboard-select.png`, `webdav-source-poster-wall.png`, `webdav-source-details.png`, and `webdav-source-player.png` with the remote card layout plus keyboard parent/row navigation. The latest SMB GUI smoke captured `build/desktop-smb-source-ui/run-20260520-090324/smb-source-opened.png`, `smb-source-poster-wall.png`, `smb-source-details.png`, and `smb-source-player.png` against the approved `临时文件\测试` directory with redacted credentials. The latest generated local-source smoke captured `build/desktop-local-source-ui/run-20260520-162054/local-source-poster-keyboard.png`, `local-source-details.png`, `local-source-details-episodes.png`, `local-source-details-episode-back-to-hero.png`, `local-source-details-episode-selected.png`, `local-source-details-episode-to-bangumi.png`, `local-source-details-bangumi-back-to-episode.png`, and `local-source-player.png`, proving poster-wall Right -> Enter keyboard selection before Details, Details hero stat pills, Details hero Down to the TV-style episode shelf, episode shelf Up back to the hero, episode-row Down to Bangumi, Bangumi Up back to the episode shelf, and episode-row Enter Player handoff. The latest Bangumi metadata GUI smoke captured `build/desktop-bangumi-metadata-ui/run-20260520-213906/bangumi-focus-bangumi.png`, `bangumi-search-results.png`, `bangumi-metadata-applied.png`, and `bangumi-metadata-cleared.png`, proving the Bangumi action grid can enter the search-result list, return to Apply match, and clear metadata by keyboard. The latest real-library smoke against `D:\Software\dufs` captured `build/desktop-local-source-ui/run-20260520-080904/local-source-scanned.png`, which indexed 22 videos and opens directly on the poster wall. Poster selection routes directly to Details, Details starts with a TV-style hero plus indexed episode shelf, Player opens without the desktop rail into a TV-like playback stage with top return, centered transport controls, bottom timeline/status chips, localized mpv status chips, and TV-facing `媒体 URI 或文件路径`/`起播秒数`/`外挂字幕路径` settings labels below the stage. Screenshot QA covers Library/Details/Player/Settings/Cloud settings and asserts window size, dark palette, red accent, readable light text, visual diversity, and per-section distinctness. | Android TV Settings category/page traversal, Settings summary quick-action rows, Cloud/RSS credential/sync/RSS edit fields, action grids, toggles, and subscription rows, route rail, Library header action parity/focus bridge, poster-wall, poster shelves, search row, and source bridge movement, source-management local/remote fields/actions, remote editor/browser focus bridge, and saved-source card, Details hero actions/stat pills, Details episode shelf boundary exits, Continue watching recents, remote-browser list, Player transport, and Bangumi metadata-list/action grid are now covered by GUI or unit tests; remaining less-traveled keyboard/DPAD paths still need more TV-parity work. |
| Settings and route rail keyboard/DPAD navigation | `DesktopCloudRssPanel.kt`, `DesktopNavigation.kt`, `MiruPlayDesktopComposeApp.kt`, `DesktopKeyEvents.kt`, `tools/smoke-desktop-keyboard-focus-ui.ps1`, `build/desktop-keyboard-focus-ui/run-20260521-175654/*.png` | The Settings category menu, Settings summary quick-action rows, Cloud/RSS credential/sync/RSS edit fields, Cloud/RSS action grids, Cloud/RSS RSS subscription rows, the empty RSS subscription state, and desktop route rail now keep focus on the selected row/control and handle directional keys with TV-style edge stops instead of wraparound; the desktop root also handles `Esc`, TV remote `Back`, and navigation back/out keys as Back equivalents from Player to Details and from Details/Settings to Library without swallowing `Backspace` text editing. TV action buttons, desktop selectable rows, saved-source picker, Settings category rows, route-rail rows, and custom desktop activation handlers now share keyboard Enter, numpad Enter, and TV DPAD Center confirm semantics across saved-source cards, poster cards, remote rows, Bangumi rows, Player round buttons, common TV-style buttons/cards, and the Settings section menu. Navigation-only field/action focus bridges now also share one KeyDown gate across Library/source/search, remote-source, Cloud/RSS, Details, Bangumi, route-rail, Player stage-return, playback-setting, and runtime rows; remaining explicit desktop key-down branching is centralized in the shared key helpers or the root Back handler, and desktop selectable rows expose the same confirm-or-navigation fallback so Cloud/RSS subscription rows, CloudDrive directory rows, Library empty states, remote-browser rows, Bangumi match/result rows, Details episode rows, Continue watching rows, and media-detail rows do not need separate stacked key handlers for Up/Down movement. `DesktopSettingsPanelTest` covers Settings category edge stops, Settings category row confirm semantics, Settings summary quick-action row movement, Cloud/RSS credential/sync/RSS edit field movement into adjacent actions/toggles, Cloud/RSS action-grid movement, RSS-to-scheduler focus bridging, RSS subscription edge stops, empty-subscription entry/exit, and null-selection entry, while `DesktopSectionContractTest` covers route rail edge stops, accepted Back key aliases, Backspace exclusion, and the route Back hierarchy; `DesktopChromeTest` covers shared confirm-key semantics including DPAD Center, disabled-control behavior, and selectable-row confirm/navigation fallback; `DesktopSourcePickerTest` covers saved-source picker confirm/navigation key semantics through the same helper; `DesktopBangumiNavigationTest` covers Bangumi list movement through the same row surface; `DesktopDetailHeroTest` covers Details episode, recent, and media-detail focus targets through the same row surface. The Windows GUI smoke opens Settings, sends two `Down` key presses from Sources to CloudDrive, selects a preloaded RSS row, sends `Down` to move from Beta to Alpha, then focuses Details and sends `Down` to Player, captures `keyboard-nav-details.png` and `keyboard-nav-player.png`, sends `Esc` back to Details and `Esc` back to Library, captures `keyboard-back-details.png` and `keyboard-back-library.png`, and asserts the content region changed for these key paths. | Remaining less-traveled keyboard/DPAD paths outside these menus still need more TV-parity work. |
| Library poster-wall keyboard/DPAD navigation | `DesktopLibraryPanels.kt`, `DesktopPosterGroupingTest.kt`, `DesktopSourcePickerTest.kt`, `DesktopSectionContractTest.kt`, `tools/smoke-desktop-local-source-ui.ps1`, `build/desktop-local-source-ui/run-20260520-162054/*.png` | The poster wall now keeps focus on the selected poster, moves selection with directional keys, clamps Down navigation to the nearest poster when the next row is short, exits Up from the top row to the Library header action row, and opens Details on `Enter`; the Library header action row moves between `扫描` and `设置` with Left/Right, moves Down into Library content, and preserves the last focused header action when content returns upward. The Library media area now also moves downward from the poster-wall bottom row into `最高热度` or directly into `最近添加` when no featured row exists, moves between the featured and recent shelves with `Up`/`Down`, exits into the search row, and the search row moves horizontally between field/action while bridging `Up` back to media and `Down` into the source panel. `DesktopPosterGroupingTest` covers poster-wall short-row movement, top-row Up exit, shelf horizontal/vertical movement, media-to-search exit, and search row movement; `DesktopSectionContractTest` covers the header action row; `DesktopSourcePickerTest` covers the source panel `Up` bridge back to the search row. The generated local-source GUI smoke scans two fixture shows, sends `Right`, captures `local-source-poster-keyboard.png` with the second poster selected, sends `Enter`, and verifies Details and Player handoff. | Broader GUI traversal across header/search/source bridges can still be expanded later. |
| Details hero keyboard/DPAD navigation | `DesktopDetailsPanels.kt`, `DesktopDetailHeroTest.kt`, `DesktopBangumiNavigationTest.kt`, `tools/smoke-desktop-local-source-ui.ps1`, `build/desktop-local-source-ui/run-20260520-162054/*.png` | The Details hero now requests focus on the primary Play action, moves between Play and Back to poster wall with `Left`/`Right`, and activates the focused action with `Enter`; it also mirrors the Android TV `DetailStats` style with indexed episode/season/metadata stat pills. The episode shelf now exits upward through the multi-season selector when multiple seasons exist, otherwise back to the hero, and exits downward to Bangumi; the season selector moves with `Left`/`Right`, `Down` returns to the selected episode row, and `Up` returns to the hero. When no related episode rows exist, the shelf exposes a focusable TV empty state that returns to the hero with `Up` and continues to the next Details panel with `Down`. Continue watching still moves downward into Bangumi, Bangumi bottom actions and lists can now move downward into media details, media-detail rows are focusable in two TV-style columns, and an empty media-detail panel still exposes a focusable TV empty state with `Up` returning to Bangumi. Bangumi top action `Up` returns focus to the episode shelf. The generated local-source GUI smoke opens Details from the poster wall, captures `local-source-details.png`, sends `Down`, captures `local-source-details-episodes.png`, sends `Up`, captures `local-source-details-episode-back-to-hero.png`, re-enters the shelf, moves to Frieren episode 2, captures `local-source-details-episode-selected.png`, sends `Down`, captures `local-source-details-episode-to-bangumi.png`, sends `Up`, captures `local-source-details-bangumi-back-to-episode.png`, then presses `Enter` and verifies Player receives Frieren episode 2. | Deeper Details GUI traversal can still be expanded later. |
| Continue watching keyboard/DPAD navigation | `DesktopDetailsPanels.kt`, `MiruPlayDesktopComposeApp.kt`, `DesktopDetailHeroTest.kt`, `tools/smoke-desktop-mpv-launch-ui.ps1`, `build/desktop-mpv-launch-ui/run-20260521-123909/*.png` | The Details hero now moves focus down into Continue watching when recent records exist; recent rows move with `Up`/`Down`, activate with `Enter`, and first-row `Up` returns to the `刷新`/`清除条目` action pair. Those actions now move horizontally with `Left`/`Right`, move back into the first recent row with `Down`, and exit upward to the previous Details panel with `Up`; when no records exist, action `Down` exits to the next Details panel. The mpv GUI smoke launches a generated Y4M sample, stops playback, returns to Details, sends `Down` then `Enter` on the recent row, captures `mpv-recent-keyboard-selected.png`, and verifies the same sample progress remains persisted with play count and saved position. | Broader GUI traversal across the action pair can still be expanded later. |
| Remote browser keyboard/DPAD navigation | `DesktopLibraryPanels.kt`, `DesktopSourcePickerTest.kt`, `tools/smoke-desktop-webdav-source-ui.ps1`, `build/desktop-webdav-source-ui/run-20260520-121024/*.png` | The remote browser now keeps focus on the selected row, moves with `Up`/`Down` without opening directories, opens/selects with `Enter`, uses first-row `Up` to navigate to the parent/root path, and uses `Left` to return focus to the last remote editor field/action. When a remote directory is empty, the browser also exposes a focusable TV empty state: `Down` from `上级` lands on the empty row, `Up` returns to `上级`, and `Left` returns to the editor side. The remote editor side also exits into the browser with `Right` from rightmost WebDAV/SMB fields and actions. `DesktopSourcePickerTest` covers editor-to-browser focus targets, browser-row return-to-editor targets, row movement, empty-state entry/exit, and preservation of first-row parent navigation. The loopback WebDAV GUI smoke opens the fixture directory, sends `Up`, captures `webdav-source-keyboard-up.png` at the root, sends `Enter` back into the fixture directory, sends `Down`, captures `webdav-source-keyboard-browse.png`, sends `Enter`, captures `webdav-source-keyboard-select.png`, and then scans/opens Details/Player from the same remote source. | Deeper multi-level remote browsing can still be expanded later. |
| Player transport keyboard/DPAD navigation | `DesktopPlaybackPanels.kt`, `tools/smoke-desktop-mpv-launch-ui.ps1`, `build/desktop-mpv-launch-ui/run-20260521-123909/*.png` | The Player stage now keeps focus on the primary transport, moves across active controls with `Left`/`Right`, activates with `Enter`, moves `Up` from any active transport control to `返回详情`, moves `Down` from `返回详情` back to the primary transport, and moves `Down` from transport into the playback settings. Playback settings now use explicit TV-style movement from media path to start seconds, down to subtitle, down to fullscreen/keep-open/RIFE/backend toggles, then down into the runtime card; runtime `Up` returns to the backend toggle and the runtime form still moves down through `mpv.exe`, `portable_config`, and `检查运行时`. The mpv GUI smoke captures `mpv-settings-focus.png` and `mpv-runtime-focus.png`, launches a generated Y4M sample, sends `Enter`, `Left+Enter`, `Right+Right+Enter`, and `Right+Enter` to pause, seek, and stop, then verifies mpv exited and progress persisted; `DesktopPlaybackPanelTest` covers the Player-stage Up/Down focus topology, settings field/toggle/runtime bridging, runtime return-to-settings, and left/right edge stops. | Full-screen mpv/window-manager specific key paths can still be expanded later. |
| Bangumi metadata-list keyboard/DPAD navigation | `DesktopBangumiPanel.kt`, `DesktopBangumiNavigationTest.kt`, `tools/smoke-desktop-bangumi-metadata-ui.ps1`, `build/desktop-bangumi-metadata-ui/run-20260520-213906/*.png` | Bangumi batch matches, candidate review rows, search results, and action buttons now share deterministic key handling: `Up`/`Down` moves through visible result rows, `Right` enters candidate review or enters the first visible match list from the action grid, `Left` returns to batch matches or exits search results back to Apply match, action buttons form a two-column grid, and `Enter` selects the focused row/button. The Bangumi panel now also localizes action labels, section labels, empty states, batch chips, candidate labels, and repository status strings to TV-facing Chinese copy at the desktop display layer. `:desktop-app:test` covers row ordering, horizontal candidate review entry/exit, visible-row clamping, edge stops, action-grid movement, action-to-list handoff, list-to-action handoff, top-action `Up` exit, and the localized Bangumi display text. `:scraper-desktop:smokeBangumiLive` now verifies live Bangumi search/details/episodes and writes token-free JSON evidence. The Windows GUI smoke opens Details, uses keyboard `Use selected`, `Search`, result-list handoff, `Apply match`, and `Clear metadata`, verifies Bangumi source/id/title are persisted then cleared, and captures `bangumi-details-ready.png`, `bangumi-focus-bangumi.png`, `bangumi-search-results.png`, `bangumi-metadata-applied.png`, and `bangumi-metadata-cleared.png`; `bangumi-focus-bangumi.png` shows the localized `Bangumi 元数据`, `使用当前条目`, and `当前索引` controls in the real window. | Broader live-service regression can be repeated with the smoke tasks as needed. |
| Provide auditable verification gates | Gradle MCP build records, `.github/workflows/ci.yml`, scripts under `tools/`, this audit document | CI now runs the Android debug build plus desktop/shared JVM checks on mainline branches and `codex/**` push branches: `checkDesktopComposeOnly`, `checkDesktopPresenterSeparation`, `checkUiPaletteDrift`, `:core:model:test`, `:repository-api:test`, `:cloud-drive-api:test`, `:sync-engine-shared:test`, `:media-source-desktop:test`, `:scanner-desktop:test`, `:repository-desktop:test`, `:scraper-desktop:test`, `:player-mpv:test`, `:cloud-drive-desktop:test`, `:sync-engine-desktop:test`, `:sync-engine-desktop:smokeCloudDriveRssScheduler`, `:desktop-app:test`, and lightweight `:desktop-app:installDist -PbundleMpvRuntime=false`. Nightly publishing is now gated to scheduled or manual main/master workflows, so `codex/**` pushes validate without creating release artifacts. Recent recorded `codex/windows-mpv-rife` `CI Build` run `26217656025` passed build and lint on commit `160f656`, with `nightly` and `build-release` skipped. `tools/verify-windows-port.ps1` now auto-selects JDK 21 when launched from a newer JDK shell and the safe default gate includes the token-free Cloud/RSS scheduler elapsed-time smoke. Latest local commands are listed below with passing evidence for Android build, desktop tests, mpv tests, CloudDrive loopback tests, scheduler smoke, runtime smoke, packaged runtime zip smoke, DirectML RIFE smoke, and screenshot QA. | Hardware/cloud/live-service checks are intentionally tracked as not achieved. |

Completion decision: do not mark complete. The project has a usable Windows
Compose Desktop port and preserved Android debug build evidence, but the
objective requires real-world confidence across bundled RIFE backends and full
CloudDrive2 behavior. Those still depend on target GPU/driver/plugin stacks and
a live CloudDrive2 environment.

## Checklist

| Requirement | Current evidence | Status |
|---|---|---|
| Android TV remains buildable | `.\gradlew.bat :app:assembleDebug` passed in the latest preservation check after the desktop port work. `.\tools\smoke-android-tv-ui.ps1` also installed and launched the debug APK on emulator `10.137.32.118:5555`, recording `build/android-tv-qa/run-20260520-190519`. | Covered for debug build and current device smoke |
| Android TV uses Compose TV | Existing Android app remains `MainActivity` + Compose navigation and `ui-tv` Compose/TV screens. | Covered structurally |
| Windows desktop entry exists | `:desktop-app` JVM application now points `mainClass` at `com.miruplay.tv.desktop.MiruPlayDesktopComposeAppKt`; `MiruPlayDesktopComposeApp.kt` is a Compose Desktop window. | Covered structurally |
| Windows UI uses Compose Desktop | `desktop-app` applies `org.jetbrains.compose` and `org.jetbrains.kotlin.plugin.compose`; the default entry renders local library source/scan/search, WebDAV/SMB open/browse/scan, single-item Bangumi search/apply/clear, batch Bangumi preview/apply/undo, continue-watching recents, mpv runtime, RIFE, command preview, Launch/Stop controls, and CloudDrive2/RSS automation with Compose Material 3. | Covered for core desktop workflow |
| Windows visual language matches TV | `ui-design` now owns the shared MiruPlay palette; Android TV `Theme.kt` and Compose Desktop both derive `AnimeRed`, `DarkBg`, `DarkSurface`, `AccentBlue`, `TextPrimary`, `TextSecondary`, and `CardBg` from `MiruPlayPalette`. The root `checkUiPaletteDrift` task guards against raw palette literal drift in `ui-tv/src` and `desktop-app/src`. Android TV Library/Details/Player were refreshed on emulator `10.137.32.118:5555` by `tools/smoke-android-tv-ui.ps1`, including poster focus, DPAD poster -> Details, Details Play -> episode row, and episode row -> Player activation. Compose Desktop Library now puts 6-column poster-wall cards first after scan/load instead of opening on a tool/control panel; saved index entries are restored on startup/source switch so a scanned library opens directly to media. Compose Desktop source management now uses a focused two-line saved-source card that keeps source name/type visible, compacts long paths, and has unit-covered plus real-window directional movement between saved sources; the deep-path GUI smoke covers save, keyboard source switching, scan, clear, and remove in a real window. Compose Desktop remote source setup now uses separate WebDAV/SMB source cards with endpoint previews and a remote-browser panel instead of one dense form; the remote-browser rows now support `Up`/`Down` focus movement, first-row `Up` parent navigation, and `Enter` selection. Compose Desktop Cloud/RSS settings now uses overview/config/subscription/scheduler cards instead of one dense automation form, with credential/sync/RSS edit fields, action grids, enable/proxy/RSS toggle rows, and RSS subscription rows all supporting unit-covered TV-style focus movement. Compose Desktop Details opens directly from a poster click and starts with a TV-style hero for poster/backdrop, title, context, plot, Play, Back-to-poster-wall actions, unit-covered episode shelf plus season-selector focus, and unit-covered media-detail row focus. Compose Desktop Bangumi metadata controls now use TV-facing Chinese labels/status copy while keeping the same action-grid focus behavior. Compose Desktop Player hides the desktop rail and shows a TV-style playback stage with top return, centered play/seek/stop controls, bottom timeline, localized mpv status chips, and RIFE/subtitle chips before exposing TV-facing mpv/RIFE controls below; its media/start/subtitle fields now bridge into the fullscreen/keep-open/RIFE/backend row, those controls bridge down into the runtime card, the runtime `mpv.exe`/`portable_config`/`检查运行时` controls form a unit-covered vertical TV form with `Up` returning to playback settings, and the playback placeholder and route rail subtitle also use TV-facing Chinese copy instead of desktop/runtime English. Compose Desktop Settings now uses focused category rows, quick-action rows, plus TV-style status cards for media sources, playback, scan, metadata, and Cloud/RSS automation instead of generic placeholder summaries, and Settings cross-page references now say playback/details pages in Chinese. Local screenshot QA covers Library, Details, Player, Settings, and Cloud settings screens; keyboard smokes now cover Settings category movement, Cloud/RSS subscription row movement, route rail movement, poster-wall movement/open, Details hero action movement/open, Continue watching row selection, WebDAV remote-browser row/parent movement/open, saved-source card movement, Bangumi action-grid apply/clear, and Player transport movement/actions; unit tests cover Settings summary quick-action row movement, source-management local/remote field movement, saved-source card movement, RSS subscription row movement, Cloud/RSS credential/sync/RSS edit field and action/toggle movement, Bangumi match/candidate/result row navigation and localized labels/status copy, Bangumi/Details bottom-panel focus bridging, Details hero action movement, Details episode shelf season-selector movement, Details media-detail row movement, recent-playback row movement, route rail chrome copy, playback placeholder copy, Settings page-reference copy, Player settings/status localization, Player playback-setting field/toggle/backend row movement, Player Stage-to-settings-to-runtime bridging, and Player runtime form/check movement back to settings. These checks assert minimum TV-style window size, non-tiny PNG output, sampled visual diversity, dark-theme coverage, MiruPlay red accent pixels, readable light text pixels, distinct images, and visible keyboard-driven content changes. Remaining less-traveled keyboard/DPAD paths still need more TV-parity work. | Partial |
| Settings category, Cloud/RSS subscription row, and route rail keyboard navigation | `DesktopCloudRssPanel.kt` and `DesktopNavigation.kt` request focus for the selected rows and handle `Up`/`Down` to move categories/routes/subscriptions with TV-style edge stops; `tools/smoke-desktop-keyboard-focus-ui.ps1` verifies Sources -> CloudDrive, RSS row Down, and Details -> Player in the real desktop window. | Covered for the Settings category menu, RSS subscription rows, and desktop route rail |
| Library poster-wall keyboard navigation | `DesktopLibraryPanels.kt` requests focus for the selected poster and handles directional keys plus `Enter`; `tools/smoke-desktop-local-source-ui.ps1` verifies poster-wall Right -> Enter against generated fixtures. | Covered for generated local poster-wall flow |
| Shared logic is not trapped in the desktop UI shell | `core:model` owns reusable display formatting and external subtitle-track parsing; `repository-api` owns media-index display helpers and metadata batch planning. `desktop-app` keeps compatibility wrappers and desktop-specific presenter logic in `Desktop*Presenters.kt`; `DesktopPlaybackPresenters.kt` now owns command preview, runtime config validation, remote playback bridging, playback-source construction, `MpvProcessPlayer` creation, and launch status mapping instead of keeping that logic in `MiruPlayDesktopComposeApp.kt`. The behavior is tested in shared modules and desktop presenter tests and can be reused by Android TV or future KMP surfaces. | Covered for the extracted helpers and mpv launch/config presenter logic; more desktop UI state can still be split into shared use cases later |
| Windows playback uses mpv | `:player-mpv` builds mpv commands, starts an external process, supports IPC pause/seek/quit/time-position queries, and `desktop-app` launches `MpvProcessPlayer` through `DesktopPlaybackLauncher`; latest `:player-mpv:test :desktop-app:test` passed with 15 desktop tests including launch preparation, remote bridge preservation, runtime validation errors, and launch status/session output. | Covered for external process mode |
| Real mpv executable can launch | mpv_PlayKit `20260510` assets were downloaded into `.gradle/mpv-playkit-20260510`; `runtime/mpv` was prepared from `mpv-lazy-20260510.exe` plus the `mpv-lazy-20260510-vsNV.7z.001` overlay, then the default `:desktop-app:smokeMpvRuntime -PrequireMpvRuntime=true` gate passed with `mpv v0.41.0-615-g7b057f66f` and required RIFE `NVIDIA, DIRECTML`. | Covered |
| Bundled RIFE runtime is supported | Runtime layout expects `portable_config/vs/MEMC_RIFE_NV.vpy` and `MEMC_RIFE_DML.vpy` for the default release gate; app can also select the optional Standard script when present. The verifier blocks launch when a selected script is missing, and the runtime manifest is now checked against actual packaged relative files/directories instead of being trusted as display-only metadata. The current local manifest records the standard `.exe` base plus `vsNV` overlay and the default NVIDIA/DirectML requirements. | Covered structurally |
| Real RIFE payload works | DirectML RIFE smoke previously passed through `tools/smoke-mpv-rife.ps1 -Backend DIRECTML` with `runtime/mpv/mpv.exe`, `MEMC_RIFE_DML.vpy`, and a generated two-frame 1440x810 Y4M clip: mpv initialized VapourSynth and exited with playback success. The `-Backend ALL -AllowFailures` matrix mode reports all three backends in one run; `-ReportPath` can persist the same run plus host diagnostics and runtime manifest evidence as JSON. `tools/assert-mpv-rife-report.ps1 -RequireRuntimeManifest` can now turn that JSON into an explicit pass/fail assertion for required target backends and manifest evidence without rerunning playback. On this host, RIFE playback is treated as non-blocking because the machine is not expected to run interpolation well. | Covered structurally; target hardware validation remains |
| Runtime preparation is repeatable | `tools/prepare-mpv-runtime.ps1` accepts extracted directories or `.7z/.7z.001`, supports `-OverlaySource` for patching a base runtime with a RIFE/VapourSynth payload, optionally validates SHA256 before extraction including `filename=sha256` lists for split payloads, validates required RIFE scripts, copies to `runtime/mpv`, and writes `runtime-manifest.json`; tested with fake base/overlay payloads and with real mpv_PlayKit `20260510` standard + `vsNV` assets. | Covered |
| Desktop distribution runtime copy is controllable | `desktop-app/build.gradle.kts` bundles exactly one runtime source: explicit `-PmpvRuntimeSource` when present, otherwise repository `runtime/mpv`. `bundleMpvRuntime` defaults to `true` for self-contained artifacts, while `-PbundleMpvRuntime=false` skips the large runtime copy for UI-only development installs; verified with `:desktop-app:installDist -PbundleMpvRuntime=false` and `:desktop-app:smokePackagedMpvRuntime -PmpvRuntimeSource=runtime\mpv -PrequireMpvRuntime=true -PrequiredRifeBackends=NVIDIA,DIRECTML`, which builds `desktop-app.zip`, smokes `mpv.exe --version`, and checks the packaged runtime entries plus manifest-declared files/directories. | Covered |
| Runtime provenance is visible | `MpvRuntimeVerifier` reads `runtime-manifest.json`; `Check runtime` dialog shows localized source, verified time, required RIFE backends, files, and manifest-entry mismatch guidance when declared runtime evidence is missing or invalid. | Covered |
| Branch CI does not publish releases | `.github/workflows/ci.yml` keeps push validation enabled for `codex/**`, but the `nightly` job now only runs for scheduled or manual workflows on `refs/heads/main` or `refs/heads/master`; `build-release` remains restricted to main/master. Recent recorded `codex/windows-mpv-rife` `CI Build` run `26217656025` passed build/lint on commit `160f656`, with both publishing jobs skipped. | Covered |
| Local/WebDAV/SMB sources are available on desktop | `:media-source-desktop` implements local, WebDAV, and SMB sources. Compose Desktop exposes local source add/scan/search, saved-source switching, current-source index clearing/removal, WebDAV/SMB source open, directory browsing, current-source scanning, loopback bridge playback for remote media, and selected-media details for local index entries, remote browser entries, and recent playback records. `tools/smoke-desktop-local-source-ui.ps1` starts the Windows GUI with an isolated store, adds a local source from either a generated fixture or `-LibraryRoot`, scans it, validates the persisted source/index JSON, records scan/search/details/player screenshots, verifies poster-wall search can filter to a target anime, verifies poster click opens Details, and verifies Player handoff. `tools/smoke-desktop-webdav-source-ui.ps1` starts a loopback Basic Auth WebDAV fixture, adds the WebDAV source through the TV-style remote source card, verifies authorized PROPFIND/GET traffic, browses the remote directory, scans sibling NFO metadata into the desktop index, returns to the TV-style poster wall, opens Details from the remote poster, and verifies Player receives the remote media path. `tools/smoke-desktop-smb-source-ui.ps1` creates a timestamped fixture only under the approved SMB test directory, opens the authenticated SMB URL through the TV-style remote source card, scans one NFO-backed `Fixture SMB` video, verifies Details/Player handoff, deletes the fixture directory, defaults to the approved `test-user` smoke credentials when env vars are absent, and redacts username/password/domain from the stored evidence. `DesktopSmbLiveShareTest` is opt-in and passed against `smb://smb.example.test/share/临时文件/测试` when provided live credentials. `DesktopRemotePlaybackSecurityTest` verifies WebDAV and SMB playback are converted to loopback bridge URLs before mpv command construction and that source host/user/password/domain fragments are absent from the command. | Covered for local/WebDAV/SMB GUI flows and remote command credential isolation |
| Desktop library indexing exists | `:scanner-desktop` scans desktop sources, filters videos, infers metadata, reads sibling `.nfo` and `tvshow.nfo`, and rebuilds `repository-desktop` index. The local-source GUI smoke validates that a generated NFO fixture appears in the desktop index as `Fixture Frieren` episode 2, verifies repository search can isolate that anime, and verifies poster-wall selection/detail/player handoff. The WebDAV GUI smoke validates that a remote NFO fixture appears as `Fixture WebDAV` episode 2 and that scanning returns the Library first screen to a poster wall. The SMB GUI smoke validates that a real authenticated SMB fixture appears as `Fixture SMB` episode 2 and that scanning returns to the poster wall. `tools/smoke-desktop-source-management-ui.ps1` proves saved-source keyboard switching, Clear index, and Remove source update the isolated desktop store: the active source index is cleared, the keyboard-selected source is removed, and the untouched second source remains. The local smoke also passes against the real anime library at `D:\Software\dufs`, with `build/desktop-local-source-ui/run-20260520-080904/local-source-scanned.png` showing the Library first screen as a poster wall. | Covered |
| Desktop media metadata can be inspected | Compose Desktop has a TV-style Details hero backed by selected index media plus a localized `媒体详情` panel backed by `DesktopMediaDetailRows`, showing active source, indexed title/type/anime/season/episode/title, Bangumi metadata source/id/title, indexed size/modified time, browser item kind/MIME/size/modified time/path, plot when present, and recent playback resume/play count/last watched. Unit tests cover the row model, hero title/subtitle helpers, Details chrome labels, episode-shelf subtitle, and Continue watching labels; the local-source GUI smoke now opens the Details route by selecting a scanned local poster. | Covered |
| Bangumi metadata is available on desktop | `:scraper-desktop` provides JVM Bangumi search/details/episodes; `:scraper-desktop:smokeBangumiLive` verifies the live search/details/episodes path and writes token-free JSON evidence; Compose Desktop has localized `使用当前条目`, `搜索`, `应用匹配`, and `清除元数据` controls to write or clear selected match source/id/title on one index entry. `tools/smoke-desktop-bangumi-metadata-ui.ps1` now proves the real Details screen flow with store assertions and screenshots for ready/search/applied/cleared states. | Covered for live scraper path and GUI single-item apply/clear flow |
| Batch metadata workflows | Compose Desktop has `Batch preview`, `Apply batch`, `Accept review`, and `Undo batch`; batch plans split matches into ready/review/conflict, render the preview as a selectable review queue, retain alternate Bangumi candidates per query, let the user switch the selected candidate before applying or manually accepting a reviewed match, skip existing-metadata conflicts instead of overwriting them, apply only high-confidence ready updates automatically, and persist the last rollback list in the desktop JSON store so undo survives restart. | Covered |
| Metadata batch planning is reusable | `repository-api/src/main/kotlin/com/miruplay/tv/repository/MetadataBatchPlanner.kt` owns query derivation, ready/review/conflict splitting, conflict isolation, preview text, and plan summaries; `desktop-app` delegates through thin compatibility wrappers. `:repository-api:test :desktop-app:test --rerun` passed after extraction. | Covered for planner logic |
| Index and subtitle display helpers are reusable | `repository-api/src/main/kotlin/com/miruplay/tv/repository/MediaIndexDisplay.kt` owns `MediaIndexEntry` display names/lines/browser conversion. `core/model/src/main/kotlin/com/miruplay/tv/model/SubtitleTracks.kt` owns external subtitle path parsing and format detection. Shared module tests cover both. | Covered for extracted helpers |
| Playback progress continues on desktop | Compose Desktop saves progress when mpv launches, polls mpv `time-pos` every 10 seconds while playback is active, saves a session-estimated position immediately on Stop so process shutdown is not blocked by IPC, shows continue-watching records, can clear a selected recent item, provides TV-style Play/Pause/-10s/+30s/Stop controls in the playback stage with left/right focus movement, Enter activation, and Up/Down movement to/from `返回详情`, adds unit-covered movement from playback-setting fields through toggles/backend into the runtime card and back, adds unit-covered vertical movement through runtime path fields and `检查运行时`, drives native desktop fullscreen from the Player `全屏` toggle while preserving route-based restore behavior, and stores original remote path rather than loopback URL. `tools/smoke-desktop-mpv-launch-ui.ps1` now proves the GUI can launch a generated local sample through mpv, exercise Pause/-10s/+30s/Stop, confirm the mpv process exits, and record `mpv-settings-focus.png`, `mpv-runtime-focus.png`, `mpv-launch-ready.png`, `mpv-launched.png`, `mpv-keyboard-controls-used.png`, and `mpv-stopped.png` evidence with a 30s saved position. | Covered |
| True mpv position tracking | `MpvIpcClient` can request `get_property time-pos`, `MpvProcessPlayer.queryTimePositionMs()` exposes it, and Compose Desktop uses `syncPlaybackProgressFromMpv` to re-anchor the session and persist observed mpv positions during playback and at stop. Unit tests cover session re-anchoring and sync helper success/null/error behavior. | Covered for Compose Desktop |
| Cloud/RSS sync parity on desktop | `repository-desktop` persists `CloudDriveAutomationConfig`, RSS subscriptions, processed items, download tasks, and file-backed CloudDrive/Bangumi credentials in the JSON store. `:cloud-drive-desktop` provides a JVM CloudDrive2 gRPC client, `:sync-engine-desktop` provides a JVM RSS runner plus scheduler with feed fetch, filtering, processed-item dedupe, CloudDrive offline submission, torrent-to-magnet staging, organizer moves, `lastRunAt` updates, `runIfDue`, scheduler state flow, and a desktop post-sync source rescan hook. Compose Desktop now exposes CloudDrive2/RSS config, token/password save/clear, `Login`, `Verify token`, inbox/library `选择目录`, subscription add/update/delete, `Run sync now`, `Start scheduler`, and `Stop scheduler` in TV-style overview/config/subscription/scheduler cards with compact endpoint/path/subscription previews, keyboard-moving credential/sync/RSS edit fields, path picker actions, action rows, enable/proxy/RSS toggles, and RSS subscription rows. The Windows CloudDrive path picker reuses the JVM gRPC client, verifies token info before browsing, clamps requested paths to the token root, lists visible directories only, supports parent navigation plus current-folder selection, and has unit coverage for root clamping, folder filtering, directory action-row movement, directory row/empty-state movement, and path picker focus bridges. The directory browser action row moves horizontally across `使用当前目录`/`返回上级`/`关闭`, `Down` enters the first visible folder row or focusable loading/empty row, and first-row/empty-row `Up` returns to `使用当前目录` without wrapping. The directory browser state flow now lives in `DesktopCloudDriveDirectoryBrowser` instead of inline Compose state mutation, with fake-client tests covering token-root scoping, outside-path clamping before list calls, visible-directory filtering, listing-error propagation, and selection normalization. Post-sync source rescan now goes through `rescanCloudRssLinkedSource`, a reusable desktop helper that scans the linked source, rebuilds the file-backed index, reports whether Library or remote status should be updated, and returns the refreshed video entries for active-source UI state. `DesktopScanIndexIntegrationTest` creates a local fixture source, writes an old indexed episode, changes the fixture contents, runs the Cloud/RSS linked-source rescan helper, and verifies the repository index is replaced with the new episode. `:cloud-drive-desktop:test` starts a real loopback gRPC server against the generated CloudDrive2 stub and covers login, API token info, bearer auth listing, raw-token fallback, token-free live-smoke report JSON, and live-smoke listing summaries. `:sync-engine-desktop:test` now also runs `DesktopCloudDriveRssAutomationEngine` against a loopback CloudDrive2 gRPC server through the real `GrpcCloudDriveClient`, covering RSS offline submission, bearer metadata, processed item persistence, download-task persistence, and organizer list calls. `:cloud-drive-desktop:smokeCloudDrive2` is available for live endpoint/token/path QA without printing the token and can write `-PcloudDriveReportPath=...` token-free JSON evidence; `tools/assert-cloud-drive-report.ps1` validates generatedAtUtc, endpoint/path, permission booleans, listing counts, and preview items, and `tools/verify-windows-port.ps1 -CloudDrive` runs the smoke plus report assertion behind explicit endpoint/token parameters. `:sync-engine-desktop:smokeCloudDriveRssDryRun` verifies a real endpoint, token, inbox/library listing, RSS fetch/parse, filter matching, and would-submit counts without calling CloudDrive offline download APIs; `-PcloudDriveRssReportPath=...` writes a token-free JSON dry-run evidence report. `:sync-engine-desktop:smokeCloudDriveRssLiveSubmit` is now available as a separate explicit-confirmation task that submits a limited number of live RSS candidates and records submit counts plus post-submit inbox listing in a token-free JSON report. Both desktop RSS smoke tasks can also run the real organizer when `-PcloudDriveRssOrganize=true` and `-PcloudDriveRssOrganizeConfirmation=I_UNDERSTAND_THIS_MOVES_REAL_CLOUDDRIVE_FILES` are supplied; the same token-free JSON report records moved count plus post-organize inbox/library listing counts. `tools/assert-cloud-rss-report.ps1` validates dry-run/live-submit/organize reports for inbox/library paths, RSS/candidate counts, submission type totals, token permissions, submit/organize evidence, preview entries, and token redaction; `:sync-engine-desktop:smokeCloudDriveRssScheduler` drives the desktop scheduler loop over real elapsed time with a local due-runner, verifies first start/duplicate start, observed checks, due-run summary, stop state, and writes a token-free scheduler report; `tools/assert-cloud-rss-scheduler-report.ps1` validates scheduler timing/state/summary evidence; `tools/verify-windows-port.ps1` and CI now run the scheduler smoke by default, while `-SkipCloudRssScheduler` only skips it for temporary local troubleshooting. Live submit/organize QA still needs to be executed against a real server before completion can be claimed. | Partial |
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
The CloudDrive directory picker now pages folder rows in six-item TV-style
windows: `Down` from row 6 moves to row 7 on the next page, `Up` moves back
across the page boundary, and the picker shows a Chinese range summary so
large CloudDrive folders are not silently truncated.
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

## Latest Verification Commands

```powershell
.\tools\verify-windows-port.ps1

# Uses JDK 21 automatically when available; set JAVA21_HOME or JDK21_HOME to override discovery.

# Optional live/device/runtime gates:
.\tools\verify-windows-port.ps1 -Gui
.\tools\verify-windows-port.ps1 -RealLibrary -RealLibraryRoot 'D:\Software\dufs'
.\tools\verify-windows-port.ps1 -AndroidTv -AndroidDeviceId 10.137.32.118:5555
.\tools\verify-windows-port.ps1 -Smb
.\tools\verify-windows-port.ps1 -MpvRuntime -PackagedMpvRuntime
.\tools\verify-windows-port.ps1 -Rife -RifeBackend ALL -AllowRifeFailures

# The -Smb switch is restricted to \\smb.example.test\share\临时文件\测试.
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

.\tools\smoke-android-tv-ui.ps1 -DeviceId 10.137.32.118:5555

.\tools\assert-android-tv-smoke-report.ps1 `
  -ReportPath .\build\android-tv-qa\run-20260520-190519\android-tv-smoke-report.json `
  -RequiredDeviceId 10.137.32.118:5555

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
  -RequiredDeviceId 10.137.32.118:5555

.\gradlew.bat checkDesktopPresenterSeparation `
  checkDesktopComposeOnly `
  checkUiPaletteDrift `
  :player-mpv:test `
  :desktop-app:test `
  :app:assembleDebug

.\gradlew.bat :desktop-app:installDist -PbundleMpvRuntime=false

.\tools\smoke-desktop-local-source-ui.ps1 -LibraryRoot 'D:\Software\dufs'

adb -s 10.137.32.118:5555 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s 10.137.32.118:5555 shell am start -n com.miruplay.tv/.MainActivity --es test_local_path /sdcard/Movies/MiruPlayTvFixture-20260519134617
adb -s 10.137.32.118:5555 exec-out screencap -p > build\android-tv-qa\library-fixture-20260519-rife-optional.png

.\tools\smoke-desktop-source-management-ui.ps1

.\tools\smoke-desktop-webdav-source-ui.ps1

.\tools\smoke-desktop-smb-source-ui.ps1

.\tools\smoke-desktop-mpv-launch-ui.ps1

.\tools\smoke-desktop-bangumi-metadata-ui.ps1

adb connect 10.137.32.118:5555
adb shell monkey -p com.miruplay.tv -c android.intent.category.LAUNCHER 1
adb exec-out screencap -p > build\android-tv-qa\library-baseline-20260519.png

adb -s 10.137.32.118:5555 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s 10.137.32.118:5555 shell am start -n com.miruplay.tv/.MainActivity --es test_local_path /sdcard/Movies/MiruPlayTvFixture-20260519134617
adb -s 10.137.32.118:5555 exec-out screencap -p > build\android-tv-qa\library-fixture-20260519134617.png

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

1. Continue narrowing TV parity gaps in less-traveled keyboard/DPAD focus paths outside the now-covered Android TV Settings category/page traversal, Settings summary quick-action rows, source-management local/remote fields plus actions and remote editor/browser focus bridge, Cloud/RSS credential/sync/RSS edit fields plus path picker/action/toggle/subscription/scheduler rows, desktop route rail, Library header action row, Library poster wall/highest-heat/recent shelves/search row/source bridge, Details hero actions, Details hero-to-episodes/recents/Bangumi/media-details fallback plus episode-shelf season selector, remote browser list including parent navigation, Player stage/settings/runtime focus bridge, saved-source card movement, and Bangumi metadata lists/action grid/apply-clear flow.
2. Validate RIFE on target Windows hardware that is expected to support
   interpolation, and decide whether the optional Standard backend should ship
   an additional `rife` plugin.
3. Run live CloudDrive2 end-to-end QA for real offline submission, torrent
   staging, and organization.
   Token/path/RSS parsing can now be checked first with the dry-run smoke task.
