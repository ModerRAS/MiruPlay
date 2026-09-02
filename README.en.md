# MiruPlay

> Android TV Anime Media Manager — local scraping, cloud sync, multi-source playback

[简体中文](./README.md) | [日本語](./README.ja.md)

## Current Status

Android TV is the primary entry point, built with Jetpack Compose TV, Media3, and Hilt, with both video (anime) and music modes. CI publishes nightly and stable releases (current version line 2.10.x).

## Features

- **Multi-Source Media** — Local files, WebDAV, SMB, and AnimeOrganizer MLIP library.db sources
- **Metadata Scraping** — Auto-fetch anime info, posters, episode lists, and episode comments from Bangumi, with local poster caching
- **Smart Filename Parsing** — Built-in AniFileBERT model (Android ONNX Runtime) parses filenames for scanning, scraping, and cloud auto-organization
- **RSS Cloud Sync** — Auto-sync new episodes via RSS subscription, with CloudDrive2 offline download and auto-organization
- **Music Mode** — Album/track library, playback queue, CUE track splitting and whole-track playback, three-tier SRC sampling-rate bypass
- **Multi-Backend Player** — Media3 (ExoPlayer) primary, with optional embedded mpv / ijkplayer backends; ASS subtitles (libass rendering), external subtitles and audio tracks, preferred subtitle language, resume playback
- **Audio DSP** — REW equalizer (per-channel PEQ), NEON FIR 32/64-bit filtering, FFT analysis
- **Remote Control** — Built-in HTTP server + WebUI + gRPC API for browser / third-party client control, kept in parity with TV-side settings
- **TV Remote Optimized** — Compose + Leanback UI fully adapted for D-pad navigation

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin 2.0.0 |
| UI | Jetpack Compose TV |
| DI | Hilt (Android) |
| Database | Room |
| Player | Media3 (ExoPlayer) + optional mpv / ijkplayer backends |
| Subtitles | ASS subtitle rendering via libass |
| Network | OkHttp + NanoHTTPD + gRPC + Protobuf |
| Serialization | Kotlinx Serialization |
| ML | AniFileBERT (ONNX Runtime) filename parsing |
| Build | Gradle 8.10 + AGP 8.6.0, JDK 21, minSdk 28 / targetSdk 35 |

## Build

**Requirements:**
- JDK 21 (Temurin)
- Android SDK 35

```bash
# Debug build
./gradlew assembleDebug

# Debug build with build number (version shows as 2.10.<BUILD_NUMBER>)
./gradlew assembleDebug -PBUILD_NUMBER=123

# Release build (requires signing)
./gradlew assembleRelease -PVERSION_NAME=2.10.0 -PVERSION_CODE=100

# Run tests
./gradlew test

# Lint
./gradlew lint
```

## Project Structure

```
MiruPlay/
├── app/                   # App entry, navigation, Hilt wiring
├── core/
│   ├── model/            # Domain models, data classes
│   └── common/           # Shared utilities, Result type
├── data/                 # Room database, DAOs, repositories
├── ui-tv/                # TV Compose UI (screens, components, theme)
├── ui-design/            # Shared Android TV palette and input contracts
├── player-core/          # Media3 player integration, music queue, audio DSP runtime
├── player-mpv-android/   # Embedded mpv playback backend
├── player-ijkplayer-android/ # ijkplayer playback backend
├── audio-dsp-core/       # Audio DSP logic (PEQ, etc.)
├── audio-dsp-native/     # NEON FIR / FFT native implementations
├── media-source-api/     # Media source contracts
├── media-source/         # Android media source implementations (local, WebDAV, SMB, MLIP)
├── repository-api/       # Repository and presentation helpers
├── scanner/              # Local file scanner (incl. audio directory classification, tag reading)
├── scraper/              # Anime metadata scraping (Bangumi)
├── scraper-core/         # Shared scraper contracts and Bangumi mapping
├── sync-engine/          # RSS sync engine
├── sync-engine-shared/   # Shared Cloud/RSS actions and directory browsing
├── cloud-drive/          # Cloud drive integration
├── cloud-drive-api/      # Shared CloudDrive contracts
├── cloud-drive-core/     # Shared CloudDrive logic
├── metadata/             # NFO metadata read/write
├── metadata-core/        # Shared NFO parsing/writing
├── translation/          # Translation helpers
├── background-task/      # Background tasks
├── web-control/          # HTTP control server + WebUI frontend
├── web-control-core/     # Shared WebUI HTTP routing and DTOs
└── gradle/               # Dependency version catalog
```

## Documentation

- [BERT Filename Parsing & Scanner Integration](./docs/anime-filename-parser.md) — AniFileBERT training artifacts, Android ONNX runtime, scanning flow, folder/filename responsibilities, ADB verification steps
- [AniFileBERT Maintenance](./docs/anifilebert-maintenance.md) — relationships between MiruPlay / AniFileBERT / AnimeName repos, data update, retraining, publishing workflow
- [CloudDrive2 RSS Offline Download](./docs/cloud-drive-rss-offline-download.md) — RSS fetching, offline download, auto-organization flow
- [Media Source Content Mode](./docs/media-source-content-mode.md) — media source content modes (video/music/mixed)
- [Metadata Search Aggregation Refactor](./docs/metadata-search-aggregation-refactor.md) — metadata search aggregation refactor record
- [Android TV Player Controls](./docs/android-tv-player-controls.md) — user-approved interaction contract for playback controls
- [Android TV ADB Behavior Tests](./docs/android-tv-behavior-tests.md) — adb-driven TV interaction behavior test suite

Additional engineering docs live under `docs/agents/` (agent workflow checklists), `docs/verification/` (real-device verification records), and `docs/workflows/` (release verification flows).

## License

[GNU General Public License v3.0](./LICENSE)