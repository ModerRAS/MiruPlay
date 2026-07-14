# Android TV ADB Behavior Tests

This suite pins the Android TV user interaction contract with adb-driven checks. It is intentionally outside instrumentation tests: it installs the debug APK, drives the real app with DPAD keys and taps, dumps UIAutomator XML, captures screenshots, and writes a behavior report under `build/android-tv-behavior`.

## Privacy Boundary

Do not commit real adb targets, IP addresses, device serials, local media paths, account tokens, or private CloudDrive/WebDAV/SMB data.

Repository scripts only accept those values through command-line arguments or environment variables. Keep local private defaults in the global Codex skill or in your shell profile:

```powershell
$env:MIRUPLAY_ANDROID_TV_DEVICE_ID = "<android-tv-device-id>"
```

Behavior reports redact the device id by default. UIAutomator XML and failure summaries redact obvious tokens, private IPs, URL credentials, and local filesystem paths. Passing `-IncludeDeviceIdInReport` is only for local troubleshooting and should not be used for shareable evidence.

## Run

Build the APK first, then run the behavior suite:

```powershell
.\gradlew.bat :app:assembleDebug
.\tools\run-android-tv-behavior.ps1 -DeviceId $env:MIRUPLAY_ANDROID_TV_DEVICE_ID -Tag full -KeepAppData
.\tools\assert-android-tv-behavior-report.ps1 -ReportPath (Get-Content .\build\android-tv-behavior\latest-report.txt -Raw).Trim()
```

Use `-KeepAppData` when the target device contains data that must not be cleared. Without it, the underlying smoke driver clears app data to make the fixture fully isolated.

## Covered Contract

The current full scenario is `android-tv-core`:

- `library-scan`: generated local source, Library scan, fixture poster visibility, and poster-surface DPAD behavior.
- `detail-player-back`: poster activation, Details episode focus, Player launch, no failure overlay, and Back navigation.
- `settings-source`: Library-to-Settings navigation, media-source panel focus, saved source card focus, edit form, and deletion of only the generated source.
- `settings-panels`: Playback, CloudDrive, Scan, and Metadata settings panel traversal.

The runner stores scenario metadata in `tools/android-tv-behavior/scenarios/*.behavior.json`. When UI behavior intentionally changes, update the scenario contract and the adb assertions in the same change.

## Evidence

Each run writes:

- `build/android-tv-behavior/latest-report.txt`
- `build/android-tv-behavior/run-*/report.json`
- nested smoke evidence with privacy-safe screenshots, sanitized UIAutomator XML dumps, and the underlying `android-tv-smoke-report.json`

Credential-bearing panels such as WebUI, CloudDrive, and Metadata do not store screenshots. When `-KeepAppData` is used, settings source-list screenshots are also omitted because they can contain existing private source names or paths.

The assertion script fails if required artifacts are missing or too small, required behavior steps are absent, or reports contain obvious credential/private-network material.
