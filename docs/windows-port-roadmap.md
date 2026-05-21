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
| mpv/RIFE runtime | Windows distribution can bundle or locate a verified mpv runtime with RIFE-capable scripts. | `:desktop-app:smokeMpvRuntime`, `:desktop-app:smokePackagedMpvRuntime`, `tools/smoke-mpv-rife.ps1`, runtime manifest evidence. |
| Cloud/RSS | Windows can configure CloudDrive2/RSS, dry-run safely, and run confirmed live submit/organize flows. | `:cloud-drive-desktop:test`, `:sync-engine-desktop:test`, dry-run report, explicit live QA report. |
| Release | CI and local release gates cover Android build, desktop tests, runtime checks, and screenshot QA. | CI green, local verification command log, packaged artifact smoke. |

## Current Status

| Area | Status | Notes |
|---|---|---|
| Android TV build | Covered for debug build and DPAD Library/detail/player/settings smoke | Latest local `:app:assembleDebug` passed. `tools/smoke-android-tv-ui.ps1` installs the debug APK on emulator `10.137.32.118:5555`, generates a playable fixture library, scans it through the TV UI, confirms Library content requests poster focus, drives DPAD Right/Left then Center from the poster surface into Details, drives DPAD Down from the Details play action to focus the first episode row, drives DPAD Center from that focused episode row into Player, verifies Android Back returns from Player to Details and from Details to the poster-focused Library wall, drives DPAD Up/Right/Center from that returned poster wall into Settings, drives DPAD Down/Center inside Settings to the media-source panel, verifies DPAD Right focuses the auto-added source card, Left returns to the media-source menu, Center opens the edit-source form without losing card focus, Right focuses the source delete button, Center removes the source while keeping the media-source empty state visible, then continues DPAD Down through Playback, CloudDrive, Scan, and Metadata settings with matching menu focus, and records `build/android-tv-qa/run-20260520-190519/android-tv-library.png`, `android-tv-library-dpad-poster.png`, `android-tv-details.png`, `android-tv-details-episode-focus.png`, `android-tv-player.png`, `android-tv-library-return.png`, `android-tv-settings.png`, `android-tv-settings-sources.png`, `android-tv-settings-source-card-focus.png`, `android-tv-settings-source-edit.png`, `android-tv-settings-source-delete-focus.png`, `android-tv-settings-source-deleted.png`, `android-tv-settings-playback.png`, `android-tv-settings-cloud-drive.png`, `android-tv-settings-scan.png`, `android-tv-settings-metadata.png`, XML dumps, and a JSON report. |
| Compose Desktop entry | Usable foundation | Swing production shell removed; screenshot QA exists for first screens. Latest Library UI now opens scanned libraries with the 6-column poster wall as the first media surface under the TV-style `探索` header, and the Library header now matches the Android TV action surface by exposing only `扫描` and `设置` instead of desktop-only Details/Player shortcuts. Highest-heat/recent rows and search/source controls sit below the wall; saved indexes are restored on startup/source switch; poster selection routes directly into a TV-style Details hero with stat pills for indexed episode/season/metadata context. Source management now uses a focused TV-style saved-source card that keeps source name/type visible with TV-facing labels and compacts long local/SMB/WebDAV paths from the middle, while local source fields and actions have unit-covered focus instead of panel-wide source switching. The latest source-management GUI smoke preloads two isolated local sources, uses keyboard `Up` on the saved-source card to switch back to the first source, proves the scan uses the keyboard-selected source id, and verifies scan/clear/remove state updates plus localized controls and localized scan status copy in a real window. Remote WebDAV/SMB setup is split into TV-style source cards with compact endpoint previews and a separate remote-browser panel; remote source edit fields plus open/scan actions now have unit-covered focus matching WebDAV fields/actions above the SMB field/action rows, and GUI smokes verify deep local paths, loopback WebDAV, and real SMB flows from add/open through scan and playback handoff. Android TV smoke confirms the TV baseline is a media-first Library with poster focus, Details hero, episode shelf, full-screen playback overlay, and Back returning Player -> Details -> Library; desktop deliberately approximates that inside a Windows window with a rail-free playback stage, TV-facing mpv/RIFE settings labels and status copy below, TV-facing `探索`/`详情`/`播放`/`设置` route chrome plus `MiruPlay 桌面版` native window title, and `Esc` as the TV Back equivalent for Player -> Details -> Library/Settings -> Library. Remaining mpv launch/config preparation now lives in `DesktopPlaybackPresenters.kt`; Settings now opens with a TV-style section menu, concrete source/playback/scan/metadata cards, and a Cloud/RSS page made of overview, CloudDrive2, path, subscription, and scheduler cards. The Bangumi metadata panel now uses TV-facing Chinese action labels, section labels, empty state, batch status chips, and status copy while preserving the existing keyboard action grid. The Settings category menu, Settings summary quick-action rows, desktop route rail, Library poster wall, Library highest-heat/recent poster shelves, Details hero actions, Details episode shelf, Continue watching recents/actions, remote browser list, remote source fields/actions, Player transport controls, playback-setting controls, and runtime controls, saved-source card, local/remote source fields and actions, Cloud/RSS credential/sync/RSS edit fields, action grids, toggles, and RSS subscription rows, and Bangumi match lists/action grid now support keyboard/DPAD-style navigation; GUI smokes prove Sources -> CloudDrive, CloudDrive RSS subscription row Down, Details -> Player -> Esc -> Details -> Esc -> Library, poster-wall Right -> Enter, Details hero Right -> Left -> Enter, Details hero Down -> episode shelf, Details episode shelf Up -> hero, Details episode shelf Down -> Bangumi -> Up -> episode shelf, Bangumi action grid Right/Right into search results, Left back to Apply, then Enter for apply plus Right/Enter for clear, Details hero Down -> Continue watching Enter, Details hero Down -> Bangumi Use selected when no recents exist, WebDAV remote-browser Up -> parent plus Down -> Enter, saved-source card Up, and Player transport Enter/Left/Right actions by key input; unit tests cover Library header action parity, route Back hierarchy, Settings summary quick-action row, saved-source card, local/remote source field and action movement, Cloud/RSS credential/sync/RSS edit field, action-grid, toggle-row, and RSS subscription row movement, Library poster-wall short-row movement, Library highest-heat/recent shelf ordering and horizontal movement, Bangumi match/candidate/result list movement, Bangumi action-grid/list handoff movement and top-action Up exit, Details hero action movement, Details hero stat labels, Details hero Down fallback targeting, Details episode shelf grouping/season filtering, Details episode shelf boundary exits, Continue watching row movement plus `刷新`/`清除条目` action-pair movement, Player-stage Up/Down focus between `返回详情` and transport controls, Player playback-setting toggle/backend row movement, and Player runtime form/check movement. |
| Shared UI palette | Covered structurally | `:ui-design` owns shared palette; drift check exists. |
| Local/WebDAV/SMB desktop sources | Implemented | Local source GUI smoke now covers generated fixture and a real local library path; WebDAV GUI smoke covers a loopback Basic Auth fixture from add/open/browse through scan with the TV-style remote source card layout; SMB GUI smoke covers a real authenticated share fixture under the approved `临时文件\测试` directory, defaults to the approved `test-user` smoke credentials, and redacts credentials from stored evidence. |
| Library/index/details | Implemented foundation | Local scan/index, WebDAV scan/index, SMB scan/index, clear-index/remove-source, long-path saved-source display, remote endpoint/path preview, TV-style poster-wall selection, Details hero with stat pills, Details episode shelf, and player handoff GUI smoke now passes for generated fixtures; latest local smoke captures `build/desktop-local-source-ui/run-20260520-162054/local-source-details.png`, `local-source-details-episodes.png`, `local-source-details-episode-back-to-hero.png`, `local-source-details-episode-selected.png`, `local-source-details-episode-to-bangumi.png`, `local-source-details-bangumi-back-to-episode.png`, and `local-source-player.png` after moving from hero into the episode shelf, returning to the hero with `Up`, re-entering the shelf, selecting Frieren episode 2, moving down to Bangumi, returning to the episode shelf with `Up`, and handing that path to Player; local smoke also passes against `D:\Software\dufs` with the scanned Library opening directly on the poster wall. |
| Bangumi metadata | Implemented foundation | Unit coverage exists for desktop navigation and offline scraper parsing. `:scraper-desktop:smokeBangumiLive` now verifies live Bangumi search/details/episodes and writes a token-free JSON report. `tools/smoke-desktop-bangumi-metadata-ui.ps1` opens the real Windows Details Bangumi panel, sends Details hero `Down` into the episode shelf, sends `Down` again to focus Bangumi `Use selected`, drives the action grid with keyboard `Right` to Search, `Right` into search results, `Left` back to Apply match, then `Right` to Clear metadata, verifies the isolated JSON store is written then cleared, and captures `build/desktop-bangumi-metadata-ui/run-20260520-213906/bangumi-details-ready.png`, `bangumi-focus-bangumi.png`, `bangumi-search-results.png`, `bangumi-metadata-applied.png`, and `bangumi-metadata-cleared.png`. |
| mpv playback | Implemented foundation | Desktop Player keeps mpv/RIFE controls behind a TV-like playback stage with localized `媒体 URI 或文件路径`/`起播秒数`/`外挂字幕路径` labels, localized mpv status chips, localized runtime verifier output, and localized diagnostic panel chrome; Player focus now moves vertically between `返回详情` and the primary transport with TV-style Up/Down keys while preserving left/right transport edge stops, playback-setting toggles plus the RIFE backend picker now have unit-covered left/right row movement, and the runtime card now has unit-covered vertical movement from `mpv.exe` through `portable_config` to `检查运行时`; `tools/smoke-desktop-mpv-launch-ui.ps1` now generates a local Y4M sample, launches it through the Windows GUI, confirms an `mpv.exe` child process, exercises Pause/-10s/+30s/Stop by keyboard focus, verifies progress persistence, and captures launched/keyboard-control/stopped Player screens. |
| RIFE runtime | Partial | Runtime structure and scripts are tracked; desktop launch keeps RIFE opt-in by default and missing mpv/RIFE launch errors now render TV-facing Chinese guidance for checking the runtime, preparing a backend, or turning RIFE off. Local machine RIFE playback is non-blocking because this host is not expected to run interpolation well. Backend matrix remains target-host validation. |
| Cloud/RSS | Partial | Loopback tests exist and the desktop Settings UI now exposes Cloud/RSS as TV-style Chinese overview/config/subscription/scheduler cards, including localized labels, empty states, previews, and common status copy. Cloud/RSS action button groups now have deterministic `Left`/`Right` row movement plus `Up`/`Down` movement through credentials, login/verify, scan-source, sync, RSS, subscription rows, and scheduler controls; CloudDrive2 credential fields, sync-path fields, and RSS edit fields now move through their form rows and bridge into the nearest save/clear or enable/proxy/RSS controls, while enable/proxy/RSS toggles bridge into their neighboring action rows with unit-covered TV-style focus movement; RSS subscription rows still support keyboard `Up`/`Down` movement and the Settings keyboard smoke captures the first and second selected subscriptions in `build/desktop-keyboard-focus-ui/run-20260521-073103`. The smoke script now writes its isolated JSON store as UTF-8 without BOM so Windows PowerShell launches preload the same fixture state. Post-sync source rescan is now extracted into a reusable desktop helper and covered by an integration test that refreshes a linked local source index after fixture contents change. Real CloudDrive2 dry-run/live evidence still open. |
| Release packaging | Partial | Lightweight install works; full bundled runtime `distZip` now has a packaged mpv/RIFE smoke gate. `tools/verify-windows-port.ps1` now provides a repeatable local gate with safe defaults, automatically selects JDK 21 on Windows when available, and keeps GUI, real-library, Android TV, SMB, mpv-runtime, and RIFE checks opt-in. GitHub Actions now runs the Android/desktop/shared JVM CI gate on `codex/**` push branches as well as mainline branches, while release artifacts remain restricted to main/master. Installer/signing/target-host runtime validation remain open. |

## Work Plan

### Phase 1: Stabilize The Existing Windows App

- [x] Keep Windows UI on Compose Desktop only.
- [x] Add screenshot QA for Library, Details, Player, and Settings.
- [x] Share media-source connection field conventions across desktop modules.
- [x] Run the current Windows GUI screenshot smoke locally.
- [x] Add a fixture-driven Windows GUI smoke that creates/uses a local media source, scans it, verifies store state, and records screenshots.
- [x] Extend the local-source GUI smoke to support a documented real local library path.
- [x] Capture Android TV Library baseline from emulator `10.137.32.118:5555` into `build/android-tv-qa/library-baseline.png`.
- [x] Add Android TV smoke coverage for Library poster focus and DPAD poster -> Details episode row -> Player -> Back to Library poster wall -> Settings -> media sources -> source card -> menu return -> source edit form -> source delete button -> empty source state -> Playback/CloudDrive/Scan/Metadata category pages.
- [x] Rework the Windows Library first screen toward the Android TV Library: full-width `探索` header, `扫描`/`设置` actions, TV empty state, and poster wall after scan.
- [x] Make scanned Windows libraries open directly as a TV-style 6-column poster wall and restore saved index entries on startup/source switch.
- [x] Rework the Windows Details first screen toward Android TV: poster click opens Details directly, with a large backdrop/poster hero, title context, plot, Play, and Back-to-poster-wall actions.
- [x] Add TV-style stat pills to the Windows Details hero from indexed episode, season, and metadata context.
- [x] Add a TV-style Windows Details episode shelf grouped from the selected indexed anime, with season filtering and keyboard focus from the hero.
- [x] Localize Windows Details hero empty state, episode shelf subtitle, Continue watching controls, and media-detail chrome to TV-facing Chinese copy.
- [x] Add unit coverage for Details episode shelf multi-season selector keyboard/DPAD navigation.
- [x] Add unit coverage for Details media-detail row keyboard/DPAD navigation and bottom-panel focus bridging.
- [x] Rework the Windows Player first screen toward Android TV: rail-free playback stage, top return action, centered transport controls, bottom timeline/status chips, and TV-facing mpv/RIFE settings below.
- [x] Rework the Windows Settings first screen toward Android TV: left-side settings categories with focused rows, concrete source/playback/scan/metadata cards, and quick actions before Cloud/RSS.
- [x] Rework the Windows Cloud/RSS settings page into TV-style overview/config/subscription/scheduler cards.
- [x] Add Windows GUI smoke coverage for Settings category and desktop route rail keyboard/DPAD-style navigation.
- [x] Make Settings category and desktop route rail keyboard movement stop at TV-list edges instead of wrapping.
- [x] Add unit coverage for Settings summary quick-action row keyboard/DPAD navigation.
- [x] Add Windows GUI smoke coverage for TV Back-equivalent `Esc` route navigation.
- [x] Add Windows GUI smoke coverage for Cloud/RSS RSS subscription row keyboard/DPAD navigation.
- [x] Add unit coverage for Cloud/RSS action-grid keyboard/DPAD navigation.
- [x] Add unit coverage for Cloud/RSS toggle-row keyboard/DPAD navigation.
- [x] Add unit coverage for Cloud/RSS sync-path field keyboard/DPAD navigation into enable/proxy toggles.
- [x] Add unit coverage for Cloud/RSS credential and RSS edit field keyboard/DPAD navigation into adjacent actions/toggles.
- [x] Add Windows GUI smoke coverage for Library poster-wall keyboard/DPAD navigation.
- [x] Add unit coverage for Library highest-heat and recent poster shelf keyboard/DPAD navigation.
- [x] Add Windows GUI smoke coverage for Details hero action keyboard/DPAD navigation.
- [x] Add Windows GUI smoke coverage for Details episode shelf boundary keyboard/DPAD navigation.
- [x] Add Windows GUI smoke coverage for Continue watching recent-playback keyboard/DPAD navigation.
- [x] Add unit coverage for Continue watching refresh/clear action-pair keyboard/DPAD navigation.
- [x] Add Windows GUI smoke coverage for Details hero Down -> Bangumi metadata focus fallback.
- [x] Add Windows GUI smoke coverage for remote-browser row keyboard/DPAD navigation.
- [x] Add unit coverage for Bangumi match/candidate/result list keyboard/DPAD navigation.
- [x] Add unit and Windows GUI smoke coverage for Bangumi action-grid keyboard/DPAD navigation.
- [x] Localize Windows Bangumi metadata controls, empty states, batch chips, and status copy to TV-facing Chinese.
- [x] Rework the Windows source-management saved-source picker for TV-style focus and long path readability.
- [x] Localize Windows source-management controls and saved-source type labels to TV-facing Chinese copy.
- [x] Localize Windows Library/source-management status messages to TV-facing Chinese copy.
- [x] Localize Windows Cloud/RSS settings labels, previews, empty states, and status copy to TV-facing Chinese.
- [x] Localize Windows route rail subtitle, playback poster placeholder, and Settings page references to TV-facing Chinese copy.
- [x] Add unit coverage for saved-source card keyboard/DPAD navigation.
- [x] Add unit coverage for source-management local action keyboard/DPAD navigation.
- [x] Add unit coverage for remote source open/scan action keyboard/DPAD navigation.
- [x] Add unit coverage for source-management local/WebDAV/SMB field keyboard/DPAD navigation into adjacent actions.
- [x] Add Windows GUI smoke coverage for saved-source card keyboard/DPAD navigation.
- [x] Rework the Windows WebDAV/SMB source setup into TV-style remote source cards with compact endpoint/path previews.
- [x] Add a tiny generated media sample and GUI mpv launch smoke.
- [x] Add unit coverage for Player playback-setting toggle/backend keyboard/DPAD navigation.
- [x] Add unit coverage for Player runtime form/check keyboard/DPAD navigation.
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
```

### Phase 3: Prove Playback And Progress

- [x] Build a tiny generated Y4M local video fixture for repeatable mpv launch smoke.
- [x] Run mpv launch from the Windows GUI against the fixture and confirm the `mpv.exe` child process.
- [x] Verify Pause, -10s, +30s, Stop, and recent-progress refresh without requiring RIFE on this host.
- [x] Verify Player transport controls support keyboard/DPAD-style left/right movement, Enter activation, and Up/Down movement to/from `返回详情`.
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
- [ ] Validate scheduler behavior over real elapsed time.
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
```

### Phase 5: Release Readiness

- [x] Build lightweight Windows install for GUI QA.
- [x] Build full Windows distribution with bundled runtime source.
- [x] Run `mpv.exe --version` smoke for the runtime used in the packaged distribution and verify packaged runtime zip entries.
- [x] Add a repeatable local Windows-port verification orchestrator with safe defaults and explicit opt-in live/device/runtime checks.
- [ ] Run RIFE matrix on target hardware; DirectML is target-host validation, NVIDIA depends on CUDA/TensorRT driver compatibility, Standard requires a plugin decision.
- [ ] Keep CI green for Android and desktop/shared JVM gates.

Verification:

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

.\gradlew.bat :app:assembleDebug
.\gradlew.bat checkDesktopComposeOnly checkDesktopPresenterSeparation checkUiPaletteDrift `
  :core:model:test :repository-api:test :cloud-drive-api:test :sync-engine-shared:test `
  :media-source-desktop:test :scanner-desktop:test :repository-desktop:test :scraper-desktop:test `
  :player-mpv:test :cloud-drive-desktop:test :sync-engine-desktop:test :desktop-app:test `
  :desktop-app:installDist -PbundleMpvRuntime=false
.\gradlew.bat :desktop-app:smokePackagedMpvRuntime `
  -PmpvRuntimeSource=runtime\mpv `
  -PrequireMpvRuntime=true `
  -PrequiredRifeBackends=NVIDIA,DIRECTML
```

`-Smb` is intentionally restricted to the approved `\\smb.example.test\share\临时文件\测试` fixture directory; do not use it to scan or modify unrelated files in that share.

## Immediate Next Actions

1. Run real CloudDrive2 dry-run/live QA and record token-free evidence for sync, organize, scheduler timing, and source rescan.
2. Run RIFE backend matrix on target Windows hardware with the packaged runtime and record the JSON report.
3. Continue narrowing deeper desktop-vs-Android-TV UI gaps beyond the first screens, especially less-traveled keyboard/DPAD focus paths outside the now-covered Android TV Settings category/page traversal, Settings summary quick-action rows, source-management local/remote fields plus actions, Cloud/RSS credential/sync/RSS edit fields plus action/toggle/subscription rows, desktop route rail, Library poster wall and highest-heat/recent shelves, Details hero actions, Details hero-to-episodes/recents/Bangumi/media-details fallback plus episode-shelf season selector, remote browser list including parent navigation, Player transport controls plus top-return focus, Player playback-setting row movement, Player runtime form/check movement, saved-source card movement, and Bangumi metadata lists/action grid/apply-clear flow.
4. Use `tools/verify-windows-port.ps1` as the local gate before pushes; GitHub Actions also runs the base gate on `codex/**` pushes. Keep SMB and RIFE checks opt-in so the shared SMB folder and low-capability local host are not touched by default.
