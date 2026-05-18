# Android Source Isolation Audit

Generated: 2026-05-18

Policy from the current thread: Android TV is the known-good implementation.
Future Windows port work should treat Android code as reference material and
avoid editing Android source unless explicitly approved.

## Current Scope

Base comparison: `master...HEAD`

Android-facing cumulative diff:

- 48 files changed
- 1,492 insertions
- 427 deletions

Current worktree noise:

- `cloud-drive/build.gradle.kts`
- `cloud-drive/src/main/kotlin/com/miruplay/tv/clouddrive/CloudDriveClient.kt`

Those two files are reported by `git status` as modified, but current textual
`git diff` output is empty. Leave them unstaged and do not use them as part of
Windows cleanup commits.

## Classification

### Cross-Platform Wiring

These changes exist to connect Android modules to newly extracted shared APIs.
They are not Windows-only behavior, but they do touch Android module build
surfaces.

Files:

- `app/build.gradle.kts`
- `data/build.gradle.kts`
- `scanner/build.gradle.kts`
- `sync-engine/build.gradle.kts`
- `web-control/build.gradle.kts`
- `ui-tv/build.gradle.kts`

Decision:

Keep only the minimum needed for compilation while cleanup is in progress. Do
not add more Android module dependencies to support desktop features.

### Shared Interface Extraction

These changes replace Android data interfaces with shared repository-api
interfaces or typealiases. They reduce duplication, but they also moved Android
call sites away from their original imports.

Files:

- `data/src/main/kotlin/com/miruplay/tv/data/repository/*Repository.kt`
- `data/src/main/kotlin/com/miruplay/tv/data/di/RepositoryModule.kt`
- `app/src/main/kotlin/com/miruplay/tv/MainActivity.kt`
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/*/*ViewModel.kt`
- `scanner/src/main/kotlin/com/miruplay/tv/scanner/ScanCoordinator.kt`
- `sync-engine/src/main/kotlin/com/miruplay/tv/sync/*.kt`
- `sync-engine/src/main/kotlin/com/miruplay/tv/sync/rss/CloudDriveRssAutomationEngine.kt`
- `web-control/src/main/kotlin/com/miruplay/tv/webcontrol/WebControlService.kt`

Decision:

Do not expand this refactor. If Android source stability becomes the priority,
prefer restoring Android imports and keeping shared interfaces consumed only by
desktop/new shared modules. If keeping these changes, require Android debug
build plus targeted scanner/sync/web-control tests before claiming safety.

### Shared Display And Route Helpers

These changes make Android UI read palette, route, display-title, and playback
formatting helpers from shared modules.

Files:

- `app/src/main/kotlin/com/miruplay/tv/navigation/NavRoutes.kt`
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/theme/Theme.kt`
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/components/AnimeCards.kt`
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/detail/AnimeDetailScreen.kt`
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/library/LibraryScreen.kt`
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/player/PlayerScreen.kt`

Decision:

Useful for long-term UI parity, but it violates the user's current boundary if
continued. Future parity work should update shared constants and desktop usage
without editing Android UI files.

### Android Behavior Changes

These are not required just to add a Windows desktop port. They change Android
runtime behavior and carry the highest regression risk.

Files and risks:

- `app/src/main/AndroidManifest.xml`: backup policy changed from enabled to
  disabled.
- `app/src/main/kotlin/com/miruplay/tv/MiruPlayApp.kt`: WebControl startup now
  depends on a preference listener.
- `ui-tv/src/main/kotlin/com/miruplay/tv/ui/settings/AddSourceScreen.kt` and
  `SettingsViewModel.kt`: WebUI enable/disable and token rotation UI added.
- `data/src/main/kotlin/com/miruplay/tv/data/preferences/WebControlPreferencesManager.kt`:
  new WebControl preference store.
- `data/src/main/kotlin/com/miruplay/tv/data/secure/SecurePreferencesManager.kt`:
  Cloud/Bangumi credential interface extraction, WebControl token generation,
  and media-source password storage behavior changed.
- `data/src/main/kotlin/com/miruplay/tv/data/repository/MediaRepositoryImpl.kt`:
  media-source password persistence changed from DB Base64 field to encrypted
  preferences with legacy migration on read.
- `web-control/src/main/kotlin/com/miruplay/tv/webcontrol/WebControlServer.kt`:
  API token authorization, cookie handling, disabled state, and CORS headers
  changed.
- `web-control/src/main/kotlin/com/miruplay/tv/webcontrol/WebControlService.kt`:
  local directory browsing is restricted to detected roots.
- `player-core/src/main/kotlin/com/miruplay/tv/player/ExoPlaybackController.kt`:
  playback state null handling and track extraction changed.
- `metadata/src/main/kotlin/com/miruplay/tv/metadata/MetadataManager.kt`:
  NFO lookup now returns null when a parent directory is missing.
- `media-source/src/main/kotlin/com/miruplay/tv/mediasource/WebDavMediaSource.kt`:
  OkHttp request body API migrated.

Decision:

Treat these as candidates for isolation or revert before the port is considered
clean. Some may be independently valuable security or correctness changes, but
they should be reviewed as Android changes, not smuggled in as Windows-port
collateral.

### Generated/Test Support

Files:

- `data/schemas/com.miruplay.tv.data.db.MiruPlayDatabase/4.json`
- `data/src/test/kotlin/com/miruplay/tv/data/db/MiruPlayDatabaseMigrationTest.kt`
- Android module test updates that follow repository-api type changes

Decision:

Only keep schema/test updates that correspond to approved Android behavior or
API changes. Do not use tests as justification for broad Android rewrites.

## Cleanup Order

1. Freeze Android source edits.
2. Continue extraction by reading Android implementation as the reference, then
   adding or improving shared/desktop code without changing Android callers.
3. Pick one of two Android outcomes before final delivery:
   - restore Android source close to `master`, keeping only build wiring needed
     for shared modules, or
   - explicitly approve and verify the Android refactor/security changes as a
     separate Android change set.
4. For every cleanup commit, stage only the intended shared/desktop/docs files.
5. Required local verification before claiming safety:
   - `:app:assembleDebug`
   - `:core:model:test`
   - affected desktop/shared module tests
   - existing guard tasks such as `checkDesktopComposeOnly` and
     `checkUiPaletteDrift`

## Immediate Next Step

The next code change should be in shared/desktop only. A good small target is
the remaining desktop HTTP range header formatting around `StreamRange`, because
it continues the range abstraction already extracted from desktop playback and
media-source code while leaving Android untouched.
