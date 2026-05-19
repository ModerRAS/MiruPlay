# MiruPlay Windows Port Roadmap

This document is the working checklist for finishing the Windows desktop port
without losing Android TV behavior. Keep it current as work lands.

## Definition Of Done

The port is complete only when all of these are proven by current evidence:

| Area | Required end state | Proof required |
|---|---|---|
| Android TV | Existing Android TV app still builds and keeps Compose/Compose TV UI. | `:app:assembleDebug`, and later a TV-device smoke run for core library/detail/player flows. |
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
| Compose Desktop entry | Usable foundation | Swing production shell removed; screenshot QA exists for first screens. Latest local GUI smoke generated `build/desktop-ui-qa/library.png`, `details.png`, `player.png`, and `settings.png`. |
| Shared UI palette | Covered structurally | `:ui-design` owns shared palette; drift check exists. |
| Local/WebDAV/SMB desktop sources | Implemented | Need GUI interaction smoke against real/local fixtures. |
| Library/index/details | Implemented foundation | Need fixture-based GUI flow and broader parity review. |
| Bangumi metadata | Implemented foundation | Unit coverage exists; live network behavior needs manual/smoke evidence. |
| mpv playback | Implemented foundation | Need repeatable GUI launch smoke against a tiny local media sample. |
| RIFE runtime | Partial | DirectML smoke has been proven locally before; NVIDIA and Standard remain target-host dependent. |
| Cloud/RSS | Partial | Loopback tests exist; real CloudDrive2 dry-run/live evidence still open. |
| Release packaging | Partial | Lightweight install works; full bundled runtime artifact QA remains open. |

## Work Plan

### Phase 1: Stabilize The Existing Windows App

- [x] Keep Windows UI on Compose Desktop only.
- [x] Add screenshot QA for Library, Details, Player, and Settings.
- [x] Share media-source connection field conventions across desktop modules.
- [x] Run the current Windows GUI screenshot smoke locally.
- [ ] Add a fixture-driven Windows GUI smoke that creates/uses a local media source, navigates sections, checks runtime status, and records screenshots.
- [ ] Add a tiny generated media sample or documented local sample path for mpv launch smoke.
- [ ] Extract remaining playback launch/config presenter logic out of the Compose entry.

Verification:

```powershell
.\gradlew.bat :desktop-app:installDist -PbundleMpvRuntime=false
.\tools\capture-desktop-ui.ps1
.\gradlew.bat checkDesktopComposeOnly checkUiPaletteDrift :desktop-app:test
```

### Phase 2: Prove Media Management Parity On Windows

- [ ] GUI smoke: add/open Local source, scan, search, inspect details, select for playback.
- [ ] GUI smoke: add/open WebDAV source using a local/loopback fixture where possible.
- [ ] GUI smoke: add/open SMB source when a Windows fixture share is available.
- [ ] Confirm clear-source-index and remove-source flows keep repository state consistent.
- [ ] Review Android TV library/detail/player screens against desktop first screens and record parity gaps.

Verification:

```powershell
.\gradlew.bat :media-source-desktop:test :scanner-desktop:test :repository-desktop:test :desktop-app:test
.\tools\capture-desktop-ui.ps1
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
.\tools\smoke-mpv-rife.ps1 -RuntimeRoot .\runtime\mpv -Backend DIRECTML -ReportPath .\build\mpv-smoke\rife-directml-report.json
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
- [ ] Run RIFE matrix on target hardware; DirectML is required, NVIDIA is target-host validation, Standard requires a plugin decision.
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

1. Extend the GUI smoke toward a fixture-driven local source flow.
2. Extract remaining mpv launch preparation from `MiruPlayDesktopComposeApp.kt`.
3. Add or document a tiny mpv launch fixture and run a GUI playback smoke.
4. Commit and push each verified slice with the exact commands used.
