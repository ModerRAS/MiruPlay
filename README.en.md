# MiruPlay

> Android TV + Windows Desktop Anime Media Manager — local scraping, cloud sync, multi-source playback

[简体中文](./README.md) | [日本語](./README.ja.md)

## Current Status

- Android TV remains the primary entry point, using Jetpack Compose TV, Media3, and Hilt.
- The Windows desktop port now has a Compose Desktop shell, Local/WebDAV/SMB sources, Bangumi metadata, CloudDrive2/RSS settings, WebUI access, mpv playback, and optional RIFE runtime support.
- Windows completion is tracked in the [Windows port roadmap](./docs/windows-port-roadmap.md). Real CloudDrive2/RSS live QA, target-hardware RIFE matrix evidence, signed installer evidence, and broader device end-to-end QA still require the matching external environments.

## Features

- **Multi-Source Media** — Supports local files, WebDAV, SMB, and other media sources
- **Metadata Scraping** — Auto-fetch anime info, posters, and episode lists from Bangumi
- **RSS Cloud Sync** — Automatically sync new episodes via RSS subscription
- **Remote Control** — Built-in HTTP server + gRPC API for browser / third-party client control
- **TV Remote Optimized** — Compose + Leanback UI fully adapted for D-pad navigation
- **Windows Desktop** — Compose Desktop UI, mpv playback, optional RIFE backends, and desktop JSON storage
- **Playback Progress** — Track and resume playback progress per episode

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin 2.0.0 |
| UI | Jetpack Compose TV + Compose Desktop |
| DI | Hilt (Android) |
| Database | Room (Android) + JSON store (Windows desktop) |
| Player | Media3 (ExoPlayer, Android) + mpv (Windows) |
| Network | OkHttp + NanoHTTPD + gRPC + Protobuf |
| Serialization | Kotlinx Serialization |

## Build

**Requirements:**
- JDK 21 (Temurin)
- Android SDK 35

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing)
./gradlew assembleRelease -PVERSION_NAME=1.0.0 -PVERSION_CODE=100

# Run tests
./gradlew test

# Lint
./gradlew lint

# Lightweight Windows desktop install without copying a local mpv runtime
./gradlew :desktop-app:installDist -PbundleMpvRuntime=false

# Safe local Windows port gate
powershell -ExecutionPolicy Bypass -File tools/verify-windows-port.ps1
```

## Project Structure

```
MiruPlay/
├── app/              # App entry, navigation, Hilt wiring
├── core/
│   ├── model/       # Domain models, data classes
│   └── common/      # Shared utilities, Result type
├── data/            # Room database, DAOs, repositories
├── ui-tv/           # TV Compose UI (screens, components, theme)
├── ui-design/       # Shared Android TV / Windows palette and input contracts
├── desktop-app/     # Windows Compose Desktop entry, settings, WebUI bridge
├── player-core/     # Media3 player integration
├── player-mpv/      # Windows mpv commands, IPC, runtime verification
├── media-source-api/# Cross-platform media source contracts
├── media-source/    # Android media source implementation
├── media-source-desktop/ # Windows Local / WebDAV / SMB sources
├── repository-api/  # Cross-platform repository and presentation helpers
├── repository-desktop/ # Windows JSON-backed repository
├── scanner/         # Local file scanner
├── scanner-desktop/ # Windows scanner
├── scraper/         # Anime metadata scraping (Bangumi)
├── scraper-core/    # Shared scraper contracts and Bangumi mapping
├── scraper-desktop/ # Windows Bangumi client
├── sync-engine/     # RSS sync engine
├── sync-engine-shared/ # Shared Cloud/RSS actions and directory browsing
├── sync-engine-desktop/ # Windows CloudDrive2/RSS runner and scheduler
├── cloud-drive/     # Cloud drive integration
├── cloud-drive-api/ # Shared CloudDrive contracts
├── cloud-drive-desktop/ # Windows CloudDrive2 gRPC client
├── metadata/        # NFO metadata read/write
├── metadata-core/   # Shared NFO parsing/writing
├── web-control/     # HTTP control server
├── web-control-core/# Shared WebUI HTTP routing and DTOs
├── runtime/mpv/     # Local mpv/RIFE payload placeholder; large files are not committed
└── gradle/          # Dependency version catalog
```

## Documentation

- [Windows port roadmap](./docs/windows-port-roadmap.md) — Windows completion status, required evidence, and remaining live/target-host QA
- [Windows port plan](./docs/windows-port-plan.md) — architecture split, desktop entry, mpv/RIFE packaging strategy
- [Windows port audit](./docs/windows-port-audit.md) — covered evidence and risk audit
- [mpv runtime packaging](./docs/mpv-runtime-packaging.md) — Windows mpv/RIFE runtime preparation and release gates

## License

[GNU General Public License v3.0](./LICENSE)
