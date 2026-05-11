# MiruPlay

> Android TV Anime Media Manager — local scraping, cloud sync, multi-source playback

[简体中文](./README.md) | [日本語](./README.ja.md)

## Features

- **Multi-Source Media** — Supports local files, WebDAV, SMB, and other media sources
- **Metadata Scraping** — Auto-fetch anime info, posters, and episode lists from Bangumi
- **RSS Cloud Sync** — Automatically sync new episodes via RSS subscription
- **Remote Control** — Built-in HTTP server + gRPC API for browser / third-party client control
- **TV Remote Optimized** — Compose + Leanback UI fully adapted for D-pad navigation
- **Playback Progress** — Track and resume playback progress per episode

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin 2.0.0 |
| UI | Jetpack Compose + TV Material |
| DI | Hilt |
| Database | Room |
| Player | Media3 (ExoPlayer) |
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
├── player-core/     # Media3 player integration
├── media-source/    # Media source abstraction (Local / WebDAV / SMB)
├── scanner/         # Local file scanner
├── scraper/         # Anime metadata scraping (Bangumi)
├── sync-engine/     # RSS sync engine
├── cloud-drive/     # Cloud drive integration
├── metadata/        # NFO metadata read/write
├── web-control/     # HTTP control server
└── gradle/          # Dependency version catalog
```

## License

[GNU General Public License v3.0](./LICENSE)
