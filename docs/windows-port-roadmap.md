# MiruPlay Windows Port Roadmap

This document is the working checklist for finishing the Windows desktop port
without losing Android TV behavior. Keep it current as work lands.

## Definition Of Done

The port is complete only when all of these are proven by current evidence:

| Area | Required end state | Proof required |
|---|---|---|
| Android TV | Existing Android TV app still builds and keeps Compose/Compose TV UI. | `:app:assembleDebug`, TV-device screenshot/smoke run for core library/detail/player flows. |
| Windows entry | Windows launches a Compose Desktop app, not Swing, with TV-like navigation and layout. | `:desktop-app:installDist`, `tools/capture-desktop-ui.ps1`, `checkDesktopComposeOnly`, `checkUiPaletteDrift`. |
| Media sources | Windows can manage and browse Local, WebDAV, and SMB sources without Android-only APIs. | `:media-source-desktop:test`, GUI source add/open/browse smoke, remote stream bridge smoke. |
| Library/index | Windows can scan, search, inspect details, clear source index, and delete sources. | `:scanner-desktop:test`, `:repository-desktop:test`, GUI library/detail smoke. |
| Metadata | Windows can search/apply/clear Bangumi metadata and run batch review/apply/undo. | `:scraper-desktop:test`, `:scraper-desktop:smokeBangumiLive`, `:repository-api:test`, `:desktop-app:test`, `tools/smoke-desktop-bangumi-metadata-ui.ps1`. |
| Playback | Windows plays through mpv, supports RIFE toggle/backend selection, IPC pause/seek/stop, progress persistence, and remote playback through a credential-isolating loopback bridge. | `:player-mpv:test`, `:desktop-app:test`, mpv launch smoke with a local sample file, remote playback command security test. |
| mpv/RIFE runtime | Windows distribution can bundle or locate a verified mpv runtime with RIFE-capable scripts. | `:desktop-app:smokeMpvRuntime`, `:desktop-app:smokePackagedMpvRuntime`, `:desktop-app:smokeNativeAppImageRuntime`, `tools/smoke-mpv-rife.ps1`, runtime manifest evidence. |
| Cloud/RSS | Windows can configure CloudDrive2/RSS, dry-run safely, and run confirmed live submit/organize flows. | `:cloud-drive-desktop:test`, `:sync-engine-desktop:test`, dry-run report, explicit live QA report. |
| Release | CI and local release gates cover Android build, desktop tests, runtime checks, and screenshot QA. | CI green, local verification command log, packaged artifact smoke. |

## Current Status

| Area | Status | Notes |
|---|---|---|
| Android TV build | Covered for debug build and DPAD Library/detail/player/settings smoke | Latest local `:app:assembleDebug` passed. `tools/smoke-android-tv-ui.ps1` installs the debug APK on emulator `<android-tv-device-id>`, generates a playable fixture library, scans it through the TV UI, confirms Library content requests poster focus, drives DPAD Right/Left then Center from the poster surface into Details, drives DPAD Down from the Details play action to focus the first episode row, drives DPAD Center from that focused episode row into Player, verifies Android Back returns from Player to Details and from Details to the poster-focused Library wall, drives DPAD Up/Right/Center from that returned poster wall into Settings, drives DPAD Down/Center inside Settings to the media-source panel, verifies DPAD Right focuses the auto-added source card, Left returns to the media-source menu, Center opens the edit-source form without losing card focus, Right focuses the source delete button, Center removes the source while keeping the media-source empty state visible, then continues DPAD Down through Playback, CloudDrive, Scan, and Metadata settings with matching menu focus, and records `build/android-tv-qa/run-20260520-190519/android-tv-library.png`, `android-tv-library-dpad-poster.png`, `android-tv-details.png`, `android-tv-details-episode-focus.png`, `android-tv-player.png`, `android-tv-library-return.png`, `android-tv-settings.png`, `android-tv-settings-sources.png`, `android-tv-settings-source-card-focus.png`, `android-tv-settings-source-edit.png`, `android-tv-settings-source-delete-focus.png`, `android-tv-settings-source-deleted.png`, `android-tv-settings-playback.png`, `android-tv-settings-cloud-drive.png`, `android-tv-settings-scan.png`, `android-tv-settings-metadata.png`, XML dumps, and a JSON report. |
| Compose Desktop entry | Usable foundation | Swing production shell removed; screenshot QA exists for first screens. Latest Library UI now opens scanned libraries with the 6-column poster wall as the first media surface under the TV-style `探索` header, and the Library header now matches the Android TV action surface by exposing only `扫描` and `设置` instead of desktop-only Details/Player shortcuts. Highest-heat/recent rows and search/source controls sit below the wall; saved indexes are restored on startup/source switch; poster selection routes directly into a TV-style Details hero with stat pills for indexed episode/season/metadata context. Source management now uses a focused TV-style saved-source card that keeps source name/type visible with TV-facing labels and compacts long local/SMB/WebDAV paths from the middle, while local source fields and actions have unit-covered focus instead of panel-wide source switching. The latest source-management GUI smoke preloads two isolated local sources, uses keyboard `Up` on the saved-source card to switch back to the first source, proves the scan uses the keyboard-selected source id, and verifies scan/clear/remove state updates plus localized controls and localized scan status copy in a real window. Remote WebDAV/SMB setup is split into TV-style source cards with compact endpoint previews and a separate remote-browser panel; remote source edit fields plus open/scan actions now have unit-covered focus matching WebDAV fields/actions above the SMB field/action rows plus Right-to-browser and browser-Left-to-editor bridge movement, and GUI smokes verify deep local paths, loopback WebDAV, and real SMB flows from add/open through scan and playback handoff. Android TV smoke confirms the TV baseline is a media-first Library with poster focus, Details hero, episode shelf, full-screen playback overlay, and Back returning Player -> Details -> Library; desktop deliberately approximates that inside a Windows window with a rail-free playback stage, TV-facing mpv/RIFE settings labels and status copy below, TV-facing `探索`/`详情`/`播放`/`设置` route chrome plus `MiruPlay 桌面版` native window title, and `Esc` plus TV remote/navigation Back aliases for Player -> Details -> Library/Settings -> Library. Remaining mpv launch/config preparation now lives in `DesktopPlaybackPresenters.kt`; Settings now opens with a TV-style section menu, concrete source/playback/scan/metadata cards, and a Cloud/RSS page made of overview, CloudDrive2, path, subscription, and scheduler cards. The Bangumi metadata panel now uses TV-facing Chinese action labels, section labels, empty state, batch status chips, and status copy while preserving the existing keyboard action grid. The Settings category menu, Settings summary quick-action rows, desktop route rail, Library header action row, Library poster wall, Library highest-heat/recent poster shelves, Library search row, Details hero actions, Details episode shelf, Continue watching recents/actions, remote browser list, remote source fields/actions, Player transport controls, playback-setting controls, and runtime controls, saved-source card, local/remote source fields and actions, Cloud/RSS credential/sync/RSS edit fields, action grids, toggles, RSS subscription rows, scheduler Start/Stop row, and Bangumi match lists/action grid now support keyboard/DPAD-style navigation; TV action buttons, desktop selectable rows, saved-source picker, Settings category rows, route-rail rows, switches/toggles, the RIFE backend picker, and custom activation handlers now share `Enter`/numpad Enter/DPAD Center confirm semantics, with selectable rows also owning a shared navigation fallback for row-like Cloud/RSS subscription, CloudDrive directory, Library empty-media, remote-browser, Bangumi list, Details episode/recent, and media-detail movement; GUI smokes prove Sources -> CloudDrive, CloudDrive RSS subscription row Down, scheduler Start -> Stop by Down/Enter/Right/Enter, Details -> Player -> Esc -> Details -> Esc -> Library, poster-wall Right -> Enter, Details hero Right -> Left -> Enter, Details hero Down -> episode shelf, Details episode shelf Up -> hero, Details episode shelf Down -> Bangumi -> Up -> episode shelf, Bangumi action grid Right/Right into search results, Left back to Apply, then Enter for apply plus Right/Enter for clear, Details hero Down -> Continue watching Enter, Details hero Down -> Bangumi Use selected when no recents exist, WebDAV remote-browser Up -> parent plus Down -> Enter, saved-source card Up, and Player transport Enter/Left/Right actions by key input; unit tests cover Library header action parity, header action-row focus and header-to-content bridging, route Back hierarchy and Back aliases, shared confirm/toggle/picker/selectable-row key semantics, saved-source picker confirm/navigation key semantics, Settings category row confirm semantics, Settings summary quick-action row, saved-source card, local/remote source field and action movement, Cloud/RSS credential/sync/RSS edit field, action-grid, toggle-row, RSS subscription row movement, scheduler action-row movement, Library poster-wall short-row movement, Library highest-heat/recent shelf ordering, horizontal movement, poster-wall-to-shelf vertical movement, Library search row movement, search-to-source focus bridging, remote source editor-to-browser and browser-to-editor focus bridging, Bangumi match/candidate/result list movement, Bangumi action-grid/list handoff movement and top-action Up exit, Details hero action movement, Details hero stat labels, Details hero Down fallback targeting, Details episode shelf grouping/season filtering, Details episode shelf boundary exits, Continue watching row movement plus `刷新`/`清除条目` action-pair movement, Player-stage Up/Down focus between `返回详情` and transport controls, Player Stage-to-settings-to-runtime vertical focus bridging, Player playback-setting field/toggle/backend row movement, and Player runtime form/check movement back to settings. |
| Shared UI palette and labels | Covered structurally | `:ui-design` owns shared palette; drift check exists. `core:model` now also owns media-source type labels, default names, generic fallback names, source hints, location field labels, source display/status labels, shared source connection field helpers, shared settings section titles/descriptions/orderings, shared WebUI summary/status tiles, and shared Cloud/RSS run-summary/status wording plus legacy-status fallback text used directly by Android TV settings and Windows source/settings/Cloud/RSS UI, without a desktop-only status/source-label forwarding layer in `:sync-engine-shared`. |
| Shared media, NFO, and scraper contracts | Covered structurally | `:media-source-api` owns the platform-neutral `MediaSource`/`MediaSourceFactory` contract and range-capable stream API; Android implementation remains in `:media-source`, while Windows uses `:media-source-desktop`. `:metadata-core` owns NFO parser/writer logic shared by Android metadata/scanner and Windows scanner-desktop. `:scraper-core` owns the platform-neutral `MetadataScraper` contract plus shared Bangumi API request, paging, collection update, JSON mapping, alias-search, and preferred-candidate promotion behavior; Android and Windows wrappers now implement the same scraper contract and provide only token/User-Agent/query-normalization differences. `:sync-engine-shared` now owns Bangumi metadata refresh/cache merge behavior plus index-entry-to-local-episode/cache-id mapping, so Android Detail rescrape and Windows apply/sync cache preparation reuse the same details/episodes fetch, local episode merge, cache id rewrite, and error handling paths. `core:model` owns Episode ordering, season grouping/selection/filtering, distinct episode counts, and Detail continue-play target/label selection; `:repository-api` also owns media-index replacement, selected-entry preservation helpers, poster grouping/titles, same-anime merge keys, detail episode ordering/selection, `PlaybackPreferencesRepository`, `ScanPreferencesRepository`, and `buildNextPlaybackSource`; Android TV `PlayerViewModel`/`SettingsViewModel` and Windows playback/settings now share playback-end preference access, scan auto-run/interval/last-run/same-anime merge preferences, reusable index presentation rules, plus next-episode lookup/order/resume/completed-progress reset while supplying only their platform-specific playable URI mapping. Android `metadata`, `scanner`, `sync-engine`, `web-control`, `scraper`, and `player-core` now depend on shared API modules or repository interfaces instead of leaking Android data/media-source implementations through business modules, and `:sync-engine:test` locks NFO resume positions as milliseconds. |
| Local/WebDAV/SMB desktop sources | Implemented | Local source GUI smoke now covers generated fixture and a real local library path; WebDAV GUI smoke covers a loopback Basic Auth fixture from add/open/browse through scan with the TV-style remote source card layout; SMB GUI smoke covers a real authenticated share fixture under the approved `临时文件\测试` directory, defaults to the approved `ynsz` smoke credentials, and redacts credentials from stored evidence. |
| Library/index/details | Implemented foundation | Local scan/index, WebDAV scan/index, SMB scan/index, clear-index/remove-source, long-path saved-source display, remote endpoint/path preview, TV-style poster-wall selection, Details hero with stat pills, Details episode shelf, and player handoff GUI smoke now passes for generated fixtures; latest local smoke captures `build/desktop-local-source-ui/run-20260520-162054/local-source-details.png`, `local-source-details-episodes.png`, `local-source-details-episode-back-to-hero.png`, `local-source-details-episode-selected.png`, `local-source-details-episode-to-bangumi.png`, `local-source-details-bangumi-back-to-episode.png`, and `local-source-player.png` after moving from hero into the episode shelf, returning to the hero with `Up`, re-entering the shelf, selecting Frieren episode 2, moving down to Bangumi, returning to the episode shelf with `Up`, and handing that path to Player; local smoke also passes against `D:\Software\dufs` with the scanned Library opening directly on the poster wall. |
| Bangumi metadata | Implemented foundation | Unit coverage exists for desktop navigation and offline scraper parsing. `:scraper-desktop:smokeBangumiLive` now verifies live Bangumi search/details/episodes and writes a token-free JSON report. The desktop Bangumi wrapper now also normalizes traditional Chinese queries before posting, and Android Detail rescrape, Windows single-item search, and Windows batch preview now reuse `:scraper-core` preferred-candidate search so weak direct matches can be promoted by stronger alias/title/id candidates with the same confidence threshold. Android Detail rescrape, Windows single-item apply, and Windows sync cache preparation now also reuse `:sync-engine-shared` `BangumiMetadataRefreshCore` for fetching details/episodes, checking whether cached Bangumi episode ids are already present, and merging remote Bangumi episode metadata onto local indexed episodes; Windows local episode/cache-id shaping is now shared too instead of living in the Compose presenter layer. The Windows desktop app now hands search result promotion to `DesktopBangumiScraper.searchPreferredResults(...)` instead of rebuilding the alias merge in the UI layer, and it now feeds title/query/id candidates rather than a single query stem. `tools/smoke-desktop-bangumi-metadata-ui.ps1` opens the real Windows Details Bangumi panel, sends Details hero `Down` into the episode shelf, sends `Down` again to focus Bangumi `Use selected`, drives the action grid with keyboard `Right` to Search, `Right` into search results, `Left` back to Apply match, then `Right` to Clear metadata, verifies the isolated JSON store is written then cleared, and captures `build/desktop-bangumi-metadata-ui/run-20260520-213906/bangumi-details-ready.png`, `bangumi-focus-bangumi.png`, `bangumi-search-results.png`, `bangumi-metadata-applied.png`, and `bangumi-metadata-cleared.png`. |
| mpv playback | Implemented foundation | Desktop Player keeps mpv/RIFE controls behind a TV-like playback stage with localized `媒体 URI 或文件路径`/`起播秒数`/`外挂字幕路径` labels, localized mpv status chips, localized runtime verifier output, and localized diagnostic panel chrome; Player focus now moves vertically between `返回详情` and the primary transport with TV-style Up/Down keys while preserving left/right transport edge stops, and continues downward from the stage into playback settings, through media/start/subtitle fields, across the fullscreen/keep-open/RIFE/backend row, then into the runtime card; runtime `Up` returns to the playback-setting row. The Player `全屏` toggle now also drives the Compose Desktop native window placement only while the Player route is active, and restores the prior floating/maximized placement when the route leaves Player or fullscreen is disabled; unit coverage locks that route-gated decision. `tools/smoke-desktop-mpv-launch-ui.ps1` now generates a local Y4M sample, launches it through the Windows GUI, confirms an `mpv.exe` child process, exercises Pause/-10s/+30s/Stop by keyboard focus, verifies progress persistence, and captures settings-focus/runtime-focus/launched/keyboard-control/stopped Player screens. |
| RIFE runtime | Partial | Runtime structure and scripts are tracked; desktop launch keeps RIFE opt-in by default and missing mpv/RIFE launch errors now render TV-facing Chinese guidance for checking the runtime, preparing a backend, or turning RIFE off. The runtime verifier now also treats `runtime-manifest.json` as consistency evidence: declared relative files/directories must exist, required RIFE backends must be known, and invalid absolute/`..` entries keep the runtime incomplete while the desktop runtime panel shows the new guidance in Chinese. Gradle runtime gates now apply the same manifest-evidence rule to the source runtime payload, packaged `distZip`, and native app-image runtime content. `tools/smoke-mpv-rife.ps1 -ReportPath ...` now embeds runtime manifest evidence plus per-backend script/log paths in target-host RIFE JSON reports, `tools/assert-mpv-rife-report.ps1 -RequireRuntimeManifest` validates that the manifest is present, problem-free, declares the required backend(s), and points to existing runtime/mpv/config/clip/manifest files plus existing script/log files for passing backends; `tools/verify-windows-port.ps1 -Rife` runs that stricter assertion after generating the report. Local machine RIFE playback is non-blocking because this host is not expected to run interpolation well. Backend matrix remains target-host validation. |
| Cloud/RSS | Partial | Loopback tests exist and the desktop Settings UI now exposes Cloud/RSS as TV-style Chinese overview/config/subscription/scheduler cards, including localized labels, empty states, previews, and common status copy. The shared `core:model` now owns the canonical Cloud/RSS status helpers, so Android TV and Windows both reuse the same Chinese wording for CloudDrive credentials, token verification, scheduler state, RSS subscription save/select/delete, and sync completion instead of each module keeping its own copy. Cloud/RSS action button groups now have deterministic `Left`/`Right` row movement plus `Up`/`Down` movement through credentials, login/verify, scan-source, sync, RSS, subscription rows, and scheduler controls; CloudDrive2 credential fields, sync-path fields, and RSS edit fields now move through their form rows and bridge into the nearest save/clear or enable/proxy/RSS controls, while enable/proxy/RSS toggles bridge into their neighboring action rows with unit-covered TV-style focus movement; RSS subscription rows now page in six-row TV-style windows, keep selected restored subscriptions visible, show a Chinese range summary, and still support keyboard `Up`/`Down` movement across the full subscription list into scheduler Start/Stop; the scheduler Start/Stop row now has unit-covered left/right movement and TV-style up/down edge stops, and the Settings keyboard smoke captures RSS row selection plus scheduler start/stop keyboard activation in `build/desktop-keyboard-focus-ui/run-20260521-175654`. Windows Settings now matches the Android TV CloudDrive path picker more closely: inbox/library path fields include keyboard-reachable `选择目录` actions, the desktop panel verifies the API token, scopes browsing to the token root, lists visible folders only, supports parent/current-folder selection, and unit tests cover root clamping, hidden/file filtering, directory action-row movement, directory-row/empty-state movement, and path-action focus bridges. Directory-browser actions now move `Left`/`Right` across `使用当前目录`/`返回上级`/`关闭`, `Down` into the first folder row when entries exist or into a focusable loading/empty row when they do not, and the first folder or empty row moves `Up` back to `使用当前目录`. The desktop CloudDrive directory browser state flow is now extracted from Compose UI into a fake-client-covered helper with explicit tests for token-root scoping, outside-path clamping, visible-directory filtering, listing-error propagation, and selection normalization. The smoke script now writes its isolated JSON store as UTF-8 without BOM so Windows PowerShell launches preload the same fixture state. Post-sync source rescan is now extracted into a reusable desktop helper and covered by an integration test that refreshes a linked local source index after fixture contents change. The base CloudDrive2 live smoke now writes a token-free JSON report for endpoint/path, token permissions, listing counts, and preview items, and `tools/assert-cloud-drive-report.ps1` plus `tools/verify-windows-port.ps1 -CloudDrive` can turn that report into an explicit live QA assertion. The desktop RSS live smoke can optionally run the real organizer behind `cloudDriveRssOrganize=true` plus an explicit move confirmation string and records moved count plus post-organize folder counts in the token-free JSON report; persisted RSS evidence now redacts RSS/submission URLs and raw GUIDs while retaining scheme/host/SHA-256 fingerprints so private tracker passkeys are not written into QA artifacts or console logs. `tools/assert-cloud-rss-report.ps1` rejects raw `submissionUrl`/`guid` fields and passkey-like text while validating the redacted evidence shape. `:sync-engine-desktop:smokeCloudDriveRssScheduler` now runs in the default local/CI safe gate, drives the desktop scheduler loop over real elapsed time, proves duplicate start prevention, due-run state, stop state, and writes token-free timing evidence validated by `tools/assert-cloud-rss-scheduler-report.ps1`. Real CloudDrive2 dry-run/live evidence still open. |
| WebUI desktop access | Partial | Windows Settings now includes the same first-class `WebUI` category as Android TV and drives a real JVM listener from the persisted shared `WebControlAccessManager` enable/token state. A new pure JVM `:web-control-core` module owns the shared NanoHTTPD routing, auth/cookie handling, API envelope/DTOs, static serving, UTF-8 request helpers, WebUI server-info/IP DTO mapping, WebUI source request shaping/password redaction, WebUI source-test response wording, shared WebUI Result error handling, WebUI local-directory DTO mapping, WebUI CloudDrive/RSS request validation, automation DTO mapping, and response shaping, WebUI CloudDrive directory browse flow and DTO mapping over the shared directory-browser state, WebUI progress/detail DTO mapping, WebUI library DTO shaping/search filtering, WebUI play-request start-position selection, and shared WebUI playback status DTO mapping and command normalization/default seek rules; Android `WebControlServer` is now a thin asset-backed wrapper over the same server and uses the same server-info/source/source-test/Result error/local-directory/progress/detail/CloudDrive automation/CloudDrive/RSS/directory/library/search/play helpers as Windows WebUI. `:desktop-app` bundles the existing Android WebUI assets and starts/stops `DesktopWebControlServer` with the Settings toggle, serving token-protected `/api/info`, `/api/library`, `/api/anime/{id}`, `/api/sources`, `/api/sources/{id}/scan`, `/api/sources/scan-all`, `/api/cloud-drive`, `/api/cloud-drive/directories`, `/api/cloud-drive/login`, `/api/cloud-drive/token`, `/api/cloud-drive/run`, `/api/playback/status`, `/api/playback/play`, `/api/playback/command`, and the static WebUI shell from desktop repositories. Source/RSS/config writes, local/WebDAV/SMB source connectivity tests, source scans, CloudDrive token/login/directory browsing, CloudDrive/RSS manual run, and WebUI playback start/control are supported through the same desktop media-source, shared directory-browser, `DesktopCloudDriveRssAutomationEngine`, `DesktopPlaybackLauncher`, and mpv command-helper paths used by the Windows UI. WebUI library/detail responses now reuse the same `repository-api` media-index poster grouping and scan merge preference as the desktop poster wall, so externally matched seasons split or merge consistently across Windows UI and WebUI. WebUI playback keeps episode ids as progress/session keys while keeping real media paths for mpv/loopback streaming, and temporary remote source instances stay alive until playback stops or is replaced; `DesktopWebControlPlaybackBridge.kt` now owns the WebUI command status, media-source selection/ownership, and next-episode temporary-source inheritance so this logic is no longer buried in the Compose entry. `DesktopWebControlPlaybackBridgeTest` covers those extracted rules, while `DesktopWebControlServerTest` covers auth, static-cookie bootstrap, disabled state, source redaction, repository-backed library/detail data, shared poster merge preference for WebUI library/detail, local-source scanning through both single-source and scan-all HTTP endpoints, CloudDrive login/token verification, scoped directory browsing/filtering, WebUI-triggered CloudDrive/RSS run, and injected playback play/command HTTP handlers; latest focused WebUI gate `:web-control-core:test :web-control:compileDebugKotlin :desktop-app:test checkDesktopPresenterSeparation checkDesktopComposeOnly -PbundleMpvRuntime=false` passed in Gradle MCP build `b-105`, and `:app:assembleDebug -PbundleMpvRuntime=false` passed in `b-106`. |
| Release packaging | Partial | Lightweight install works; full bundled runtime `distZip` now has a packaged mpv/RIFE smoke gate, and the Windows app-image path has a JDK `jpackage --type app-image` gate that verifies the launcher, generated launcher config/classpath, bundled mpv/RIFE runtime, and a real `MiruPlay.exe` headless desktop-entry smoke report from the generated app image. CI now also has a `windows-latest` job that builds `:desktop-app:test :desktop-app:distZip -PwindowsPackageVersion=... -PbundleMpvRuntime=false` and uploads the resulting versioned Windows desktop ZIP; nightly main/master releases attach the same-version lightweight ZIP alongside the Android APK. The `packageWindowsAppImage`, `distZip`, `distTar`, and installer paths now all use the same `windowsPackageVersion` input instead of mixing a hard-coded app-image version with installer metadata. `:desktop-app:smokeWindowsInstaller -PrequireWindowsInstallerToolchain=true` remains the opt-in MSI/EXE gate: it preflights WiX, reuses the verified app image, emits installer SHA256/size/version/signing-mode JSON evidence, and can sign plus verify with explicit `signtool`/PFX inputs. `tools/assert-windows-installer-report.ps1` validates the installer evidence JSON against the generated MSI/EXE path, size, SHA256, type, app version, and expected signing mode, and `tools/verify-windows-port.ps1 -WindowsInstaller` runs that assertion after packaging. `tools/verify-windows-port.ps1` now provides a repeatable local gate with safe defaults, automatically selects JDK 21 on Windows when available, and keeps GUI, real-library, Android TV, SMB, mpv-runtime, native app-image, Windows installer, and RIFE checks opt-in while running the local Cloud/RSS scheduler smoke by default. GitHub Actions now runs the Android/desktop/shared JVM CI gate on `codex/**` push branches as well as mainline branches, while nightly/release publishing is explicitly limited to main/master scheduled or manual runs. Signed release installer artifact validation and target-host runtime validation remain open. |

Latest local shared-input update: `:ui-design` now owns `MiruPlayInputIntent`
for platform-neutral activation, Back/navigation-back, direction, and media
playback intents. Android TV and Windows keep only thin Compose `Key` adapters,
so DPAD Center/Enter/Space activation, TV/desktop Back aliases, and
play/pause/stop media keys cannot drift between platforms. The same shared
intent layer now also owns horizontal, vertical, and linear directional deltas;
the desktop route rail and saved-source picker consume those deltas through
intent-based key handlers instead of branching directly on Compose `Key`
values, and the Library header, local-source fields/action row, remote-source
fields/action row, and remote-browser rows/actions/empty state now expose
intent-level navigation contracts for the same reason. The Settings category
menu also steps sections from the shared intent layer instead of branching on
desktop key values.
The Library poster wall, highest-heat/recent shelves, and search row now follow
that contract as well, keeping the Windows media-first surface aligned with TV
DPAD direction semantics. The Details hero action row, episode-season selector,
episode rows, Continue watching action/record rows, episode/recents empty
states, and two-column media-details list now expose the same intent-level
focus contracts. The Windows Player stage transport,
playback-setting form/toggle row, playback-end action row, and runtime form now
also use intent-level navigation contracts, matching the shared playback-input
layer used by Android TV fullscreen playback. Cloud/RSS credential/path/RSS
fields, action rows, toggles, subscription rows, CloudDrive directory browser,
RSS picker, and Settings quick-action rows now also use intent-level navigation
contracts while retaining thin Compose `Key` adapters. The Bangumi action grid,
batch-match/candidate/search-result lists, list exits, and empty-results bridge
now use the same intent-level navigation helpers, leaving Compose `Key`
handling as an adapter instead of the metadata focus contract.
Verified with
`.\gradlew.bat :ui-design:test :ui-tv:test :desktop-app:test checkDesktopPresenterSeparation checkDesktopComposeOnly -PbundleMpvRuntime=false`.

## Work Plan

### Phase 1: Stabilize The Existing Windows App

- [x] Keep Windows UI on Compose Desktop only.
- [x] Add screenshot QA for Library, Details, Player, and Settings.
- [x] Share media-source connection field conventions across desktop modules.
- [x] Run the current Windows GUI screenshot smoke locally.
- [x] Add a fixture-driven Windows GUI smoke that creates/uses a local media source, scans it, verifies store state, and records screenshots.
- [x] Extend the local-source GUI smoke to support a documented real local library path.
- [x] Capture Android TV Library baseline from emulator `<android-tv-device-id>` into `build/android-tv-qa/library-baseline.png`.
- [x] Add Android TV smoke coverage for Library poster focus and DPAD poster -> Details episode row -> Player -> Back to Library poster wall -> Settings -> media sources -> source card -> menu return -> source edit form -> source delete button -> empty source state -> Playback/CloudDrive/Scan/Metadata category pages.
- [x] Rework the Windows Library first screen toward the Android TV Library: full-width `探索` header, `扫描`/`设置` actions, TV empty state, and poster wall after scan.
- [x] Make scanned Windows libraries open directly as a TV-style 6-column poster wall and restore saved index entries on startup/source switch.
- [x] Rework the Windows Details first screen toward Android TV: poster click opens Details directly, with a large backdrop/poster hero, title context, plot, Play, and Back-to-poster-wall actions.
- [x] Add TV-style stat pills to the Windows Details hero from indexed episode, season, and metadata context.
- [x] Add a TV-style Windows Details episode shelf grouped from the selected indexed anime, with season filtering and keyboard focus from the hero.
- [x] Localize Windows Details hero empty state, episode shelf subtitle, Continue watching controls, and media-detail chrome to TV-facing Chinese copy.
- [x] Add unit coverage for Details episode shelf multi-season selector keyboard/DPAD navigation.
- [x] Add unit coverage for Details episode shelf empty-state keyboard/DPAD focus bridging.
- [x] Add unit coverage for Details media-detail row/empty-state keyboard/DPAD navigation and bottom-panel focus bridging.
- [x] Keep every Windows media-detail row reachable by keyboard/DPAD pagination, not only the first visible six rows.
- [x] Rework the Windows Player first screen toward Android TV: rail-free playback stage, top return action, centered transport controls, bottom timeline/status chips, and TV-facing mpv/RIFE settings below.
- [x] Rework the Windows Settings first screen toward Android TV: left-side settings categories with focused rows, concrete source/playback/scan/metadata cards, and quick actions before Cloud/RSS.
- [x] Rework the Windows Cloud/RSS settings page into TV-style overview/config/subscription/scheduler cards.
- [x] Add Windows GUI smoke coverage for Settings category and desktop route rail keyboard/DPAD-style navigation.
- [x] Make Settings category and desktop route rail keyboard movement stop at TV-list edges instead of wrapping.
- [x] Add unit coverage for Settings summary quick-action row keyboard/DPAD navigation.
- [x] Bridge Windows Settings summary quick-action rows back to the selected category with keyboard/DPAD Up.
- [x] Add Windows GUI smoke coverage for TV Back-equivalent `Esc` route navigation.
- [x] Add unit coverage for TV remote/navigation Back aliases while keeping `Backspace` available for text editing.
- [x] Share desktop confirm-key handling so custom controls accept keyboard Enter, numpad Enter, and TV DPAD Center.
- [x] Extend shared confirm-key handling to desktop switches/toggles and the RIFE backend picker.
- [x] Extend shared confirm-key handling to Settings category rows.
- [x] Share desktop navigation-only key handling for TV-style field/action focus bridges.
- [x] Share desktop selectable-row confirm/navigation key handling for Cloud/RSS subscription and CloudDrive directory rows.
- [x] Share desktop selectable-row confirm/navigation key handling for Library empty, remote-browser, and Bangumi list rows.
- [x] Share desktop selectable-row confirm/navigation key handling for Details episode, recent-playback, and media-detail rows.
- [x] Add Windows GUI smoke coverage for Cloud/RSS RSS subscription row keyboard/DPAD navigation.
- [x] Add unit coverage for Cloud/RSS empty RSS subscription keyboard/DPAD focus bridging.
- [x] Add unit coverage for Cloud/RSS action-grid keyboard/DPAD navigation.
- [x] Add unit coverage for Cloud/RSS toggle-row keyboard/DPAD navigation.
- [x] Add unit coverage for Cloud/RSS sync-path field keyboard/DPAD navigation into enable/proxy toggles.
- [x] Add unit coverage for Cloud/RSS credential and RSS edit field keyboard/DPAD navigation into adjacent actions/toggles.
- [x] Keep every Windows Cloud/RSS RSS subscription row reachable by keyboard/DPAD pagination, not only the first visible six rows.
- [x] Add Windows GUI smoke coverage for Library poster-wall keyboard/DPAD navigation.
- [x] Add unit coverage for Library header action-row keyboard/DPAD navigation into content.
- [x] Add unit coverage for Library highest-heat and recent poster shelf keyboard/DPAD navigation.
- [x] Add unit coverage for Library poster-wall to highest-heat/recent shelf vertical keyboard/DPAD navigation.
- [x] Add unit coverage for Library search row keyboard/DPAD navigation into media and source panels.
- [x] Add unit coverage for Library empty media-state keyboard/DPAD focus bridging.
- [x] Add Windows GUI smoke coverage for Details hero action keyboard/DPAD navigation.
- [x] Add Windows GUI smoke coverage for Details episode shelf boundary keyboard/DPAD navigation.
- [x] Keep every Windows Details episode-shelf row reachable by keyboard/DPAD pagination, not only the first visible six rows.
- [x] Add Windows GUI smoke coverage for Continue watching recent-playback keyboard/DPAD navigation.
- [x] Add unit coverage for Continue watching refresh/clear action-pair keyboard/DPAD navigation.
- [x] Add unit coverage for Continue watching empty-state keyboard/DPAD focus bridging.
- [x] Keep every Windows Continue watching record reachable by keyboard/DPAD pagination, not only the first visible six rows.
- [x] Add Windows GUI smoke coverage for Details hero Down -> Bangumi metadata focus fallback.
- [x] Add Windows GUI smoke coverage for remote-browser row keyboard/DPAD navigation.
- [x] Add unit coverage for remote-browser empty-state keyboard/DPAD focus.
- [x] Add unit coverage for Bangumi match/candidate/result list keyboard/DPAD navigation.
- [x] Add unit coverage for Bangumi empty-result keyboard/DPAD focus bridging.
- [x] Add unit and Windows GUI smoke coverage for Bangumi action-grid keyboard/DPAD navigation.
- [x] Localize Windows Bangumi metadata controls, empty states, batch chips, and status copy to TV-facing Chinese.
- [x] Keep every Windows Bangumi batch-match, candidate, and search-result row reachable by keyboard/DPAD pagination, not only the first visible rows.
- [x] Rework the Windows source-management saved-source picker for TV-style focus and long path readability.
- [x] Localize Windows source-management controls and saved-source type labels to TV-facing Chinese copy.
- [x] Localize Windows Library/source-management status messages to TV-facing Chinese copy.
- [x] Localize Windows Cloud/RSS settings labels, previews, empty states, and status copy to TV-facing Chinese.
- [x] Share Cloud/RSS status and scheduler summary conventions across Android TV and Windows.
- [x] Keep every Windows CloudDrive directory picker folder reachable by keyboard/DPAD pagination, not only the first visible six rows.
- [x] Localize Windows route rail subtitle, playback poster placeholder, and Settings page references to TV-facing Chinese copy.
- [x] Add unit coverage for saved-source card keyboard/DPAD navigation.
- [x] Share saved-source picker confirm/navigation key handling through the desktop confirm-key helper.
- [x] Add unit coverage for source-management local action keyboard/DPAD navigation.
- [x] Add unit coverage for remote source open/scan action keyboard/DPAD navigation.
- [x] Add unit coverage for source-management local/WebDAV/SMB field keyboard/DPAD navigation into adjacent actions.
- [x] Add unit coverage for remote source editor-to-browser and browser-to-editor keyboard/DPAD focus bridging.
- [x] Keep every Windows WebDAV/SMB remote-browser item reachable by keyboard/DPAD pagination, not only the first visible eight rows.
- [x] Add Windows GUI smoke coverage for saved-source card keyboard/DPAD navigation.
- [x] Rework the Windows WebDAV/SMB source setup into TV-style remote source cards with compact endpoint/path previews.
- [x] Add a tiny generated media sample and GUI mpv launch smoke.
- [x] Add unit coverage for Player playback-setting toggle/backend keyboard/DPAD navigation.
- [x] Add unit coverage for Player runtime form/check keyboard/DPAD navigation.
- [x] Add unit coverage for Player Stage -> playback settings -> runtime vertical keyboard/DPAD focus bridging.
- [x] Connect the Windows Player `全屏` setting to the native Compose Desktop window fullscreen state with route-gated restore behavior.
- [x] Extract remaining playback launch/config presenter logic out of the Compose entry.

Verification:

```powershell
.\gradlew.bat :desktop-app:installDist -PbundleMpvRuntime=false
.\tools\capture-desktop-ui.ps1
.\tools\smoke-desktop-keyboard-focus-ui.ps1
.\tools\smoke-desktop-local-source-ui.ps1
.\tools\smoke-desktop-local-source-ui.ps1 -LibraryRoot 'D:\Software\dufs'
.\tools\smoke-desktop-source-management-ui.ps1
.\tools\smoke-desktop-webdav-source-ui.ps1
.\tools\smoke-desktop-smb-source-ui.ps1
.\gradlew.bat checkDesktopComposeOnly checkUiPaletteDrift :desktop-app:test
```

### Phase 2: Prove Media Management Parity On Windows

- [x] GUI smoke: add/open Local source and scan a fixture with NFO metadata.
- [x] GUI smoke: inspect details by clicking a poster-wall item and select scanned local media for player handoff.
- [x] GUI smoke: repeat the local source flow against `D:\Software\dufs`.
- [x] GUI smoke: search scanned local index from the query field in the poster-wall layout.
- [x] Add a live Bangumi scraper smoke for search, subject details, and episode listing.
- [x] GUI smoke: apply and clear Bangumi metadata from the real Windows Details screen.
- [x] GUI smoke: add/open WebDAV source using a local/loopback fixture where possible.
- [x] GUI smoke: add/open SMB source against a real Windows fixture share without touching unrelated share files.
- [x] Confirm clear-source-index and remove-source flows keep repository state consistent.
- [x] Review Android TV detail/player screens against desktop first screens and record deeper parity gaps.

Verification:

```powershell
.\gradlew.bat :media-source-desktop:test :scanner-desktop:test :repository-desktop:test :desktop-app:test
.\gradlew.bat :scraper-desktop:test :scraper-desktop:smokeBangumiLive -PbangumiSmokeReportPath=build\bangumi-smoke\live-report.json
.\tools\smoke-desktop-bangumi-metadata-ui.ps1
.\tools\capture-desktop-ui.ps1
.\tools\smoke-desktop-keyboard-focus-ui.ps1
.\tools\smoke-desktop-local-source-ui.ps1
.\tools\smoke-desktop-local-source-ui.ps1 -LibraryRoot 'D:\Software\dufs'
.\tools\smoke-desktop-source-management-ui.ps1
.\tools\smoke-desktop-webdav-source-ui.ps1
.\tools\smoke-desktop-smb-source-ui.ps1
.\tools\smoke-android-tv-ui.ps1
.\tools\assert-android-tv-smoke-report.ps1 `
  -ReportPath .\build\android-tv-qa\run-YYYYMMDD-HHMMSS\android-tv-smoke-report.json `
  -RequiredDeviceId <android-tv-device-id>
```

### Phase 3: Prove Playback And Progress

- [x] Build a tiny generated Y4M local video fixture for repeatable mpv launch smoke.
- [x] Run mpv launch from the Windows GUI against the fixture and confirm the `mpv.exe` child process.
- [x] Verify Pause, -10s, +30s, Stop, and recent-progress refresh without requiring RIFE on this host.
- [x] Verify Player transport controls support keyboard/DPAD-style left/right movement, Enter activation, and Up/Down movement to/from `返回详情`.
- [x] Verify Player playback settings can move from media/start/subtitle fields into toggles and then into the runtime card, with runtime `Up` returning to settings.
- [x] Verify the Player `全屏` toggle is route-gated so it enters native desktop fullscreen only on the Player route and restores the prior window placement when leaving Player.
- [x] Verify remote playback bridge keeps WebDAV/SMB hosts and credentials out of mpv command lines.
- [x] Keep RIFE optional and make missing runtime errors actionable in the UI.

Verification:

```powershell
.\gradlew.bat :player-mpv:test :desktop-app:test
.\tools\smoke-desktop-mpv-launch-ui.ps1
.\gradlew.bat :desktop-app:smokeMpvRuntime -PrequireMpvRuntime=true
# Full RIFE playback/interpolation smoke is target-hardware validation, not a blocker on low-capability local hosts.
```

### Phase 4: Prove CloudDrive2/RSS

- [ ] Run token/path/RSS dry-run against a real CloudDrive2 endpoint and save token-free report.
- [ ] Run explicit-confirmation live submit with a low submit limit and save token-free report.
- [ ] Validate organizer move behavior against real inbox/library paths.
- [x] Validate scheduler behavior over real elapsed time with token-free report assertion.
- [x] Verify post-sync source rescan updates desktop index.

Verification:

```powershell
.\gradlew.bat :cloud-drive-desktop:test :sync-engine-desktop:test
.\gradlew.bat :sync-engine-desktop:smokeCloudDriveRssDryRun `
  -PcloudDriveEndpoint=<endpoint> `
  -PcloudDriveToken=<token> `
  -PcloudDriveRssUrl=<rss> `
  -PcloudDriveInbox=<inbox> `
  -PcloudDriveLibrary=<library> `
  -PcloudDriveRssReportPath=build/cloud-rss-smoke/dry-run-report.json

.\tools\assert-cloud-rss-report.ps1 `
  -ReportPath .\build\cloud-rss-smoke\dry-run-report.json `
  -RequiredInbox <inbox> `
  -RequiredLibrary <library> `
  -RequireCandidates

.\gradlew.bat :sync-engine-desktop:smokeCloudDriveRssDryRun `
  -PcloudDriveEndpoint=<endpoint> `
  -PcloudDriveToken=<token> `
  -PcloudDriveRssUrl=<rss> `
  -PcloudDriveInbox=<inbox> `
  -PcloudDriveLibrary=<library> `
  -PcloudDriveRssOrganize=true `
  -PcloudDriveRssOrganizeConfirmation=I_UNDERSTAND_THIS_MOVES_REAL_CLOUDDRIVE_FILES `
  -PcloudDriveRssReportPath=build/cloud-rss-smoke/organize-report.json

.\tools\assert-cloud-rss-report.ps1 `
  -ReportPath .\build\cloud-rss-smoke\organize-report.json `
  -RequiredInbox <inbox> `
  -RequiredLibrary <library> `
  -RequireOrganize

# -RequireOrganize requires organize.movedCount > 0, not just post-listing fields.

.\gradlew.bat :sync-engine-desktop:smokeCloudDriveRssScheduler `
  -PcloudDriveRssSchedulerDurationMs=2000 `
  -PcloudDriveRssSchedulerCheckIntervalMs=250 `
  -PcloudDriveRssSchedulerRunAfterChecks=2 `
  -PcloudDriveRssSchedulerReportPath=build/cloud-rss-smoke/scheduler-report.json

.\tools\assert-cloud-rss-scheduler-report.ps1 `
  -ReportPath .\build\cloud-rss-smoke\scheduler-report.json `
  -MinRunCount 1 `
  -MinChecksObserved 2
```

### Phase 5: Release Readiness

- [x] Build lightweight Windows install for GUI QA.
- [x] Build full Windows distribution with bundled runtime source.
- [x] Run `mpv.exe --version` smoke for the runtime used in the packaged distribution and verify packaged runtime zip entries.
- [x] Add a Windows native app-image gate that builds with JDK `jpackage` and verifies the launcher, generated launcher config/classpath, bundled mpv/RIFE runtime, and generated-launcher headless desktop-entry smoke report.
- [x] Add an opt-in Windows MSI/EXE installer gate that preflights WiX, builds from the verified app image, records installer SHA256/size/version/signing-mode evidence, supports explicit signtool/PFX signing, and validates the installer report against the generated artifact.
- [x] Add a repeatable local Windows-port verification orchestrator with safe defaults and explicit opt-in live/device/runtime checks.
- [x] Add shared media-source API, metadata-core, scraper-core, and Android-side metadata/scraper/sync/web-control tests to CI/local verification for cross-platform dependency-boundary changes.
- [x] Keep `codex/**` branch CI as validation-only by skipping nightly/release publishing outside main/master scheduled or manual runs.
- [x] Build and upload a lightweight Windows desktop ZIP from `windows-latest` CI, and attach the same ZIP to nightly main/master releases.
- [ ] Run RIFE matrix on target hardware; DirectML is target-host validation, NVIDIA depends on CUDA/TensorRT driver compatibility, Standard requires a plugin decision.
- [x] Keep CI green for Android and desktop/shared JVM gates on the active Windows port branch.

Verification:

```powershell
.\tools\verify-windows-port.ps1

# Uses JDK 21 automatically when available; set JAVA21_HOME or JDK21_HOME to override discovery.

# Optional live/device/runtime gates:
.\tools\verify-windows-port.ps1 -Gui
.\tools\verify-windows-port.ps1 -RealLibrary -RealLibraryRoot 'D:\Software\dufs'
.\tools\verify-windows-port.ps1 -AndroidTv -AndroidDeviceId <android-tv-device-id>
.\tools\assert-android-tv-smoke-report.ps1 `
  -ReportPath .\build\android-tv-qa\run-20260520-190519\android-tv-smoke-report.json `
  -RequiredDeviceId <android-tv-device-id>
.\tools\verify-windows-port.ps1 -Smb
.\tools\verify-windows-port.ps1 -MpvRuntime -PackagedMpvRuntime -NativeAppImage
.\tools\verify-windows-port.ps1 `
  -WindowsInstaller `
  -SignWindowsInstaller `
  -WindowsInstallerType msi `
  -MpvRuntimeSource runtime\mpv `
  -RequiredRifeBackends NVIDIA,DIRECTML `
  -WindowsInstallerCertPath C:\path\MiruPlay-release.pfx `
  -WindowsInstallerSignTool C:\path\signtool.exe `
  -WindowsInstallerCertPassword <password>
.\tools\verify-windows-port.ps1 -Rife -RifeBackend ALL -AllowRifeFailures
.\tools\verify-windows-port.ps1 `
  -CloudDrive `
  -CloudDriveEndpoint http://127.0.0.1:19798 `
  -CloudDriveToken <token> `
  -CloudDrivePath /Downloads `
  -RequireCloudDriveOfflinePermission
# Temporary local bypass only; the default gate runs the scheduler smoke.
.\tools\verify-windows-port.ps1 -SkipCloudRssScheduler
.\tools\verify-windows-port.ps1 `
  -CloudRssDryRun `
  -CloudRssEndpoint http://127.0.0.1:19798 `
  -CloudRssToken <token> `
  -CloudRssUrl https://example.test/rss.xml `
  -CloudRssInbox /Downloads `
  -CloudRssLibrary /Library `
  -CloudRssFilter Episode `
  -RequireCloudRssCandidates
.\tools\assert-cloud-drive-report.ps1 `
  -ReportPath .\build\cloud-drive-smoke\cloud-drive-report.json `
  -RequiredPath /Downloads `
  -RequireOfflinePermission
.\tools\assert-cloud-rss-report.ps1 `
  -ReportPath .\build\cloud-rss-smoke\dry-run-report.json `
  -RequiredInbox /Downloads `
  -RequiredLibrary /Library `
  -RequireCandidates
.\tools\assert-cloud-rss-scheduler-report.ps1 `
  -ReportPath .\build\cloud-rss-smoke\scheduler-report.json `
  -MinRunCount 1 `
  -MinChecksObserved 2
.\tools\assert-mpv-rife-report.ps1 `
  -ReportPath .\build\mpv-smoke\rife-matrix-report.json `
  -RequiredBackends NVIDIA,DIRECTML `
  -RequireRuntimeManifest `
  -AllowFailures

.\gradlew.bat :app:assembleDebug
.\gradlew.bat checkDesktopComposeOnly checkDesktopPresenterSeparation checkUiPaletteDrift `
  :core:model:test :media-source-api:test :metadata-core:test :repository-api:test :cloud-drive-api:test :sync-engine-shared:test `
  :metadata:test :scraper-core:test :scraper:test :sync-engine:test :web-control:test `
  :media-source-desktop:test :scanner-desktop:test :repository-desktop:test :scraper-desktop:test `
  :player-mpv:test :cloud-drive-desktop:test :sync-engine-desktop:test :desktop-app:test `
  :desktop-app:installDist -PbundleMpvRuntime=false
.\gradlew.bat :desktop-app:smokePackagedMpvRuntime `
  -PmpvRuntimeSource=runtime\mpv `
  -PrequireMpvRuntime=true `
  -PrequiredRifeBackends=NVIDIA,DIRECTML
.\gradlew.bat :desktop-app:smokeNativeAppImageRuntime `
  -PmpvRuntimeSource=runtime\mpv `
  -PrequireMpvRuntime=true `
  -PrequiredRifeBackends=NVIDIA,DIRECTML
.\gradlew.bat :desktop-app:smokeWindowsInstaller `
  -PmpvRuntimeSource=runtime\mpv `
  -PrequireMpvRuntime=true `
  -PrequiredRifeBackends=NVIDIA,DIRECTML `
  -PrequireWindowsInstallerToolchain=true
```

`-Smb` is intentionally restricted to the approved `\\smb.ynz.local\share\临时文件\测试` fixture directory; do not use it to scan or modify unrelated files in that share.

## Immediate Next Actions

1. Run real CloudDrive2 dry-run/live QA and record token-free evidence for sync and organize.
2. Run RIFE backend matrix on target Windows hardware with the packaged runtime and record the JSON report, including manifest evidence via `-RequireRuntimeManifest`.
3. Continue narrowing deeper desktop-vs-Android-TV UI gaps beyond the first screens, especially less-traveled keyboard/DPAD focus paths outside the now-covered Android TV Settings category/page traversal, Settings summary quick-action rows, source-management local/remote fields plus actions and remote editor/browser focus bridge, Cloud/RSS credential/sync/RSS edit fields plus action/toggle/subscription rows, desktop route rail, Library header action row, Library poster wall/highest-heat/recent shelves/search row/source bridge, Details hero actions, Details hero-to-episodes/recents/Bangumi/media-details fallback plus episode-shelf season selector, remote browser list including parent navigation, Player stage/settings/runtime focus bridge, saved-source card movement, and Bangumi metadata action grid/apply-clear flow.
4. Use `tools/verify-windows-port.ps1` as the local gate before pushes; GitHub Actions also runs the base gate plus the local Cloud/RSS scheduler smoke on `codex/**` pushes. Keep SMB, live CloudDrive/RSS, and RIFE checks opt-in so the shared SMB folder, real CloudDrive server, and low-capability local host are not touched by default.
