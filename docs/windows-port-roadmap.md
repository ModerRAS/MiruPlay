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
| Metadata | Windows can search/apply/clear Bangumi metadata and run batch review/apply/undo. | `:scraper-desktop:test`, `:repository-api:test`, `:desktop-app:test`, GUI details smoke. |
| Playback | Windows plays through mpv, supports RIFE toggle/backend selection, IPC pause/seek/stop, and progress persistence. | `:player-mpv:test`, `:desktop-app:test`, mpv launch smoke with a local sample file. |
| mpv/RIFE runtime | Windows distribution can bundle or locate a verified mpv runtime with RIFE-capable scripts. | `:desktop-app:smokeMpvRuntime`, `tools/smoke-mpv-rife.ps1`, runtime manifest evidence. |
| Cloud/RSS | Windows can configure CloudDrive2/RSS, dry-run safely, and run confirmed live submit/organize flows. | `:cloud-drive-desktop:test`, `:sync-engine-desktop:test`, dry-run report, explicit live QA report. |
| Release | CI and local release gates cover Android build, desktop tests, runtime checks, and screenshot QA. | CI green, local verification command log, packaged artifact smoke. |

## Current Status

| Area | Status | Notes |
|---|---|---|
| Android TV build | Covered for debug build | Latest local `:app:assembleDebug` passed. Instrumented TV QA still open. |
| Compose Desktop entry | Usable foundation | Swing production shell removed; screenshot QA exists for first screens. Latest Library UI now uses the Android TV-style full-width header, right-side actions, empty state, and poster wall after scan; poster selection routes directly into a TV-style Details hero; Player now opens as a rail-free TV-style playback stage. |
| Shared UI palette | Covered structurally | `:ui-design` owns shared palette; drift check exists. |
| Local/WebDAV/SMB desktop sources | Implemented | Local source GUI smoke now covers generated fixture and a real local library path; WebDAV/SMB GUI fixture smokes still open. |
| Library/index/details | Implemented foundation | Local scan/index, poster-wall selection, TV-style details hero, and player handoff GUI smoke now passes; GUI search smoke needs to be re-added for the poster-wall layout. |
| Bangumi metadata | Implemented foundation | Unit coverage exists; live network behavior needs manual/smoke evidence. |
| mpv playback | Implemented foundation | Desktop Player keeps mpv/RIFE controls behind a TV-like playback stage; need repeatable GUI launch smoke against a tiny local media sample. |
| RIFE runtime | Partial | Runtime structure and scripts are tracked; local machine RIFE playback is non-blocking because this host is not expected to run interpolation well. Backend matrix remains target-host validation. |
| Cloud/RSS | Partial | Loopback tests exist; real CloudDrive2 dry-run/live evidence still open. |
| Release packaging | Partial | Lightweight install works; full bundled runtime artifact QA remains open. |

## Work Plan

### Phase 1: Stabilize The Existing Windows App

- [x] Keep Windows UI on Compose Desktop only.
- [x] Add screenshot QA for Library, Details, Player, and Settings.
- [x] Share media-source connection field conventions across desktop modules.
- [x] Run the current Windows GUI screenshot smoke locally.
- [x] Add a fixture-driven Windows GUI smoke that creates/uses a local media source, scans it, verifies store state, and records screenshots.
- [x] Extend the local-source GUI smoke to support a documented real local library path.
- [x] Capture Android TV Library baseline from emulator `10.137.32.118:5555` into `build/android-tv-qa/library-baseline.png`.
- [x] Rework the Windows Library first screen toward the Android TV Library: full-width Explore header, right-side actions, TV empty state, and poster wall after scan.
- [x] Rework the Windows Details first screen toward Android TV: poster click opens Details directly, with a large backdrop/poster hero, title context, plot, Play, and Back-to-poster-wall actions.
- [x] Rework the Windows Player first screen toward Android TV: rail-free playback stage, top return action, centered transport controls, bottom timeline/status chips, and advanced mpv/RIFE settings below.
- [ ] Add a tiny generated media sample or documented local sample path for mpv launch smoke.
- [ ] Extract remaining playback launch/config presenter logic out of the Compose entry.

Verification:

```powershell
.\gradlew.bat :desktop-app:installDist -PbundleMpvRuntime=false
.\tools\capture-desktop-ui.ps1
.\tools\smoke-desktop-local-source-ui.ps1
.\tools\smoke-desktop-local-source-ui.ps1 -LibraryRoot 'D:\Software\dufs'
.\gradlew.bat checkDesktopComposeOnly checkUiPaletteDrift :desktop-app:test
```

### Phase 2: Prove Media Management Parity On Windows

- [x] GUI smoke: add/open Local source and scan a fixture with NFO metadata.
- [x] GUI smoke: inspect details by clicking a poster-wall item and select scanned local media for player handoff.
- [x] GUI smoke: repeat the local source flow against `D:\Software\dufs`.
- [ ] GUI smoke: search scanned local index from the query field in the poster-wall layout.
- [ ] GUI smoke: add/open WebDAV source using a local/loopback fixture where possible.
- [ ] GUI smoke: add/open SMB source when a Windows fixture share is available.
- [ ] Confirm clear-source-index and remove-source flows keep repository state consistent.
- [ ] Review Android TV detail/player screens against desktop first screens and record deeper parity gaps.

Verification:

```powershell
.\gradlew.bat :media-source-desktop:test :scanner-desktop:test :repository-desktop:test :desktop-app:test
.\tools\capture-desktop-ui.ps1
.\tools\smoke-desktop-local-source-ui.ps1
.\tools\smoke-desktop-local-source-ui.ps1 -LibraryRoot 'D:\Software\dufs'
```

### Phase 3: Prove Playback And Progress

- [ ] Build or document a tiny local video fixture for repeatable mpv launch smoke.
- [ ] Run mpv launch from the Windows GUI against the fixture.
- [ ] Verify Pause, -10s, +30s, Stop, and recent-progress refresh.
- [ ] Verify remote playback bridge keeps credentials out of mpv command lines.
- [ ] Keep RIFE optional and make missing runtime errors actionable in the UI.

Verification:

```powershell
.\gradlew.bat :player-mpv:test :desktop-app:test
.\gradlew.bat :desktop-app:smokeMpvRuntime -PrequireMpvRuntime=true
# Full RIFE playback/interpolation smoke is target-hardware validation, not a blocker on low-capability local hosts.
```

### Phase 4: Prove CloudDrive2/RSS

- [ ] Run token/path/RSS dry-run against a real CloudDrive2 endpoint and save token-free report.
- [ ] Run explicit-confirmation live submit with a low submit limit and save token-free report.
- [ ] Validate organizer move behavior against real inbox/library paths.
- [ ] Validate scheduler behavior over real elapsed time.
- [ ] Verify post-sync source rescan updates desktop index.

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

- [ ] Build lightweight Windows install for GUI QA.
- [ ] Build full Windows distribution with bundled runtime source.
- [ ] Run `mpv.exe --version` smoke from packaged runtime.
- [ ] Run RIFE matrix on target hardware; DirectML is target-host validation, NVIDIA depends on CUDA/TensorRT driver compatibility, Standard requires a plugin decision.
- [ ] Keep CI green for Android and desktop/shared JVM gates.

Verification:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat checkDesktopComposeOnly checkDesktopPresenterSeparation checkUiPaletteDrift `
  :core:model:test :repository-api:test :player-mpv:test `
  :cloud-drive-desktop:test :sync-engine-desktop:test :desktop-app:test `
  :desktop-app:installDist -PbundleMpvRuntime=false
.\gradlew.bat :desktop-app:distZip -PmpvRuntimeSource=runtime\mpv -PrequireMpvRuntime=true -PrunMpvSmoke=true
```

## Immediate Next Actions

1. Continue narrowing desktop-vs-Android-TV UI gaps beyond the Library, Details, and Player first screens, especially Settings, source management, and navigation shape.
2. Re-add a poster-wall-layout search GUI smoke without making the source controls dominate the Library view.
3. Extract remaining mpv launch preparation from `MiruPlayDesktopComposeApp.kt`.
4. Add WebDAV and SMB GUI fixture smokes where local loopback fixtures are practical.
