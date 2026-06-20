# PROJECT KNOWLEDGE BASE

**Generated:** 2026-05-11
**Commit:** e1422c5
**Branch:** master

## OVERVIEW
MiruPlay — Android TV anime media manager. Multi-module Kotlin/Compose app with web scraping (Bangumi), cloud sync (RSS), and multiple media sources (local, WebDAV, SMB).

## STRUCTURE
```
MiruPlay/
├── app/                     # Application entry point, Hilt setup, navigation
├── core/
│   ├── model/              # Domain models, data classes, serialization
│   └── common/             # Shared Result type, error handling (pureKotlin)
├── data/                   # Room DB, DAOs, repositories (Hilt-bound)
├── ui-tv/                  # Compose TV UI: screens, components, theme
├── player-core/            # Media3/ExoPlayer integration, MediaSessionService
├── media-source/           # Media source abstraction (local, WebDAV, SMB)
├── scanner/                # Local media file scanner
├── scraper/                # Anime metadata scrapers (Bangumi)
├── sync-engine/            # RSS feed sync, cloud drive automation
├── cloud-drive/            # Cloud drive integration
├── metadata/               # NFO metadata parsing/writing
├── web-control/            # HTTP server (NanoHTTPD) for external control + gRPC
├── gradle/                 # Version catalog (libs.versions.toml), wrapper
└── .github/workflows/      # CI: build, lint, nightly release
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| App entry point | `app/src/main/kotlin/.../MiruPlayApp.kt` | `@HiltAndroidApp`, starts WebControl + RSS scheduler |
| Navigation | `app/src/main/kotlin/.../MainActivity.kt` | Compose NavHost: library → anime → player |
| DI wiring | `app/.../di/AppModule.kt` + `data/.../di/` | OkHttp, Room, repository bindings |
| DB schema | `data/.../db/MiruPlayDatabase.kt` + `data/.../dao/` | Room DB with migrations |
| Repository interfaces | `data/.../repository/` | Media, Metadata, Progress, Index, CloudDriveAutomation |
| Media playback | `player-core/.../player/MiruPlayMediaService.kt` | Media3 ExoPlayer |
| Media sources | `media-source/.../mediasource/` | Adapters: local files, WebDAV, SMB |
| Scrapers | `scraper/.../scraper/BangumiScraper.kt` | Bangumi metadata |
| RSS sync | `sync-engine/.../sync/rss/` | Cloud drive RSS automation |
| Web API | `web-control/.../webcontrol/WebControlServer.kt` | HTTP + gRPC for external control |
| TV UI screens | `ui-tv/.../ui/` | Library, detail, player, settings |
| Theme | `ui-tv/.../ui/theme/Theme.kt` | TV-optimized Compose theme |
| Version catalog | `gradle/libs.versions.toml` | All dependency versions |
| CI/CD | `.github/workflows/ci.yml` | Build, lint, nightly release |

## CODE MAP
| Symbol | Type | Location | Role |
|--------|------|----------|------|
| `MiruPlayApp` | Application | `app/.../MiruPlayApp.kt` | Hilt entry, starts services |
| `MainActivity` | Activity | `app/.../MainActivity.kt` | LEANBACK launcher, Compose NavHost |
| `MiruPlayNavigation` | Composable | `app/.../MainActivity.kt` | Navigation graph (4 routes) |
| `MiruPlayTheme` | Composable | `ui-tv/.../theme/Theme.kt` | TV Compose theme |
| `MediaRepository` | Interface | `data/.../repository/MediaRepository.kt` | Media CRUD + sources |
| `ProgressRepository` | Interface | `data/.../repository/ProgressRepository.kt` | Playback progress tracking |
| `IndexRepository` | Interface | `data/.../repository/IndexRepository.kt` | Media index operations |
| `MetadataRepository` | Interface | `data/.../repository/MetadataRepository.kt` | Metadata operations |
| `MiruPlayDatabase` | Room DB | `data/.../db/MiruPlayDatabase.kt` | Room database (2 migrations) |
| `MediaRepositoryImpl` | Class | `data/.../repository/MediaRepositoryImpl.kt` | Main repo impl |
| `MiruPlayMediaService` | Service | `player-core/.../MiruPlayMediaService.kt` | Media3 session service |
| `BangumiScraper` | Class | `scraper/.../BangumiScraper.kt` | Bangumi metadata scraping |
| `WebControlServer` | Class | `web-control/.../WebControlServer.kt` | HTTP control server |
| `CloudDriveRssScheduler` | Class | `sync-engine/.../CloudDriveRssScheduler.kt` | RSS sync scheduler |
| `AppModule` | Hilt Module | `app/.../di/AppModule.kt` | Singleton OkHttp + Room |
| `RepositoryModule` | Hilt Module | `data/.../di/RepositoryModule.kt` | Repository bindings |
| `Result<T>` | Sealed class | `core/common/.../AppError.kt` | Success/Error result type |

## CONVENTIONS

### Architecture
- **Clean Architecture layers**: `ui-tv` → `data` → (`scraper` | `media-source` | `sync-engine`)
- **Interface + Impl pattern**: Every repository has interface in same dir, impl prefixed `*Impl`
- **Hilt everywhere**: `@Singleton` repositories, `@AndroidEntryPoint` activity, `@HiltAndroidApp` application
- **Kotlin-only**: No Java source files in the project

### Naming
- Test methods: backtick-quoted descriptive names (`` fun `addSource should return valid id` ``)
- DAO methods: standard Room conventions (`@Query`, `@Insert`, `@Upsert`)
- Screen files: `*Screen.kt` composables, `*ViewModel.kt` for state holders

### Data Flow
- Repositories return `Result<T>` (sealed: `Success` / `Error`)
- Room DAOs return `Flow<List<T>>` for reactive observation
- ViewModels use `StateFlow` for UI state

### Web Control Parity
- **Settings/menu changes must sync WebAPI + WebUI**: When adding, removing, or changing settings-related entries, menu items, toggles, forms, or configuration fields in the TV app/settings flow, update the matching Web Control API contract and the WebUI presentation in the same change.
- **Touch all three layers when needed**: Check TV settings UI/state (`ui-tv`), Web API models/routes/service wiring (`web-control-core` / `web-control`), and WebUI display/form submission (`web-control/frontend/src/App.vue` or related frontend files).
- **No silent defaults for new fields**: If a new setting/menu field exists in app-side state, do not rely on server/client defaults to "probably match". Plumb it explicitly through request/response models and make it visible/editable in WebUI when that setting should be remotely inspectable or configurable.
- **Verification expectation**: For settings/menu changes, verify at least one API path and one WebUI path still round-trip the new field correctly.

### Testing
- **JUnit 4 + MockK + Turbine** for Flow testing
- **Robolectric 4.12** for Android-dependent unit tests
- **Inline fakes/stubs**: Private classes defined in test files
- **`runBlocking`** wraps suspend function tests
- Test run: `./gradlew test`

## ANTI-PATTERNS (THIS PROJECT)
- **System directories in scanner**: `LocalMediaSource.kt` explicitly lists dirs that "should never be traversed" (e.g., `/proc`, `/sys`). Do not add arbitrary directory scanning.
- **No direct DB access outside data module**: Repositories are the only DB access surface. DAOs are package-private to `data`.
- **No `as any` or `@Suppress` for type safety**: Kotlin type system should be respected.

## UNIQUE STYLES

### Test Hook in MainActivity
`MainActivity` checks for `test_local_path` intent extra to auto-add a Local source. This is a developer convenience — do not remove without replacing the test workflow.

### WebDAV URL Encoding
`resolvePlayableUri()` in `MainActivity` auto-detects WebDAV sources and joins remote URLs using `URLEncoder.encode()` with `+`→`%20` replacement. All path segments are individually encoded.

### Nightly CI Versioning
CI generates date-based versions (`YYYY.mm.dd`) for nightly builds. Version properties passed via `-PVERSION_NAME` / `-PVERSION_CODE` Gradle project properties.

### Release Build 自动创建 Tag
`build-release` 任务 push 到 main/master 时会自动构建 release APK。
版本号从 `app/build.gradle.kts` 中 `appVersionName` 的默认值提取 major.minor，patch 用 CI `run_number`。
例如 `appVersionName = "0.1.0"` → 实际版本 `0.1.<run_number>`。
发新版流程：
1. 修改 `app/build.gradle.kts` 中的 `appVersionName` 默认值（如 `0.2.0`）
2. 提交并 push 到 main/master
3. CI 自动构建、打 tag、创建 Release

tag 格式为 `v<major>.<minor>.<run_number>`（如 `v0.2.123`），不会 push 到 origin。

### Agent-managed Version Bump
当用户要求推送到 GitHub（例如要求 `push`、`推 master`、`推到 origin/main`）且本轮改动会进入 `main/master` release 流程时，Codex 需要自行判断是否更新 `app/build.gradle.kts` 中的 `baseAppVersionName`。仅要求本地提交时不要自动改版本号，除非用户明确要求发版或改版本。

决策前必须查询线上已发布版本，不能只依赖本地 tag；优先使用 `gh release list --limit 20`，也可以用 `git ls-remote --tags origin 'refs/tags/v*'` 交叉确认。只参考稳定 release/tag（`v<major>.<minor>.<patch>`），忽略 nightly、pre-release 和非 semver tag。以线上最高稳定版本作为基准。

版本递增规则：
- **Patch 级**：bug fix、性能优化、日志/测试/文档、小型 UI 调整、兼容性内部改动，不修改 `baseAppVersionName` 的 major/minor；CI 会用下一次 `run_number` 生成新的 patch。
- **Minor 级**：用户可见的新功能、主要工作流变化、新设置项、新外部接口或较明显的体验改进，将 `baseAppVersionName` 升到线上最高稳定版本的下一个 minor，并把 patch 写成 `0`（例如线上最高 `v0.1.426`，新功能设为 `"0.2.0"`）。
- **Major 级**：不兼容的数据/配置/API 变化、需要用户手动迁移或可能破坏既有安装行为的改动，将 `baseAppVersionName` 升到下一个 major，并把 minor/patch 写成 `0.0`。

如果线上最高稳定版本的 major/minor 已经高于本地 `baseAppVersionName`，即使只是 patch 级改动，也要先把本地 base 对齐到线上 major/minor，避免发布出较低版本线。不要手工创建 release tag；push 到 `main/master` 后由 CI 负责 tag 和 GitHub Release。提交或推送前，在最终回复里说明本次选择的版本级别和依据。

## COMMANDS
```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease -PVERSION_NAME=1.0.0 -PVERSION_CODE=100

# Test
./gradlew test                          # All unit tests
./gradlew :data:test                    # Single module
./gradlew connectedAndroidTest          # Instrumented tests

# Lint
./gradlew lint
```

## NOTES
- **JDK 21 (Temurin)** required — configured in `gradle.properties` and CI
- **Gradle 8.10**, **AGP 8.6.0**, **Kotlin 2.0.0** with Compose compiler plugin
- **minSdk 28**, **targetSdk 35** — Android TV (Leanback) with optional touchscreen
- **Release builds require signing**: keystore at `miruplay-release.jks` (CI uses base64-encoded secret)
- **`web-control` module** has a frontend (`web-control/frontend/`) with Node.js dependencies — the module has 11K+ total files due to node_modules
- **`tmp/` directory** contains unrelated sub-projects (anime-organizer) — not part of MiruPlay build
- **Gradle parallel + caching** enabled: `org.gradle.parallel=true`, `org.gradle.caching=true`
- **Kotlin code style**: `kotlin.code.style=official`
