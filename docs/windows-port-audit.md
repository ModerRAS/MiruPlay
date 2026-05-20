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
source dropdown with a TV-style focused source card that compacts long paths
and now has both unit-covered and real-window keyboard directional switching,
and split WebDAV/SMB setup into remote source cards with compact endpoint
previews. Cloud/RSS settings now also render as TV-style overview/config/
subscription/scheduler cards instead of one dense automation form, and RSS
subscription rows now support `Up`/`Down` key movement. The Settings category
menu and desktop route rail now support `Up`/`Down` key navigation,
and the Library poster wall supports directional movement plus `Enter` to open
Details. The remote browser list now supports `Up`/`Down` row movement, first-row
`Up` parent navigation, and `Enter` without opening directories during focus
movement. Real Windows GUI
smokes prove Sources to CloudDrive, Details to Player, poster-wall Right to
Enter, CloudDrive RSS subscription row Down, Details hero Right to Left to
Enter, Details hero Down to Continue watching Enter, WebDAV remote-browser Up
to parent plus Down to Enter, saved-source card Up, and Player transport paths
by keyboard input.
Release packaging now has a packaged runtime gate that builds `desktop-app.zip`,
launches the packaged runtime source with `mpv.exe --version`, and verifies the
zip contains the mpv executable, runtime manifest, and NVIDIA/DIRECTML RIFE
scripts.

## Prompt-to-Artifact Completion Matrix

| Objective deliverable | Concrete artifacts inspected | Verification evidence | Remaining gap |
|---|---|---|---|
| Keep the original Android TV app working | `app/src/main/kotlin/com/miruplay/tv/MainActivity.kt`, `ui-tv/`, Android Gradle modules, `tools/smoke-android-tv-ui.ps1` | `:app:assembleDebug` passed in the latest preservation check. `tools/smoke-android-tv-ui.ps1` installed the debug APK on emulator `10.137.32.118:5555`, generated seven playable MP4/NFO fixture shows, pushed them under `/sdcard/Movies`, launched with `test_local_path`, clicked scan, opened a fixture poster, clicked Play, and captured `build/android-tv-qa/run-20260520-112248/android-tv-library.png`, `android-tv-details.png`, `android-tv-player.png`, XML dumps, and `android-tv-smoke-report.json`. | Broader TV-device regression still needs manual coverage for settings/source-management and less-traveled focus paths. |
| Add a Windows desktop entry | `desktop-app/build.gradle.kts`, `MiruPlayDesktopComposeApp.kt` | `mainClass` points to `com.miruplay.tv.desktop.MiruPlayDesktopComposeAppKt`; `:desktop-app:test`, lightweight `:desktop-app:installDist -PbundleMpvRuntime=false`, and full `:desktop-app:smokePackagedMpvRuntime -PmpvRuntimeSource=runtime\mpv -PrequireMpvRuntime=true -PrequiredRifeBackends=NVIDIA,DIRECTML` pass. | Release artifact signing/installer is outside this audit. |
| Use Compose-family UI on both targets | Android remains Compose/Compose TV; desktop default entry is Compose Multiplatform Desktop | `desktop-app` applies Compose plugins, `application.mainClass` points at `MiruPlayDesktopComposeAppKt`, screenshot QA launches the Compose entry, the old Swing shell has been removed from production sources, and root `checkDesktopComposeOnly` fails if Swing UI imports/classes or `coroutines-swing` return. | Covered structurally; full Android-TV-vs-desktop screen parity is tracked separately. |
| Preserve media management capabilities on desktop | `media-source-desktop`, `scanner-desktop`, `repository-api`, `repository-desktop`, `scraper-desktop`, `sync-engine-desktop`, `cloud-drive-desktop`, Compose Library/Details/Settings panels | Unit and integration coverage now includes shared display/subtitle/index/batch planning helpers, desktop details rows, playback progress, RSS scheduler, CloudDrive gRPC client, RSS offline submission through real `GrpcCloudDriveClient`, and remote playback command security that keeps WebDAV/SMB hosts and credentials out of mpv command lines. | Real CloudDrive2 server live QA remains open for token validation, offline submission, torrent staging, organization, scheduler timing, and source rescan. |
| Use mpv as Windows playback backend | `player-mpv`, `MpvProcessPlayer`, `MpvIpcClient`, Compose Player panel, `tools/smoke-desktop-mpv-launch-ui.ps1` | `:player-mpv:test` passes; desktop app launches `MpvProcessPlayer`; progress sync polls mpv `time-pos` while playing; RIFE is opt-in by default on desktop so low-capability hosts can launch plain mpv first; missing mpv/RIFE launch failures point users to Check runtime, preparing a backend, or turning RIFE off. The GUI mpv launch smoke generates a local Y4M sample, fills it into the Windows Player, disables RIFE for host-safe playback, clicks Play, confirms an `mpv.exe` child process containing the sample path, exercises Pause, -10s, +30s, and Stop through keyboard focus, verifies a persisted progress record, and captures launched/keyboard-control/stopped Player screenshots. `MpvProcessPlayer.stop()` escalates from IPC quit to destroy and force-destroy, including descendant process cleanup when available. | External-process mpv mode is covered; embedded libmpv is intentionally deferred. |
| Plan and integrate a bundled RIFE-capable mpv runtime | `runtime/mpv`, `tools/prepare-mpv-runtime.ps1`, `desktop-app:verifyMpvRuntimePayload`, `smokeMpvRuntime`, `smokePackagedMpvRuntime`, `tools/smoke-mpv-rife.ps1`, `docs/mpv-runtime-packaging.md` | Runtime verifier and Gradle smoke pass for NVIDIA/DIRECTML scripts; `:desktop-app:smokePackagedMpvRuntime -PmpvRuntimeSource=runtime\mpv -PrequireMpvRuntime=true -PrequiredRifeBackends=NVIDIA,DIRECTML` builds `desktop-app.zip`, launches the packaging runtime with `mpv.exe --version`, and verifies the bundled zip contains `runtime/mpv/mpv.exe`, `runtime-manifest.json`, and the NVIDIA/DIRECTML scripts; prior DirectML VapourSynth/RIFE smoke proved the payload path; `-Backend ALL -AllowFailures` reports the backend matrix. `tools/smoke-mpv-rife.ps1 -ReportPath ...` writes a JSON evidence bundle with mpv version, host OS/CPU/GPU diagnostics, backend statuses, exit codes, and log paths for target-host QA. | Local RIFE playback is non-blocking on this host; backend performance/compatibility must be validated on target hardware. |
| Make Windows UI visually match the TV UI | `ui-design`, `ui-tv/.../theme/Theme.kt`, `MiruPlayDesktopComposeApp.kt`, root `checkUiPaletteDrift`, `tools/capture-desktop-ui.ps1`, `tools/smoke-android-tv-ui.ps1`, `build/desktop-ui-qa/*.png`, `build/android-tv-qa/run-20260520-112248/*.png` | TV and desktop now derive the MiruPlay red/dark/blue/text/card palette from the same `MiruPlayPalette` constants. `checkUiPaletteDrift` fails if TV or desktop UI reintroduces raw shared palette literals outside `:ui-design`. Android TV smoke captures the media-first Library, Details hero with Play/episodes, and full-screen Player overlay against a playable fixture. Windows Library uses the TV-style full-width Explore header, right-side actions, TV empty state, and a 6-column poster-wall first screen after scanning or loading a saved index; highest-heat/recent rows plus search/source controls sit below the wall. Source management now shows the active saved source as a two-line TV-style focus card with visible source name/type, middle-compacted local/WebDAV/SMB paths, and unit-covered plus real-window directional saved-source movement. WebDAV and SMB setup now render as separate TV-style remote source cards with compact endpoint previews beside a remote-browser panel that also compacts long paths. Cloud/RSS settings now render as TV-style overview, CloudDrive2, sync-path, subscription, and scheduler cards; RSS subscription rows now handle `Up`/`Down` selection; `tools/capture-desktop-ui.ps1` now captures both `settings.png` and `settings-cloud.png` and asserts visual quality/distinctness. The latest Settings keyboard smoke preloaded two RSS subscriptions, selected the first visible row, sent `Down`, and captured `build/desktop-keyboard-focus-ui/run-20260520-141716/keyboard-settings-rss-first.png` and `keyboard-settings-rss-second.png` to prove row navigation. The latest source-management GUI smoke created two deep local fixture paths, used keyboard `Up` on the saved-source card to switch from Season 02 back to Season 01, verified the subsequent scan indexed the keyboard-selected source id, and captured `build/desktop-source-management-ui/run-20260520-133927/source-management-saved-source-keyboard.png`, `source-management-scanned.png`, `source-management-controls.png`, `source-management-cleared.png`, and `source-management-removed.png`. The latest WebDAV GUI smoke captured `build/desktop-webdav-source-ui/run-20260520-121024/webdav-source-opened.png`, `webdav-source-browsed.png`, `webdav-source-keyboard-up.png`, `webdav-source-keyboard-browse.png`, `webdav-source-keyboard-select.png`, `webdav-source-poster-wall.png`, `webdav-source-details.png`, and `webdav-source-player.png` with the remote card layout plus keyboard parent/row navigation. The latest SMB GUI smoke captured `build/desktop-smb-source-ui/run-20260520-090324/smb-source-opened.png`, `smb-source-poster-wall.png`, `smb-source-details.png`, and `smb-source-player.png` against the approved `临时文件\测试` directory with redacted credentials. The latest generated local-source smoke captured `build/desktop-local-source-ui/run-20260520-113934/local-source-poster-keyboard.png`, `local-source-details.png`, and `local-source-player.png`, proving poster-wall Right -> Enter keyboard selection before Details and Details hero Right -> Left -> Enter keyboard activation before Player handoff. The latest real-library smoke against `D:\Software\dufs` captured `build/desktop-local-source-ui/run-20260520-080904/local-source-scanned.png`, which indexed 22 videos and opens directly on the poster wall. Poster selection routes directly to Details, Details starts with a TV-style hero, Player opens without the desktop rail into a TV-like playback stage with top return, centered transport controls, bottom timeline/status chips, and advanced mpv/RIFE settings below the stage. Screenshot QA covers Library/Details/Player/Settings/Cloud settings and asserts window size, dark palette, red accent, readable light text, visual diversity, and per-section distinctness. | Settings category, Cloud/RSS subscription rows, route rail, Library poster-wall, Details hero actions, Continue watching recents, remote-browser list, Player transport, Bangumi metadata-list, and saved-source card navigation are now covered by GUI or unit tests; remaining less-traveled keyboard/DPAD paths still need more TV-parity work. |
| Settings and route rail keyboard/DPAD navigation | `DesktopCloudRssPanel.kt`, `DesktopNavigation.kt`, `tools/smoke-desktop-keyboard-focus-ui.ps1`, `build/desktop-keyboard-focus-ui/run-20260520-141716/*.png` | The Settings category menu, Cloud/RSS RSS subscription rows, and desktop route rail now keep focus on the selected row and handle `Up`/`Down`; `DesktopSettingsPanelTest` covers RSS subscription edge stops and null-selection entry. The Windows GUI smoke opens Settings, sends two `Down` key presses from Sources to CloudDrive, selects a preloaded RSS row, sends `Down` to move from Beta to Alpha, then focuses Details and sends `Down` to Player, capturing `keyboard-settings-sources.png`, `keyboard-settings-cloud.png`, `keyboard-settings-rss-first.png`, `keyboard-settings-rss-second.png`, `keyboard-nav-details.png`, and `keyboard-nav-player.png`, and asserting the content region changed for these key paths. | Remaining less-traveled keyboard/DPAD paths outside these menus still need more TV-parity work. |
| Library poster-wall keyboard/DPAD navigation | `DesktopLibraryPanels.kt`, `tools/smoke-desktop-local-source-ui.ps1`, `build/desktop-local-source-ui/run-20260520-113934/*.png` | The poster wall now keeps focus on the selected poster, moves selection with directional keys, and opens Details on `Enter`; the generated local-source GUI smoke scans two fixture shows, sends `Right`, captures `local-source-poster-keyboard.png` with the second poster selected, sends `Enter`, and verifies Details and Player handoff. | Deeper cross-row poster paths can still be expanded later. |
| Details hero keyboard/DPAD navigation | `DesktopDetailsPanels.kt`, `DesktopDetailHeroTest.kt`, `tools/smoke-desktop-local-source-ui.ps1`, `build/desktop-local-source-ui/run-20260520-113934/*.png` | The Details hero now requests focus on the primary Play action, moves between Play and Back to poster wall with `Left`/`Right`, and activates the focused action with `Enter`; the generated local-source GUI smoke opens Details from the poster wall, sends `Right`, `Left`, then `Enter`, and verifies Player receives the selected media path. | Deeper Details-to-metadata panel focus paths can still be expanded later. |
| Continue watching keyboard/DPAD navigation | `DesktopDetailsPanels.kt`, `MiruPlayDesktopComposeApp.kt`, `DesktopDetailHeroTest.kt`, `tools/smoke-desktop-mpv-launch-ui.ps1`, `build/desktop-mpv-launch-ui/run-20260520-115654/*.png` | The Details hero now moves focus down into Continue watching when recent records exist; recent rows move with `Up`/`Down` and activate with `Enter`. The mpv GUI smoke launches a generated Y4M sample, stops playback, returns to Details, sends `Down` then `Enter` on the recent row, captures `mpv-recent-keyboard-selected.png`, and verifies Player receives the same media path. | Multi-row recent-history traversal can still be expanded later. |
| Remote browser keyboard/DPAD navigation | `DesktopLibraryPanels.kt`, `DesktopSourcePickerTest.kt`, `tools/smoke-desktop-webdav-source-ui.ps1`, `build/desktop-webdav-source-ui/run-20260520-121024/*.png` | The remote browser now keeps focus on the selected row, moves with `Up`/`Down` without opening directories, opens/selects with `Enter`, and uses first-row `Up` to navigate to the parent/root path; the loopback WebDAV GUI smoke opens the fixture directory, sends `Up`, captures `webdav-source-keyboard-up.png` at the root, sends `Enter` back into the fixture directory, sends `Down`, captures `webdav-source-keyboard-browse.png`, sends `Enter`, captures `webdav-source-keyboard-select.png`, and then scans/opens Details/Player from the same remote source. | Deeper multi-level remote browsing can still be expanded later. |
| Player transport keyboard/DPAD navigation | `DesktopPlaybackPanels.kt`, `tools/smoke-desktop-mpv-launch-ui.ps1`, `build/desktop-mpv-launch-ui/run-20260520-115654/*.png` | The Player stage now keeps focus on the primary transport, moves across active controls with `Left`/`Right`, and activates with `Enter`; the mpv GUI smoke launches a generated Y4M sample, sends `Enter`, `Left+Enter`, `Right+Right+Enter`, and `Right+Enter` to pause, seek, and stop, then verifies mpv exited and progress persisted. | Full-screen mpv/window-manager specific key paths can still be expanded later. |
| Bangumi metadata-list keyboard/DPAD navigation | `DesktopBangumiPanel.kt`, `DesktopBangumiNavigationTest.kt`, `tools/smoke-desktop-bangumi-metadata-ui.ps1` | Bangumi batch matches, candidate review rows, and search results now share row key handling: `Up`/`Down` moves through visible rows, `Right` enters candidate review, `Left` returns to batch matches, and `Enter` selects the focused row. `:desktop-app:test` covers row ordering, horizontal candidate review entry/exit, visible-row clamping, and edge stops. `:scraper-desktop:smokeBangumiLive` now verifies live Bangumi search/details/episodes and writes token-free JSON evidence. The Windows GUI smoke opens Details, uses `Use selected`, runs live `Search`, clicks `Apply match`, verifies Bangumi source/id/title are persisted, clicks `Clear metadata`, and verifies those fields are cleared. | Broader live-service regression can be repeated with the smoke tasks as needed. |
| Provide auditable verification gates | Gradle MCP build records, `.github/workflows/ci.yml`, scripts under `tools/`, this audit document | CI now runs the Android debug build plus desktop/shared JVM checks: `checkDesktopComposeOnly`, `checkUiPaletteDrift`, `:core:model:test`, `:repository-api:test`, `:player-mpv:test`, `:cloud-drive-desktop:test`, `:sync-engine-desktop:test`, `:desktop-app:test`, and lightweight `:desktop-app:installDist -PbundleMpvRuntime=false`. Latest local commands are listed below with passing evidence for Android build, desktop tests, mpv tests, CloudDrive loopback tests, runtime smoke, packaged runtime zip smoke, DirectML RIFE smoke, and screenshot QA. | Hardware/cloud/live-service checks are intentionally tracked as not achieved. |

Completion decision: do not mark complete. The project has a usable Windows
Compose Desktop port and preserved Android debug build evidence, but the
objective requires real-world confidence across bundled RIFE backends and full
CloudDrive2 behavior. Those still depend on target GPU/driver/plugin stacks and
a live CloudDrive2 environment.

## Checklist

| Requirement | Current evidence | Status |
|---|---|---|
| Android TV remains buildable | `.\gradlew.bat :app:assembleDebug` passed in the latest preservation check after the desktop port work. `.\tools\smoke-android-tv-ui.ps1` also installed and launched the debug APK on emulator `10.137.32.118:5555`, recording `build/android-tv-qa/run-20260520-112248`. | Covered for debug build and current device smoke |
| Android TV uses Compose TV | Existing Android app remains `MainActivity` + Compose navigation and `ui-tv` Compose/TV screens. | Covered structurally |
| Windows desktop entry exists | `:desktop-app` JVM application now points `mainClass` at `com.miruplay.tv.desktop.MiruPlayDesktopComposeAppKt`; `MiruPlayDesktopComposeApp.kt` is a Compose Desktop window. | Covered structurally |
| Windows UI uses Compose Desktop | `desktop-app` applies `org.jetbrains.compose` and `org.jetbrains.kotlin.plugin.compose`; the default entry renders local library source/scan/search, WebDAV/SMB open/browse/scan, single-item Bangumi search/apply/clear, batch Bangumi preview/apply/undo, continue-watching recents, mpv runtime, RIFE, command preview, Launch/Stop controls, and CloudDrive2/RSS automation with Compose Material 3. | Covered for core desktop workflow |
| Windows visual language matches TV | `ui-design` now owns the shared MiruPlay palette; Android TV `Theme.kt` and Compose Desktop both derive `AnimeRed`, `DarkBg`, `DarkSurface`, `AccentBlue`, `TextPrimary`, `TextSecondary`, and `CardBg` from `MiruPlayPalette`. The root `checkUiPaletteDrift` task guards against raw palette literal drift in `ui-tv/src` and `desktop-app/src`. Android TV Library/Details/Player were refreshed on emulator `10.137.32.118:5555` by `tools/smoke-android-tv-ui.ps1`. Compose Desktop Library now puts 6-column poster-wall cards first after scan/load instead of opening on a tool/control panel; saved index entries are restored on startup/source switch so a scanned library opens directly to media. Compose Desktop source management now uses a focused two-line saved-source card that keeps source name/type visible, compacts long paths, and has unit-covered plus real-window directional movement between saved sources; the deep-path GUI smoke covers save, keyboard source switching, scan, clear, and remove in a real window. Compose Desktop remote source setup now uses separate WebDAV/SMB source cards with endpoint previews and a remote-browser panel instead of one dense form; the remote-browser rows now support `Up`/`Down` focus movement, first-row `Up` parent navigation, and `Enter` selection. Compose Desktop Cloud/RSS settings now uses overview/config/subscription/scheduler cards instead of one dense automation form, and RSS subscription rows support `Up`/`Down` focus movement. Compose Desktop Details opens directly from a poster click and starts with a TV-style hero for poster/backdrop, title, context, plot, Play, and Back-to-poster-wall actions. Compose Desktop Player hides the desktop rail and shows a TV-style playback stage with top return, centered play/seek/stop controls, bottom timeline, and RIFE/subtitle status chips before exposing advanced mpv controls below. Compose Desktop Settings now uses focused category rows plus TV-style status cards for media sources, playback, scan, metadata, and Cloud/RSS automation instead of generic placeholder summaries. Local screenshot QA covers Library, Details, Player, Settings, and Cloud settings screens; keyboard smokes now cover Settings category movement, Cloud/RSS subscription row movement, route rail movement, poster-wall movement/open, Details hero action movement/open, Continue watching row selection, WebDAV remote-browser row/parent movement/open, saved-source card movement, and Player transport movement/actions; unit tests cover saved-source card movement, RSS subscription row movement, Bangumi match/candidate/result row navigation, Details hero action movement, and recent-playback row movement. These checks assert minimum TV-style window size, non-tiny PNG output, sampled visual diversity, dark-theme coverage, MiruPlay red accent pixels, readable light text pixels, distinct images, and visible keyboard-driven content changes. Remaining less-traveled keyboard/DPAD paths still need more TV-parity work. | Partial |
| Settings category, Cloud/RSS subscription row, and route rail keyboard navigation | `DesktopCloudRssPanel.kt` and `DesktopNavigation.kt` request focus for the selected rows and handle `Up`/`Down` to move categories/routes/subscriptions; `tools/smoke-desktop-keyboard-focus-ui.ps1` verifies Sources -> CloudDrive, RSS row Down, and Details -> Player in the real desktop window. | Covered for the Settings category menu, RSS subscription rows, and desktop route rail |
| Library poster-wall keyboard navigation | `DesktopLibraryPanels.kt` requests focus for the selected poster and handles directional keys plus `Enter`; `tools/smoke-desktop-local-source-ui.ps1` verifies poster-wall Right -> Enter against generated fixtures. | Covered for generated local poster-wall flow |
| Shared logic is not trapped in the desktop UI shell | `core:model` owns reusable display formatting and external subtitle-track parsing; `repository-api` owns media-index display helpers and metadata batch planning. `desktop-app` keeps compatibility wrappers and desktop-specific presenter logic in `Desktop*Presenters.kt`; `DesktopPlaybackPresenters.kt` now owns command preview, runtime config validation, remote playback bridging, playback-source construction, `MpvProcessPlayer` creation, and launch status mapping instead of keeping that logic in `MiruPlayDesktopComposeApp.kt`. The behavior is tested in shared modules and desktop presenter tests and can be reused by Android TV or future KMP surfaces. | Covered for the extracted helpers and mpv launch/config presenter logic; more desktop UI state can still be split into shared use cases later |
| Windows playback uses mpv | `:player-mpv` builds mpv commands, starts an external process, supports IPC pause/seek/quit/time-position queries, and `desktop-app` launches `MpvProcessPlayer` through `DesktopPlaybackLauncher`; latest `:player-mpv:test :desktop-app:test` passed with 15 desktop tests including launch preparation, remote bridge preservation, runtime validation errors, and launch status/session output. | Covered for external process mode |
| Real mpv executable can launch | mpv_PlayKit `20260510` assets were downloaded into `.gradle/mpv-playkit-20260510`; `runtime/mpv` was prepared from `mpv-lazy-20260510.exe` plus the `mpv-lazy-20260510-vsNV.7z.001` overlay, then the default `:desktop-app:smokeMpvRuntime -PrequireMpvRuntime=true` gate passed with `mpv v0.41.0-615-g7b057f66f` and required RIFE `NVIDIA, DIRECTML`. | Covered |
| Bundled RIFE runtime is supported | Runtime layout expects `portable_config/vs/MEMC_RIFE_NV.vpy` and `MEMC_RIFE_DML.vpy` for the default release gate; app can also select the optional Standard script when present. The verifier blocks launch when a selected script is missing. The current local manifest records the standard `.exe` base plus `vsNV` overlay and the default NVIDIA/DirectML requirements. | Covered structurally |
| Real RIFE payload works | DirectML RIFE smoke previously passed through `tools/smoke-mpv-rife.ps1 -Backend DIRECTML` with `runtime/mpv/mpv.exe`, `MEMC_RIFE_DML.vpy`, and a generated two-frame 1440x810 Y4M clip: mpv initialized VapourSynth and exited with playback success. The `-Backend ALL -AllowFailures` matrix mode reports all three backends in one run; `-ReportPath` can persist the same run plus host diagnostics as JSON. On this host, RIFE playback is treated as non-blocking because the machine is not expected to run interpolation well. | Covered structurally; target hardware validation remains |
| Runtime preparation is repeatable | `tools/prepare-mpv-runtime.ps1` accepts extracted directories or `.7z/.7z.001`, supports `-OverlaySource` for patching a base runtime with a RIFE/VapourSynth payload, optionally validates SHA256 before extraction including `filename=sha256` lists for split payloads, validates required RIFE scripts, copies to `runtime/mpv`, and writes `runtime-manifest.json`; tested with fake base/overlay payloads and with real mpv_PlayKit `20260510` standard + `vsNV` assets. | Covered |
| Desktop distribution runtime copy is controllable | `desktop-app/build.gradle.kts` bundles exactly one runtime source: explicit `-PmpvRuntimeSource` when present, otherwise repository `runtime/mpv`. `bundleMpvRuntime` defaults to `true` for self-contained artifacts, while `-PbundleMpvRuntime=false` skips the large runtime copy for UI-only development installs; verified with `:desktop-app:installDist -PbundleMpvRuntime=false` and `:desktop-app:smokePackagedMpvRuntime -PmpvRuntimeSource=runtime\mpv -PrequireMpvRuntime=true -PrequiredRifeBackends=NVIDIA,DIRECTML`, which builds `desktop-app.zip`, smokes `mpv.exe --version`, and checks the packaged runtime entries. | Covered |
| Runtime provenance is visible | `MpvRuntimeVerifier` reads `runtime-manifest.json`; `Check runtime` dialog shows source, verified time, required RIFE backends, and files. | Covered |
| Local/WebDAV/SMB sources are available on desktop | `:media-source-desktop` implements local, WebDAV, and SMB sources. Compose Desktop exposes local source add/scan/search, saved-source switching, current-source index clearing/removal, WebDAV/SMB source open, directory browsing, current-source scanning, loopback bridge playback for remote media, and selected-media details for local index entries, remote browser entries, and recent playback records. `tools/smoke-desktop-local-source-ui.ps1` starts the Windows GUI with an isolated store, adds a local source from either a generated fixture or `-LibraryRoot`, scans it, validates the persisted source/index JSON, records scan/search/details/player screenshots, verifies poster-wall search can filter to a target anime, verifies poster click opens Details, and verifies Player handoff. `tools/smoke-desktop-webdav-source-ui.ps1` starts a loopback Basic Auth WebDAV fixture, adds the WebDAV source through the TV-style remote source card, verifies authorized PROPFIND/GET traffic, browses the remote directory, scans sibling NFO metadata into the desktop index, returns to the TV-style poster wall, opens Details from the remote poster, and verifies Player receives the remote media path. `tools/smoke-desktop-smb-source-ui.ps1` creates a timestamped fixture only under the approved SMB test directory, opens the authenticated SMB URL through the TV-style remote source card, scans one NFO-backed `Fixture SMB` video, verifies Details/Player handoff, deletes the fixture directory, defaults to the approved `test-user` smoke credentials when env vars are absent, and redacts username/password/domain from the stored evidence. `DesktopSmbLiveShareTest` is opt-in and passed against `smb://smb.example.test/share/临时文件/测试` when provided live credentials. `DesktopRemotePlaybackSecurityTest` verifies WebDAV and SMB playback are converted to loopback bridge URLs before mpv command construction and that source host/user/password/domain fragments are absent from the command. | Covered for local/WebDAV/SMB GUI flows and remote command credential isolation |
| Desktop library indexing exists | `:scanner-desktop` scans desktop sources, filters videos, infers metadata, reads sibling `.nfo` and `tvshow.nfo`, and rebuilds `repository-desktop` index. The local-source GUI smoke validates that a generated NFO fixture appears in the desktop index as `Fixture Frieren` episode 2, verifies repository search can isolate that anime, and verifies poster-wall selection/detail/player handoff. The WebDAV GUI smoke validates that a remote NFO fixture appears as `Fixture WebDAV` episode 2 and that scanning returns the Library first screen to a poster wall. The SMB GUI smoke validates that a real authenticated SMB fixture appears as `Fixture SMB` episode 2 and that scanning returns to the poster wall. `tools/smoke-desktop-source-management-ui.ps1` proves saved-source keyboard switching, Clear index, and Remove source update the isolated desktop store: the active source index is cleared, the keyboard-selected source is removed, and the untouched second source remains. The local smoke also passes against the real anime library at `D:\Software\dufs`, with `build/desktop-local-source-ui/run-20260520-080904/local-source-scanned.png` showing the Library first screen as a poster wall. | Covered |
| Desktop media metadata can be inspected | Compose Desktop has a TV-style Details hero backed by selected index media plus a `Media details` panel backed by `DesktopMediaDetailRows`, showing active source, indexed title/type/anime/season/episode/title, Bangumi metadata source/id/title, indexed size/modified time, browser item kind/MIME/size/modified time/path, plot when present, and recent playback resume/play count/last watched. Unit tests cover the row model and hero title/subtitle helpers, and the local-source GUI smoke now opens the Details route by selecting a scanned local poster. | Covered |
| Bangumi metadata is available on desktop | `:scraper-desktop` provides JVM Bangumi search/details/episodes; `:scraper-desktop:smokeBangumiLive` verifies the live search/details/episodes path and writes token-free JSON evidence; Compose Desktop has `Use selected`, `Search`, `Apply match`, and `Clear metadata` to write or clear selected match source/id/title on one index entry. `tools/smoke-desktop-bangumi-metadata-ui.ps1` now proves the real Details screen flow with store assertions and screenshots for ready/search/applied/cleared states. | Covered for live scraper path and GUI single-item apply/clear flow |
| Batch metadata workflows | Compose Desktop has `Batch preview`, `Apply batch`, `Accept review`, and `Undo batch`; batch plans split matches into ready/review/conflict, render the preview as a selectable review queue, retain alternate Bangumi candidates per query, let the user switch the selected candidate before applying or manually accepting a reviewed match, skip existing-metadata conflicts instead of overwriting them, apply only high-confidence ready updates automatically, and persist the last rollback list in the desktop JSON store so undo survives restart. | Covered |
| Metadata batch planning is reusable | `repository-api/src/main/kotlin/com/miruplay/tv/repository/MetadataBatchPlanner.kt` owns query derivation, ready/review/conflict splitting, conflict isolation, preview text, and plan summaries; `desktop-app` delegates through thin compatibility wrappers. `:repository-api:test :desktop-app:test --rerun` passed after extraction. | Covered for planner logic |
| Index and subtitle display helpers are reusable | `repository-api/src/main/kotlin/com/miruplay/tv/repository/MediaIndexDisplay.kt` owns `MediaIndexEntry` display names/lines/browser conversion. `core/model/src/main/kotlin/com/miruplay/tv/model/SubtitleTracks.kt` owns external subtitle path parsing and format detection. Shared module tests cover both. | Covered for extracted helpers |
| Playback progress continues on desktop | Compose Desktop saves progress when mpv launches, polls mpv `time-pos` every 10 seconds while playback is active, saves a session-estimated position immediately on Stop so process shutdown is not blocked by IPC, shows continue-watching records, can clear a selected recent item, provides TV-style Play/Pause/-10s/+30s/Stop controls in the playback stage with left/right focus movement and Enter activation, and stores original remote path rather than loopback URL. `tools/smoke-desktop-mpv-launch-ui.ps1` now proves the GUI can launch a generated local sample through mpv, exercise Pause/-10s/+30s/Stop, confirm the mpv process exits, and record `mpv-launch-ready.png`, `mpv-launched.png`, `mpv-keyboard-controls-used.png`, and `mpv-stopped.png` evidence with a 30s saved position. | Covered |
| True mpv position tracking | `MpvIpcClient` can request `get_property time-pos`, `MpvProcessPlayer.queryTimePositionMs()` exposes it, and Compose Desktop uses `syncPlaybackProgressFromMpv` to re-anchor the session and persist observed mpv positions during playback and at stop. Unit tests cover session re-anchoring and sync helper success/null/error behavior. | Covered for Compose Desktop |
| Cloud/RSS sync parity on desktop | `repository-desktop` persists `CloudDriveAutomationConfig`, RSS subscriptions, processed items, download tasks, and file-backed CloudDrive/Bangumi credentials in the JSON store. `:cloud-drive-desktop` provides a JVM CloudDrive2 gRPC client, `:sync-engine-desktop` provides a JVM RSS runner plus scheduler with feed fetch, filtering, processed-item dedupe, CloudDrive offline submission, torrent-to-magnet staging, organizer moves, `lastRunAt` updates, `runIfDue`, scheduler state flow, and a desktop post-sync source rescan hook. Compose Desktop now exposes CloudDrive2/RSS config, token/password save/clear, `Login`, `Verify token`, subscription add/update/delete, `Run sync now`, `Start scheduler`, and `Stop scheduler` in TV-style overview/config/subscription/scheduler cards with compact endpoint/path/subscription previews and keyboard-moving RSS subscription rows. `:cloud-drive-desktop:test` starts a real loopback gRPC server against the generated CloudDrive2 stub and covers login, API token info, bearer auth listing, and raw-token fallback. `:sync-engine-desktop:test` now also runs `DesktopCloudDriveRssAutomationEngine` against a loopback CloudDrive2 gRPC server through the real `GrpcCloudDriveClient`, covering RSS offline submission, bearer metadata, processed item persistence, download-task persistence, and organizer list calls. `:cloud-drive-desktop:smokeCloudDrive2` is available for live endpoint/token/path QA without printing the token. `:sync-engine-desktop:smokeCloudDriveRssDryRun` verifies a real endpoint, token, inbox/library listing, RSS fetch/parse, filter matching, and would-submit counts without calling CloudDrive offline download APIs; `-PcloudDriveRssReportPath=...` writes a token-free JSON dry-run evidence report. `:sync-engine-desktop:smokeCloudDriveRssLiveSubmit` is now available as a separate explicit-confirmation task that submits a limited number of live RSS candidates and records submit counts plus post-submit inbox listing in a token-free JSON report. Live submit/organize/scheduler QA still needs to be executed against a real server before completion can be claimed. | Partial |
| Desktop UI maturity | Compose Desktop is now the default entry and follows the TV visual language for local library/source management, remote browser, saved-source switching, media details, Bangumi metadata, recents, playback, runtime, and settings/automation slices. The Library route now uses a TV-style full-width header and poster-wall primary surface as the first content after scan/load; source management uses a focused saved-source card with long-path compaction and unit-covered saved-source key movement; remote source setup uses WebDAV/SMB cards and a separate browser panel with keyboard row and parent navigation; Cloud/RSS settings uses TV-style overview/config/subscription/scheduler cards with keyboard-moving RSS subscription rows; Details opens from poster selection and starts with a TV-style hero before the metadata/Bangumi panels; Player hides the desktop rail and starts with a TV-style playback stage before advanced settings; Settings opens with a TV-style category rail, concrete source/playback/scan/metadata status cards, and Cloud/RSS cards. Screenshot QA is scripted via `tools/capture-desktop-ui.ps1` and passes with palette, text, size, diversity, and per-section distinctness checks. Latest Android TV fixture QA generated `build/android-tv-qa/run-20260520-112248/android-tv-library.png`, `android-tv-details.png`, `android-tv-player.png`, XML dumps, and `android-tv-smoke-report.json`; latest keyboard GUI smoke generated `build/desktop-keyboard-focus-ui/run-20260520-141716/keyboard-settings-sources.png`, `keyboard-settings-cloud.png`, `keyboard-settings-rss-first.png`, `keyboard-settings-rss-second.png`, `keyboard-nav-details.png`, and `keyboard-nav-player.png`; latest generated local-source smoke generated `build/desktop-local-source-ui/run-20260520-113934/local-source-poster-keyboard.png`, `local-source-details.png`, and `local-source-player.png`; latest source-management GUI smoke generated `build/desktop-source-management-ui/run-20260520-133927/source-management-saved-source-keyboard.png`, `source-management-scanned.png`, `source-management-controls.png`, `source-management-cleared.png`, and `source-management-removed.png`; latest WebDAV GUI smoke generated `build/desktop-webdav-source-ui/run-20260520-121024/webdav-source-keyboard-up.png`, `webdav-source-keyboard-browse.png`, `webdav-source-keyboard-select.png`, `webdav-source-poster-wall.png`, `webdav-source-details.png`, and `webdav-source-player.png`; latest SMB GUI smoke generated `build/desktop-smb-source-ui/run-20260520-090324/smb-source-poster-wall.png`, `smb-source-details.png`, and `smb-source-player.png`; latest real-library local-source smoke generated `build/desktop-local-source-ui/run-20260520-080904/local-source-scanned.png`, `local-source-details.png`, and `local-source-player.png` against `D:\Software\dufs`; latest mpv GUI launch smoke generated `build/desktop-mpv-launch-ui/run-20260520-115654/mpv-launch-ready.png`, `mpv-launched.png`, `mpv-keyboard-controls-used.png`, `mpv-stopped.png`, and `mpv-recent-keyboard-selected.png`; latest Settings screenshot QA refreshed `build/desktop-ui-qa/settings.png` and `build/desktop-ui-qa/settings-cloud.png`. | Partial |

Additional current UI evidence: `tools/smoke-desktop-keyboard-focus-ui.ps1`
captured `build/desktop-keyboard-focus-ui/run-20260520-141716/keyboard-settings-sources.png`,
`keyboard-settings-cloud.png`, `keyboard-settings-rss-first.png`,
`keyboard-settings-rss-second.png`, `keyboard-nav-details.png`, and
`keyboard-nav-player.png`, proving the real Windows GUI can move the Settings
category selection, Cloud/RSS RSS subscription row selection, and desktop route
rail by keyboard input.
The generated local-source GUI smoke also captured
`build/desktop-local-source-ui/run-20260520-113934/local-source-poster-keyboard.png`,
proving the Library poster wall can move selection with keyboard input before
opening Details, then send `Right`, `Left`, and `Enter` on the Details hero to
reach Player without mouse input.
The source-management GUI smoke captured
`build/desktop-source-management-ui/run-20260520-133927/source-management-saved-source-keyboard.png`,
proving the real Windows saved-source card can switch between saved local
sources by keyboard input before scanning the selected source.
The WebDAV GUI smoke captured
`build/desktop-webdav-source-ui/run-20260520-121024/webdav-source-keyboard-up.png`,
`webdav-source-keyboard-browse.png`, and `webdav-source-keyboard-select.png`,
proving remote-browser parent navigation, row movement, and Enter selection by
keyboard input without touching the SMB share.
The mpv GUI smoke captured
`build/desktop-mpv-launch-ui/run-20260520-115654/mpv-keyboard-controls-used.png`
and `mpv-recent-keyboard-selected.png`, proving Player transport movement while
mpv was running and Continue watching selection from Details after playback
stopped.
`DesktopBangumiNavigationTest` now covers Bangumi batch-match, candidate, and
search-result row movement without depending on the live Bangumi service.
`:scraper-desktop:smokeBangumiLive` now covers the live service path for
search, subject details, and regular episode listing while writing token-free
JSON evidence.
The Bangumi metadata GUI smoke captured
`build/desktop-bangumi-metadata-ui/run-20260520-135512/bangumi-details-ready.png`,
`bangumi-search-results.png`, `bangumi-metadata-applied.png`, and
`bangumi-metadata-cleared.png`, proving the real Windows Details panel can
search Bangumi, apply the selected metadata, and clear it again in an isolated
desktop store.

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

.\gradlew.bat :desktop-app:smokePackagedMpvRuntime `
  -PmpvRuntimeSource=runtime\mpv `
  -PrequireMpvRuntime=true `
  -PrequiredRifeBackends=NVIDIA,DIRECTML

.\gradlew.bat :repository-desktop:test :desktop-app:test :data:compileDebugKotlin :scanner:test

.\gradlew.bat :scraper-desktop:test :scraper-desktop:smokeBangumiLive `
  -PbangumiSmokeReportPath=build\bangumi-smoke\live-report.json

.\gradlew.bat :sync-engine-desktop:test :repository-desktop:test :player-mpv:test :desktop-app:test :desktop-app:installDist :app:assembleDebug

.\gradlew.bat checkDesktopComposeOnly `
  checkDesktopPresenterSeparation `
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

.\gradlew.bat checkDesktopComposeOnly checkUiPaletteDrift :desktop-app:installDist -PbundleMpvRuntime=false

.\tools\smoke-desktop-source-management-ui.ps1

.\tools\capture-desktop-ui.ps1

.\tools\smoke-desktop-keyboard-focus-ui.ps1

.\gradlew.bat :cloud-drive-desktop:test :sync-engine-desktop:test :desktop-app:test

.\tools\smoke-desktop-webdav-source-ui.ps1

.\tools\smoke-desktop-smb-source-ui.ps1

.\gradlew.bat :app:assembleDebug

.\tools\smoke-android-tv-ui.ps1 -DeviceId 10.137.32.118:5555

.\gradlew.bat checkDesktopComposeOnly

.\gradlew.bat :ui-tv:compileDebugKotlin :desktop-app:test --rerun

.\gradlew.bat checkUiPaletteDrift

.\tools\capture-desktop-ui.ps1

.\tools\smoke-desktop-local-source-ui.ps1

.\tools\smoke-desktop-local-source-ui.ps1 -LibraryRoot 'D:\Software\dufs'

.\tools\smoke-desktop-smb-source-ui.ps1

.\tools\smoke-android-tv-ui.ps1

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
```

## Highest-Risk Remaining Work

1. Continue narrowing TV parity gaps in less-traveled keyboard/DPAD focus paths outside the now-covered Settings category menu, Cloud/RSS subscription rows, desktop route rail, Library poster wall, Details hero actions, Continue watching recents, remote browser list including parent navigation, Player transport controls, saved-source card movement, Bangumi metadata lists, and Bangumi apply/clear flow.
2. Validate RIFE on target Windows hardware that is expected to support
   interpolation, and decide whether the optional Standard backend should ship
   an additional `rife` plugin.
3. Run live CloudDrive2 end-to-end QA for real offline submission, torrent
   staging, organization, scheduler behavior over real time, and source rescan.
   Token/path/RSS parsing can now be checked first with the dry-run smoke task.
