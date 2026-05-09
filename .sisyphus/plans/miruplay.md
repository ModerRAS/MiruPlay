# MiruPlay — Android TV 动漫播放器 工作计划

## TL;DR

> **概述**：从零构建一个生产级 Android TV 动漫播放器，支持本地/远程（WebDAV/SMB）媒体源、NFO 元数据解析与写入、观看进度同步、DPAD 优先的 TV UI。
>
> **交付物**：
> - 11 个 Gradle 模块（7 业务 + 4 支撑，Clean Architecture）
> - 完整的 ExoPlayer 封装 + MediaSession
> - Fake/WebDAV/SMB 三种 MediaSource 实现
> - 剧集扫描器（多命名模式识别）
> - Kodi NFO 解析器 + 写入器
> - AniList 元数据抓取器（可插拔接口）
> - 观看进度同步引擎（Room ↔ NFO）
> - Compose for TV UI（首页/详情/播放器/设置）
>
> **预估工作量**：Large
> **并行执行**：YES — 5 个 Wave，每个 3-7 个任务
> **关键路径**：T1 → T2 → T5 → T8 → T10 → T14 → T19 → F1-F4 → 用户确认

---

## 上下文

### 原始需求

构建一个 Android TV 动漫播放器 App（Kotlin），核心能力：
- 播放动漫/视频内容
- 连接远程媒体库（WebDAV、SMB，可选 NFS）
- 扫描并索引媒体文件
- 解析和生成 NFO 元数据
- NFO 缺失时支持元数据抓取
- 追踪观看进度并写回 NFO
- 为 TV 设计（DPAD 导航、10-foot UI）

### 实现策略：完整实现（非最小可用）

> 用户明确要求：**不要做最小可用实现，直接按生产质量完整实现**。每个模块在实现时就是完整的，不预留 TODO。
> 但任务仍按 9 步递增构建：每个 Wave 完成一组模块后，后续 Wave 在此基础上叠加。

### 访谈摘要

**已确认决策**：
| 决策项 | 选择 |
|--------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose for TV（tv-material 1.0.0） |
| 播放器 | Media3 ExoPlayer 1.10.0 |
| 架构 | Clean Architecture + MVVM |
| 异步 | Coroutines + Flow |
| DI | Hilt 2.52 |
| 序列化 | Kotlinx Serialization |
| 持久化 | Room |
| 测试策略 | **TDD（测试驱动开发）** |
| minSdk | **28 (Android 9.0)** — Dolby Vision 不需要，HDR10/HEVC 在 API 21+ 即可工作 |
| 包名 | com.miruplay.tv |
| 许可证 | GPL v3 |
| 刮削数据源 | **AniList GraphQL** + **Bangumi Archive**（参考 anime-organizer 的离线优先设计） |
| 实现哲学 | **完整实现**：每模块按生产质量一次到位，不预留 TODO |

**研究关键发现**：
- `com.google.android.exoplayer2` 已废弃，必须使用 `androidx.media3`
- Compose for TV 的 `tv-material` 和 `tv-foundation` 已是稳定版（1.0.0）
- TV 必需 `MediaSessionService`（Now Playing 卡片）
- 参考项目：LitPlayer（SMB/WebDAV 支持）、AsukaPlayer（多模块 Clean Architecture）、M3UAndroid（TV Compose 模式）
- Gradle Version Catalog + Convention Plugin 是多模块项目标准方案
- Hilt 多模块需启用 `hilt.enableExperimentalClasspathAggregation=true`

### Metis 审查

Metis 识别了以下缺口并已在计划中处理：

| 缺口 | 处理方式 |
|------|----------|
| 字幕策略未明确 | 计划中指定：外部 ASS/SRT 优先于内嵌，使用 ExoPlayer SubtitleView（不实现自定义渲染器），记录 ASS 复杂排版限制 |
| Scraper API 未选定 | 默认使用 **AniList GraphQL**（无需认证、动漫覆盖好、文档完善） |
| 多用户 vs NFO 冲突 | 规则：NFO 时间戳 > Room 时间戳 → NFO 胜出；否则 Room 胜出 |
| 首次启动无源配置 | 计划中强制包含「添加源」设置页（T12） |
| minSdk 34 可能过高 | **标记为需注意**：API 34 排除大量活跃 TV 设备（多数运行 Android 11-13）。除非有特定 API 34 功能需求，建议降为 26-28。用户在计划中最终决定 |
| 测试策略需按模块区分 | 已指定：接口层纯单元 TDD、I/O 层单元+集成测试、UI 层 Compose 测试 |

### 范围边界

**包含（IN）**：
- 7 个模块（player-core, media-source, scanner, metadata, scraper, sync-engine, ui-tv）
- 本地 Fake 源 + WebDAV 源 + SMB 源
- Kodi 格式 NFO 完整解析/写入
- AniList 抓取器（单个实现）
- Room 本地缓存 + 观看进度
- DPAD 导航 TV UI（4 个屏幕）
- MediaSessionService（TV Now Playing）
- ProGuard/R8 混淆规则

**明确排除（OUT）**：
- 自定义 ASS/SSA 字幕渲染器（使用 ExoPlayer 内置）
- 视频转码/ffmpeg
- 云端同步（Firebase/自定义后端）
- 用户认证/账号系统
- 社交功能（评分/推荐/分享）
- Google Cast 发送/接收
- 动态插件系统
- 离线下载/缓存管理
- 实时文件监控（仅手动/打开时扫描）
- 手势支持（仅 DPAD）
- 家长控制

---

## 工作目标

### 核心目标

构建一个可直接在 Android TV 上使用的动漫播放器，支持从远程存储（WebDAV/SMB）流式播放，自动管理元数据和观看进度。

### 具体交付物

- 可编译运行的 Android TV APK
- `./gradlew assembleDebug` 全部模块编译通过
- 所有 TDD 测试通过（单元 + 集成 + Compose UI）
- `adb shell dumpsys media_session` 可见 MiruPlay 会话

### 完成定义

- [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
- [ ] `./gradlew testDebugUnitTest` → 所有模块测试通过
- [ ] App 安装到 TV 模拟器后可：添加源 → 浏览媒体库 → 播放视频 → 进度自动保存

---

## 验证策略（强制）

> **零人工干预** — 所有验证由 Agent 执行。禁止 "用户手动确认" 类验收标准。

### 测试决策
- **测试基础设施**：不存在（新建项目）
- **自动化测试**：**TDD** — 每个任务：RED（先写失败测试）→ GREEN（最小实现）→ REFACTOR
- **框架**：JUnit 5 + MockK + Turbine (Flow 测试) + Compose Testing
- **项目创建时一并搭建**：T1 中配置所有测试依赖

### QA 策略
每个任务必须包含 Agent 可执行的 QA 场景：
- **构建验证**：`./gradlew assembleDebug`、`./gradlew testDebugUnitTest`
- **运行时验证**：`adb logcat`、`adb shell dumpsys media_session`、`adb shell input keyevent`（DPAD 模拟）
- **UI 验证**：Compose Testing（`createComposeRule()`）+ 截图
- **API 验证**：curl（针对测试 WebDAV 服务器）
- **证据路径**：`.sisyphus/evidence/task-{N}-{slug}.{ext}`

---

## 执行策略

### 并行执行 Wave

```
Wave 1 (立即启动 — 基础设施, 7 任务, MAX PARALLEL):
├── T1: Gradle Wrapper + 根构建文件 [quick]
├── T2: Version Catalog (libs.versions.toml) [quick]
├── T3: Convention Plugin (build-logic) [quick]
├── T4: 所有模块目录 + build.gradle.kts [quick]
├── T5: core-model 领域模型 [quick]
├── T6: core-common 错误模型 + 工具类 [quick]
└── T7: ProGuard/R8 + 资源脚手架 [quick]

Wave 2 (依赖 Wave 1 — 接口层, 8 任务, MAX PARALLEL):
├── T8: MediaSource 接口 + 能力模型 [quick]
├── T9: Scanner 接口 [quick]
├── T10: Metadata 接口 (NFO 解析/写入) [quick]
├── T11: Scraper 接口 [quick]
├── T12: PlayerCore 接口 [quick]
├── T13: SyncEngine 接口 [quick]
├── T14: Repository 接口 (数据层) [quick]
└── T15: Room 数据库 + DAO + 实体 [quick]

Wave 3 (依赖 Wave 2 — 数据层+播放器+DI, 7 任务, HIGH PARALLEL):
├── T16: FakeMediaSource 实现 (TDD) [deep]
├── T17: LocalRepository 实现 (TDD) [deep]
├── T18: MetadataRepository 实现 (TDD) [deep]
├── T19: ProgressRepository 实现 (TDD) [deep]
├── T20: PlayerCore ExoPlayer 封装 (TDD) [deep]
├── T21: MediaSessionService 集成 [deep]
└── T22: Hilt DI 模块配置 [quick]

Wave 4 (依赖 Wave 3 — UI+扫描+元数据, 12 任务, MAX PARALLEL):
├── T23: TV UI 主题 + 设计令牌 + 公共组件 [visual-engineering]
├── T24: UI — 添加源/设置页 [visual-engineering]
├── T25: UI — 媒体库列表页 (首页) [visual-engineering]
├── T26: UI — 剧集详情页 [visual-engineering]
├── T27: UI — 播放器页 [visual-engineering]
├── T28: UI — 导航 + MainActivity + App 装配 [visual-engineering]
├── T29: Scanner — 剧集命名模式识别 (TDD) [deep]
├── T30: Scanner — 目录遍历 + 索引引擎 (TDD) [deep]
├── T31: Scanner 集成 Repository [deep]
├── T32: Metadata — NFO 解析器 (TDD) [deep]
├── T33: Metadata — NFO 写入器 (TDD) [deep]
└── T34: Metadata — ViewModel 层集成 [deep]

Wave 5 (依赖 Wave 4 — 远程源+同步, 9 任务, HIGH PARALLEL):
├── T35: AniList Scraper 实现 [unspecified-high]
├── T36: WebDAV MediaSource (TDD) [unspecified-high]
├── T37: SMB MediaSource (TDD) [unspecified-high]
├── T38: RemoteRepository 适配层 [unspecified-high]
├── T39: SyncEngine — 进度同步核心 (TDD) [deep]
├── T40: SyncEngine — 冲突检测与解决 (TDD) [deep]
├── T41: SyncEngine — 定期自动同步调度 [deep]
├── T42: 全局错误处理 + 网络故障恢复 [deep]
└── T43: 集成测试 + 边缘情况修复 [deep]

Wave FINAL (所有实现完成后 — 4 个并行审查，然后等待用户确认):
├── F1: 计划合规审计 [oracle]
├── F2: 代码质量审查 [unspecified-high]
├── F3: 真实 QA 验证 [unspecified-high]
└── F4: 范围一致性检查 [deep]
→ 汇总结果 → 获取用户明确确认

关键路径: T1 → T3 → T8 → T16 → T20 → T22 → T25 → T27 → T28 → T31 → T36 → T37 → T39 → T42 → F1-F4 → 用户确认
并行加速: 相比串行执行约节省 65%
最大并发: 12 (Wave 4)
```

### Agent 分派摘要

- **Wave 1**: 7 — T1→`quick`, T2→`quick`, T3→`quick`, T4→`quick`, T5→`quick`, T6→`quick`, T7→`quick`
- **Wave 2**: 8 — T8→`quick`, T9→`quick`, T10→`quick`, T11→`quick`, T12→`quick`, T13→`quick`, T14→`quick`, T15→`quick`
- **Wave 3**: 7 — T16→`deep`, T17→`deep`, T18→`deep`, T19→`deep`, T20→`deep`, T21→`deep`, T22→`quick`
- **Wave 4**: 12 — T23→`visual-engineering`, T24→`visual-engineering`, T25→`visual-engineering`, T26→`visual-engineering`, T27→`visual-engineering`, T28→`visual-engineering`, T29→`deep`, T30→`deep`, T31→`deep`, T32→`deep`, T33→`deep`, T34→`deep`
- **Wave 5**: 9 — T35→`unspecified-high`, T36→`unspecified-high`, T37→`unspecified-high`, T38→`unspecified-high`, T39→`deep`, T40→`deep`, T41→`deep`, T42→`deep`, T43→`deep`
- **FINAL**: 4 — F1→`oracle`, F2→`unspecified-high`, F3→`unspecified-high`, F4→`deep`

---

- [x] 5. core-model 领域模型

  **做什么**：
  - 在 `core-model` 模块（纯 Kotlin，无 Android 依赖）中创建完整领域模型
  - `Anime`：id, title (日文原名), titleCn (中文名), summary, genres, studio, director, episodeCount, airDate, rating, bangumiId, anilistId, tmdbId, posterUrl, fanartUrl
  - `Season`：seasonNumber, title, episodeCount, episodes (List<Episode>)
  - `Episode`：id, animeId, seasonNumber, episodeNumber, title, filePath, fileName, duration, watchedPosition (ms), lastWatchedTimestamp, playCount, thumbnailPath
  - `PlaybackState`（sealed class）：Idle, Loading(source), Playing(source, position), Paused(source, position), Buffering(source, position), Ended(source), Error(source, error)
  - `MediaSourceInfo`：id, name, type (LOCAL/WEBDAV/SMB), connectionInfo (url, username, etc.), isConnected, lastScanned
  - `MediaCapabilities`（data class）：seekable, supportsRange, supportsList, supportsWrite
  - `NfoMetadata`：episode 字段集合（title, season, episode, plot, premiered, rating, playcount, lastplayed, resumePosition, uniqueIds 等）
  - `ScraperResult`：animeId, title, matchedTitle, confidence (Float 0-1), source (ANILIST/BANGUMI)
  - `ScanResult`：animeName, episodesFound, newEpisodes, updatedEpisodes
  - 所有数据类使用 `@Serializable`（kotlinx.serialization）

  **完整实现**（非最小）：
  - 所有字段一次性定义完整，不预留 "未来扩展" 的半成品
  - `Episode` 的 watchedPosition 和 lastWatchedTimestamp 用毫秒精度
  - `PlaybackState` sealed class 覆盖所有 ExoPlayer 状态
  - `MediaSourceInfo.connectionInfo` 支持 `Map<String, String>` 灵活扩展

  **不能做**：
  - 不要引入 Android 框架依赖（保持纯 Kotlin 模块）
  - 不要在 domain model 中混合 UI 状态

  **推荐 Agent Profile**：
  - **Category**: `quick` — 纯数据类定义
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: YES（与 T6 并行）
  - **并行组**: Wave 1
  - **阻塞**: T8-T15（所有接口依赖这些模型）
  - **被阻塞**: T4（需要模块目录存在）

  **参考资料**：
  - AsukaPlayer 的 `player-domain` 模块数据模型（GitHub: qianmokano/Asukaplayer）
  - Kodi NFO 规范：`https://kodi.wiki/view/NFO_files`
  - Kotlinx Serialization 官方文档：`https://github.com/Kotlin/kotlinx.serialization`

  **验收标准**：
  - [ ] 所有数据类编译通过（`./gradlew :core-model:compileKotlin`）
  - [ ] 所有 `@Serializable` 类可序列化/反序列化（round-trip 测试）
  - [ ] `PlaybackState` sealed class 覆盖所有状态：`PlaybackState::class.sealedSubclasses.size >= 7`
  - [ ] `Episode` 的 `watchedPosition` 和 `lastWatchedTimestamp` 支持毫秒精度

  **QA 场景**：

  ```
  场景: kotlinx.serialization round-trip
    工具: Bash (gradle test)
    前置条件: T4 完成
    步骤:
      1. 创建测试：json 字符串 → Episode 对象 → 序列化回 json
      2. 断言 round-trip 前后 json 等价
      3. 执行: ./gradlew :core-model:testDebugUnitTest
    预期结果: 所有序列化测试通过
    失败指标: 反序列化异常、字段丢失
    证据: .sisyphus/evidence/task-5-serialization-test.txt

  场景: PlaybackState 穷举
    工具: Bash (gradle test)
    前置条件: 模型定义完成
    步骤:
      1. 测试 `PlaybackState::class.sealedSubclasses` 数量
      2. when 表达式穷举所有子类（无 else 分支）
    预期结果: 编译器验证穷举
    证据: .sisyphus/evidence/task-5-sealed-exhaustive.txt
  ```

  **提交**: YES
  - 消息: `feat(core-model): define complete domain model entities`
  - 文件: `core-model/src/main/kotlin/com/miruplay/tv/model/*.kt`

- [x] 6. core-common 错误模型 + 工具类

  **做什么**：
  - `Result<T>` sealed class：Success(data: T) / Error(error: AppError)
  - `AppError` sealed class hierarchy：
    - `MediaSourceError`：NotFound(path), AuthenticationFailed, ConnectionLost, Timeout, PermissionDenied
    - `ParseError`：NfoMalformed(line, message), InvalidEpisodePattern(filename), XmlParseError(cause)
    - `NetworkError`：NoConnectivity, ServerUnreachable(url), HttpError(code, message), RateLimited(retryAfter)
    - `ScrapingError`：NoMatchFound(query), ApiError(source, message), ParseError
    - `PlaybackError`：CodecNotSupported(codec), FileCorrupted, StreamError(cause)
    - `SyncError`：ConflictDetected(local, remote), WriteFailed(path, cause), ReadOnlyMedia
  - 扩展函数：`AppError.toUserMessage(): String`（返回中文用户可读消息）
  - `PathUtils`：标准化路径、解析 UNC 路径、提取文件名/扩展名
  - `CoroutineUtils`：`withTimeoutOrDefault()`、`retryWithBackoff()`、`cancellable()`

  **完整实现**：
  - 完整错误层次结构，每个错误类型有对应的用户消息映射
  - `Result<T>` 包含 `map`、`flatMap`、`getOrNull`、`onSuccess`、`onError` 方法

  **不能做**：
  - 不要在错误中存储敏感信息（密码、token）
  - 不要依赖 Android Context（保持纯 Kotlin）

  **推荐 Agent Profile**：
  - **Category**: `quick` — 错误模型和工具类
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: YES（与 T5 并行）
  - **并行组**: Wave 1
  - **阻塞**: T8-T15
  - **被阻塞**: T4

  **参考资料**：
  - Kotlin Result 类型最佳实践
  - anime-organizer 的 `src/error.rs`：错误类型设计参考（Rust → Kotlin 等价映射）

  **验收标准**：
  - [ ] `AppError::class.sealedSubclasses` 包含所有 6 个子类
  - [ ] 每个错误子类的 `toUserMessage()` 返回非空中文字符串
  - [ ] `Result<T>` 的 `map`/`flatMap` 链式调用编译通过
  - [ ] `PathUtils.normalizePath("C:\\anime\\show\\..\\other")` 返回标准路径

  **QA 场景**：

  ```
  场景: AppError 用户消息穷举
    工具: Bash (gradle test)
    前置条件: T5 完成
    步骤:
      1. 创建测试：遍历 AppError 所有 sealed subclass
      2. 对每个调用 toUserMessage()
      3. 断言所有返回非空字符串
    预期结果: 所有错误都有用户消息
    证据: .sisyphus/evidence/task-6-error-messages.txt

  场景: Result 链式调用
    工具: Bash (gradle test)
    前置条件: 实现完成
    步骤:
      1. Result.Success(42).map { it * 2 }
      2. 断言结果为 Success(84)
      3. Result.Error(...).flatMap { ... }
      4. 断言不执行 flatMap，保持 Error
    预期结果: map/flatMap 语义正确
    证据: .sisyphus/evidence/task-6-result-chain.txt
  ```

  **提交**: YES
  - 消息: `feat(core-common): add error model, Result type, and utilities`
  - 文件: `core-common/src/main/kotlin/com/miruplay/tv/common/*.kt`

- [x] 7. ProGuard/R8 规则 + 资源脚手架

  **做什么**：
  - `app/proguard-rules.pro`：保留所有 Room 实体（`@Keep` 注解类）、kotlinx.serialization 序列化类、Hilt 生成的组件、ExoPlayer 扩展
  - `app/src/main/AndroidManifest.xml`：声明 `INTERNET`、`ACCESS_NETWORK_STATE`、`WAKE_LOCK`、`FOREGROUND_SERVICE` 权限；声明 `LEANBACK_LAUNCHER` intent-filter；声明 `uses-feature android.hardware.touchscreen required=false`；声明 `android:banner="@drawable/tv_banner"`；声明 `MediaSessionService`
  - `app/src/main/res/drawable/tv_banner.xml`：320×180 占位 banner（渐变背景 + 文字）
  - `app/src/main/res/values/strings.xml`：app_name = "MiruPlay"
  - `app/src/main/res/values/themes.xml`：基础 TV 主题（`Theme.Leanback` 或 Compose 主题）
  - `ui-tv/src/main/res/values/strings.xml`：UI 模块字符串（后续任务使用）

  **完整实现**：
  - ProGuard 规则覆盖所有序列化/DI/持久化框架
  - AndroidManifest 包含 TV 所需的全部声明
  - Banner 占位图（后续 Wave 4 替换为正式资源）

  **不能做**：
  - 不要在 ProGuard 中过度保留（仅保留反射/注解处理所需的类）
  - 不要在 manifest 中声明未在本 scope 使用的权限

  **推荐 Agent Profile**：
  - **Category**: `quick` — 配置和资源文件
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: YES（与 T5, T6 并行）
  - **并行组**: Wave 1
  - **阻塞**: 无（后续任务不阻塞于此，但需在最终构建中存在）
  - **被阻塞**: T4（需要模块目录）

  **参考资料**：
  - Android TV Manifest 要求：`https://developer.android.com/training/tv/start/start`
  - Room ProGuard 规则：`https://developer.android.com/training/data-storage/room`
  - kotlinx.serialization ProGuard 规则

  **验收标准**：
  - [ ] `AndroidManifest.xml` 包含 `LEANBACK_LAUNCHER` intent-filter
  - [ ] `uses-feature android:name="android.hardware.touchscreen" android:required="false"` 存在
  - [ ] `android:banner` 指向有效 drawable
  - [ ] ProGuard 规则文件存在且包含 Room/Kotlinx/Hilt 保留规则
  - [ ] `app_name` 字符串资源存在

  **QA 场景**：

  ```
  场景: Manifest 语义验证
    工具: Bash (aapt2 + grep)
    前置条件: T4, T7 完成
    步骤:
      1. 执行: ./gradlew :app:assembleDebug
      2. aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk
      3. 检查 "uses-feature: name='android.hardware.touchscreen' required='false'"
      4. 检查 "application-label:'MiruPlay'"
    预期结果: APK 正确声明 TV feature
    失败指标: 缺少 touchscreen 声明（Play Store 会标记为非 TV 应用）
    证据: .sisyphus/evidence/task-7-manifest-verify.txt

  场景: ProGuard 规则语法
    工具: Bash (R8 dry-run)
    前置条件: T7 完成
    步骤:
      1. 执行: ./gradlew :app:minifyDebugWithR8 --dry-run
      2. 检查无 ProGuard 语法错误
    预期结果: R8 规则语法有效
    证据: .sisyphus/evidence/task-7-proguard-verify.txt
  ```

  **提交**: YES
  - 消息: `chore: add ProGuard rules, AndroidManifest, and resource scaffolding`
  - 文件: `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/**/*`, `ui-tv/src/main/res/values/strings.xml`

- [x] 8. MediaSource 接口 + 能力模型

  **做什么**：
  - 在 `media-source` 模块定义 `MediaSource` 核心接口：
    ```kotlin
    interface MediaSource {
        val capabilities: MediaCapabilities
        suspend fun listFiles(path: String): Result<List<FileEntry>>
        suspend fun openStream(path: String): Result<InputStream>
        suspend fun getMetadata(path: String): Result<FileMetadata>
        suspend fun testConnection(): Result<Boolean>
        suspend fun close()
    }
    ```
  - `FileEntry`：name, path, isDirectory, size (bytes), lastModified (epoch ms), mimeType
  - `FileMetadata`：延伸自 FileEntry，增加 duration (ms), width, height, codecInfo, subtitleTracks (List<SubtitleTrack>)
  - `SubtitleTrack`：language, title, isExternal, path, format (ASS/SRT/VTT)
  - `MediaCapabilities`：seekable, supportsRange, supportsList, supportsWrite
  - `MediaSourceFactory` 接口：`fun create(config: MediaSourceInfo): Result<MediaSource>`

  **完整实现**（接口定义完整，不预留空方法）：
  - 字幕轨道信息完整（外部 ASS/SRT 优先于内嵌）
  - `FileMetadata` 包含编解码信息用于播放器预检

  **不能做**：
  - 不要在接口中引入 Android Context 依赖
  - 不要假设具体协议实现细节

  **推荐 Agent Profile**：
  - **Category**: `quick`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: YES（Wave 2 所有接口可并行）
  - **并行组**: Wave 2
  - **阻塞**: T16（FakeMediaSource）、T36（WebDAV）、T37（SMB）
  - **被阻塞**: T5, T6（需要领域模型和 Result 类型）

  **参考资料**：
  - LitPlayer 的 SMB/WebDAV 抽象层设计（GitHub: daluobo/LitPlayer-release）
  - Java InputStream 最佳实践（大文件流式读取）
  - anime-organizer 的文件操作抽象

  **验收标准**：
  - [ ] `MediaSource` 接口的 5 个方法全部声明（含 suspend 修饰符）
  - [ ] `MediaCapabilities` 覆盖 seekable/supportsRange/supportsList/supportsWrite
  - [ ] `SubtitleTrack.format` 支持 ASS/SRT/VTT 枚举
  - [ ] 接口编译通过（`./gradlew :media-source:compileKotlin`）

  **QA 场景**：

  ```
  场景: 接口编译验证
    工具: Bash
    前置条件: T5, T6 完成
    步骤:
      1. 执行: ./gradlew :media-source:compileKotlin
      2. 检查 BUILD SUCCESSFUL
    预期结果: 编译通过
    证据: .sisyphus/evidence/task-8-interface-compile.txt

  场景: 接口契约文档化
    工具: Bash (dokka 或手动验证)
    前置条件: T8 完成
    步骤:
      1. 检查每个接口方法有 KDoc 注释
      2. KDoc 说明 suspend 语义、返回 Result 的错误类型
    预期结果: 所有方法有完整 KDoc
    证据: .sisyphus/evidence/task-8-kdoc-verify.txt
  ```

  **提交**: YES
  - 消息: `feat(media-source): define MediaSource interface and capability model`
  - 文件: `media-source/src/main/kotlin/com/miruplay/tv/mediasource/*.kt`

- [x] 9. Scanner 接口

  **做什么**：
  - 在 `scanner` 模块定义 Scanner 接口：
    ```kotlin
    interface MediaScanner {
        suspend fun scan(source: MediaSource, rootPath: String): Result<ScanResult>
        suspend fun quickScan(source: MediaSource, rootPath: String): Result<ScanResult>
    }
    
    interface EpisodeDetector {
        fun detectEpisode(fileName: String): EpisodeMatch?
        fun detectSeason(fileName: String): Int?
        fun extractAnimeName(fileName: String): String?
    }
    ```
  - `EpisodeMatch`：animeName, seasonNumber, episodeNumber, episodeTitle (可选), isSpecial, isMultiPart, partLabel
  - `SeasonGroup`：seasonNumber, episodes (List<FileEntry>)
  - `NamedPattern` interface：用于注册自定义命名模式
  - `ScanConfig`：includeHidden, ignorePatterns, maxDepth, minFileSize, probeVideoHeaders

  **完整实现**（支持所有常见命名模式）：
  - 接口设计支持注册自定义命名模式
  - `EpisodeMatch.isMultiPart` 支持 `03a.mkv` + `03b.mkv` 合并识别

  **不能做**：
  - 不要在接口层实现具体的正则匹配（那是 T29 的事）
  - 不要依赖 Room 或持久化

  **推荐 Agent Profile**：
  - **Category**: `quick`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 2 内所有接口并行
  - **阻塞**: T29-T31
  - **被阻塞**: T5, T6

  **验收标准**：
  - [ ] `EpisodeDetector.detectEpisode()` 返回 `EpisodeMatch?`（nullable）
  - [ ] `MediaScanner.scan()` 接收 `MediaSource` + `rootPath` 参数
  - [ ] `ScanConfig` 包含所有必需配置字段

  **QA 场景**：

  ```
  场景: 接口编译验证
    工具: Bash
    前置条件: T8 完成
    步骤:
      1. 执行: ./gradlew :scanner:compileKotlin
      2. 检查 BUILD SUCCESSFUL
    预期结果: 依赖于 media-source 的接口编译通过
    证据: .sisyphus/evidence/task-9-compile.txt
  ```

  **提交**: YES
  - 消息: `feat(scanner): define MediaScanner and EpisodeDetector interfaces`
  - 文件: `scanner/src/main/kotlin/com/miruplay/tv/scanner/*.kt`

- [x] 10. Metadata 接口（NFO 解析/写入）

  **做什么**：
  - 在 `metadata` 模块定义 NFO 处理接口：
    ```kotlin
    interface NfoParser {
        suspend fun parseEpisodeNfo(nfoPath: String): Result<NfoMetadata>
        suspend fun parseTvShowNfo(nfoPath: String): Result<TvShowNfoMetadata>
        suspend fun detectNfoType(nfoContent: String): NfoType
    }
    
    interface NfoWriter {
        suspend fun writeEpisodeNfo(nfoPath: String, metadata: NfoMetadata): Result<Unit>
        suspend fun writeTvShowNfo(nfoPath: String, metadata: TvShowNfoMetadata): Result<Unit>
        suspend fun updateWatchProgress(nfoPath: String, position: Long, lastWatched: Long): Result<Unit>
    }
    ```
  - `NfoType` enum：EPISODE, TVSHOW, MOVIE, UNKNOWN
  - `NfoMetadata`（已在 T5 domain model 中定义，此处复用）
  - `TvShowNfoMetadata`：title, originalTitle, plot, genre, premiered, studio, rating, uniqueIds (List<UniqueId>), actors
  - `UniqueId`：type (String: "bangumi"/"anilist"/"tmdb"/"anidb"), value (String), default (Boolean)
  - `NfoWriteOptions`：preserveUnknownTags (Boolean), createBackup (Boolean)

  **完整实现**：
  - 支持 Kodi v17+ NFO 格式（`episodedetails` 根元素）
  - Jellyfin/Plex/Emby 变体兼容（防御性解析）
  - `UniqueId` 设计参考 anime-organizer 的多源 ID 方案

  **不能做**：
  - 不要在接口层硬编码 XML 解析（那是 T32 的事）
  - 不支持写 NFO 时创建新文件覆盖（`createBackup` 选项控制）

  **推荐 Agent Profile**：
  - **Category**: `quick`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 2
  - **阻塞**: T32-T34
  - **被阻塞**: T5

  **参考资料**：
  - Kodi NFO 官方规范：`https://kodi.wiki/view/NFO_files/Episodes`
  - anime-organizer 的 `src/nfo.rs`：NFO 结构定义（Rust → Kotlin 等价映射）
  - Jellyfin NFO 扩展格式

  **验收标准**：
  - [ ] `NfoParser` 3 个方法全部声明
  - [ ] `NfoWriter.updateWatchProgress()` 参数包含 position (Long) 和 lastWatched (Long)
  - [ ] `UniqueId` 类型字符串支持 "bangumi"/"anilist"/"tmdb"/"anidb"
  - [ ] `NfoWriteOptions.preserveUnknownTags` 默认 true

  **QA 场景**：

  ```
  场景: 接口编译 + KDoc 验证
    工具: Bash
    前置条件: T5 完成
    步骤:
      1. 执行: ./gradlew :metadata:compileKotlin
      2. 检查每个方法有 KDoc
    预期结果: 编译通过，文档完整
    证据: .sisyphus/evidence/task-10-compile.txt
  ```

  **提交**: YES
  - 消息: `feat(metadata): define NfoParser and NfoWriter interfaces`
  - 文件: `metadata/src/main/kotlin/com/miruplay/tv/metadata/*.kt`

- [x] 11. Scraper 接口

  **做什么**：
  - 在 `scraper` 模块定义可插拔刮削器接口：
    ```kotlin
    interface MetadataScraper {
        val sourceName: String  // "AniList", "BangumiArchive"
        suspend fun searchAnime(query: String): Result<List<ScraperResult>>
        suspend fun getAnimeDetails(animeId: String): Result<Anime>
        suspend fun getEpisodes(animeId: String): Result<List<EpisodeMetadata>>
        suspend fun searchByAlias(normalizedName: String, candidates: List<String>): Result<ScraperResult?>
    }
    
    interface AliasResolver {
        suspend fun resolve(normalizedName: String): Result<ScraperResult?>
        suspend fun bulkResolve(names: List<String>): Result<Map<String, ScraperResult?>>
    }
    ```
  - `EpisodeMetadata`：episodeNumber, title, airDate, summary, thumbnailUrl
  - `ScraperConfig`：timeout (ms), maxRetries, cacheEnabled, cacheDuration (ms)
  - `ScraperSource` enum：ANILIST, BANGUMI_ARCHIVE

  **完整实现**：
  - 接口支持多数据源（AniList + Bangumi Archive），可插拔切换
  - `searchByAlias` 方法专门用于别名解析（参考 anime-organizer 的 3 级回退机制）
  - `AliasResolver` 独立于 `MetadataScraper`，可单独替换别名解析策略

  **不能做**：
  - 不要在接口中引用具体 HTTP 客户端
  - 不要假设数据源一定在线（Bangumi Archive 支持离线）

  **推荐 Agent Profile**：
  - **Category**: `quick`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 2
  - **阻塞**: T35（AniList 实现）
  - **被阻塞**: T5

  **参考资料**：
  - AniList GraphQL API 文档：`https://docs.anilist.co/`
  - anime-organizer 的 `src/metadata/bangumi.rs`：Bangumi Archive API 设计
  - anime-organizer 的 `src/metadata/alias.rs`：别名解析逻辑

  **验收标准**：
  - [ ] `MetadataScraper.sourceName` 属性声明
  - [ ] `searchByAlias()` 接收 `normalizedName` + `candidates` 参数
  - [ ] `AliasResolver` 独立接口（非 `MetadataScraper` 的成员）
  - [ ] `ScraperSource` enum 包含 ANILIST 和 BANGUMI_ARCHIVE

  **QA 场景**：

  ```
  场景: 接口编译验证
    工具: Bash
    前置条件: T5 完成
    步骤:
      1. 执行: ./gradlew :scraper:compileKotlin
      2. 检查 BUILD SUCCESSFUL
    预期结果: 编译通过
    证据: .sisyphus/evidence/task-11-compile.txt
  ```

  **提交**: YES
  - 消息: `feat(scraper): define pluggable MetadataScraper and AliasResolver interfaces`
  - 文件: `scraper/src/main/kotlin/com/miruplay/tv/scraper/*.kt`

- [x] 12. PlayerCore 接口

  **做什么**：
  - 在 `player-core` 模块定义播放器抽象：
    ```kotlin
    interface PlaybackController {
        val state: StateFlow<PlaybackState>
        suspend fun play(source: PlaybackSource)
        suspend fun pause()
        suspend fun resume()
        suspend fun seekTo(positionMs: Long)
        suspend fun stop()
        suspend fun setPlaybackSpeed(speed: Float)
        suspend fun setSubtitleTrack(trackIndex: Int)
        suspend fun setAudioTrack(trackIndex: Int)
        fun getAvailableSubtitles(): List<SubtitleTrack>
        fun getAvailableAudioTracks(): List<AudioTrack>
    }
    ```
  - `PlaybackSource`：uri (String), mediaSource (MediaSource), startPosition (Long = 0), subtitleTracks (List<SubtitleTrack>)
  - `AudioTrack`：index, language, title, codec
  - `PlaybackConfig`：preferredAudioLanguage (String = "ja"), preferredSubtitleLanguage (String = "zh"), autoResume (Boolean = true), respectEmbeddedSubtitles (Boolean = false)

  **完整实现**：
  - `state: StateFlow<PlaybackState>` 支持响应式状态观察
  - `PlaybackConfig.respectEmbeddedSubtitles = false`：外部字幕优先于内嵌（粉丝字幕策略）

  **不能做**：
  - 不要在接口中直接引用 ExoPlayer/Media3 类型（保持抽象）
  - 不要在接口中处理 TV 遥控器按键（那是 UI 模块的事）

  **推荐 Agent Profile**：
  - **Category**: `quick`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 2
  - **阻塞**: T20（实现）
  - **被阻塞**: T5

  **验收标准**：
  - [ ] `PlaybackController.state` 是 `StateFlow<PlaybackState>`（不可变状态）
  - [ ] `play()` 参数包含完整 `PlaybackSource`（uri + mediaSource + startPosition + subtitles）
  - [ ] `PlaybackConfig.autoResume` 默认 true
  - [ ] `PlaybackConfig.respectEmbeddedSubtitles` 默认 false

  **QA 场景**：

  ```
  场景: 接口编译验证
    工具: Bash
    前置条件: T5 完成
    步骤:
      1. 执行: ./gradlew :player-core:compileKotlin
      2. 检查 BUILD SUCCESSFUL
    预期结果: 编译通过
    证据: .sisyphus/evidence/task-12-compile.txt
  ```

  **提交**: YES
  - 消息: `feat(player-core): define PlaybackController interface and config`
  - 文件: `player-core/src/main/kotlin/com/miruplay/tv/player/*.kt`

- [x] 13. SyncEngine 接口

  **做什么**：
  - 在 `sync-engine` 模块定义进度同步接口：
    ```kotlin
    interface SyncEngine {
        suspend fun syncEpisode(episode: Episode, nfoPath: String): Result<SyncResult>
        suspend fun syncAllEpisodes(episodes: List<Episode>): Result<List<SyncResult>>
        suspend fun resolveConflict(local: Episode, remote: NfoMetadata, nfoPath: String): Result<Episode>
    }
    
    interface ProgressTracker {
        suspend fun updateProgress(episodeId: String, positionMs: Long): Result<Unit>
        suspend fun getProgress(episodeId: String): Result<Long>
        suspend fun markCompleted(episodeId: String): Result<Unit>
        suspend fun markUnwatched(episodeId: String): Result<Unit>
    }
    ```
  - `SyncResult`：episodeId, action (SYNCED_TO_NFO / SYNCED_FROM_NFO / CONFLICT / SKIPPED), resolvedPosition, timestamp
  - `SyncConfig`：autoSyncInterval (ms), nfoWriteDelay (ms), conflictResolution (LOCAL_WINS / REMOTE_WINS / MANUAL)
  - `ConflictInfo`：localPosition, remotePosition, localTimestamp, remoteTimestamp, resolution

  **完整实现**：
  - 冲突解决规则建模为 `ConflictResolution` enum（可配置）
  - `ProgressTracker` 独立接口（Room 实现隔离）

  **不能做**：
  - 不要直接操作 Room 数据库（通过 Repository 接口）
  - 不要直接操作 NFO 文件（通过 NfoWriter 接口）

  **推荐 Agent Profile**：
  - **Category**: `quick`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 2
  - **阻塞**: T39-T41
  - **被阻塞**: T5

  **验收标准**：
  - [ ] `ConflictResolution` enum 包含 LOCAL_WINS / REMOTE_WINS / MANUAL
  - [ ] `SyncResult.action` 区分 SYNCED_TO_NFO / SYNCED_FROM_NFO / CONFLICT / SKIPPED
  - [ ] `SyncConfig.autoSyncInterval` 和 `nfoWriteDelay` 存在

  **QA 场景**：

  ```
  场景: 接口编译验证
    工具: Bash
    前置条件: T5 完成
    步骤:
      1. 执行: ./gradlew :sync-engine:compileKotlin
      2. 检查 BUILD SUCCESSFUL
    预期结果: 编译通过
    证据: .sisyphus/evidence/task-13-compile.txt
  ```

  **提交**: YES
  - 消息: `feat(sync-engine): define SyncEngine and ProgressTracker interfaces`
  - 文件: `sync-engine/src/main/kotlin/com/miruplay/tv/sync/*.kt`

- [x] 14. Repository 接口（数据层）

  **做什么**：
  - 在 `data` 模块定义数据访问接口：
    ```kotlin
    interface MediaRepository {
        suspend fun addSource(source: MediaSourceInfo): Result<Long>
        suspend fun removeSource(sourceId: Long): Result<Unit>
        suspend fun getSources(): Result<List<MediaSourceInfo>>
        suspend fun updateSource(source: MediaSourceInfo): Result<Unit>
        suspend fun getSourceById(sourceId: Long): Result<MediaSourceInfo>
    }
    
    interface MetadataRepository {
        suspend fun cacheMetadata(anime: Anime): Result<Unit>
        suspend fun getCachedMetadata(animeId: String): Result<Anime?>
        suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>>
        suspend fun invalidateCache(animeId: String): Result<Unit>
    }
    
    interface ProgressRepository {
        suspend fun saveProgress(episodeId: String, positionMs: Long, lastWatched: Long): Result<Unit>
        suspend fun getProgress(episodeId: String): Result<ProgressRecord?>
        suspend fun getAllProgress(): Result<List<ProgressRecord>>
        suspend fun deleteProgress(episodeId: String): Result<Unit>
    }
    
    interface IndexRepository {
        suspend fun rebuildIndex(sourceId: Long, entries: List<IndexEntry>): Result<Unit>
        suspend fun queryIndex(sourceId: Long, query: String): Result<List<IndexEntry>>
        suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>>
        suspend fun clearIndex(sourceId: Long): Result<Unit>
    }
    ```
  - `ProgressRecord`：episodeId, positionMs, lastWatched, playCount
  - `IndexEntry`：sourceId, path, animeName, seasonNumber, episodeNumber, isDirectory

  **完整实现**：
  - 4 个独立 Repository 接口（职责分离）
  - `IndexRepository.rebuildIndex()` 支持全量重建索引

  **不能做**：
  - 不要在接口中暴露 Room DAO 类型
  - 不要混合 Repository 职责（Media 不管 Progress）

  **推荐 Agent Profile**：
  - **Category**: `quick`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 2
  - **阻塞**: T17-T19（Repository 实现）
  - **被阻塞**: T5

  **验收标准**：
  - [ ] 4 个 Repository 接口全部声明
  - [ ] 所有方法返回 `Result<T>`（错误可追踪）
  - [ ] `ProgressRepository.saveProgress()` 接受 positionMs (Long) 和 lastWatched (Long)

  **QA 场景**：

  ```
  场景: 接口编译验证
    工具: Bash
    前置条件: T5 完成
    步骤:
      1. 执行: ./gradlew :data:compileKotlin
      2. 检查 BUILD SUCCESSFUL
    预期结果: 编译通过
    证据: .sisyphus/evidence/task-14-compile.txt
  ```

  **提交**: YES
  - 消息: `feat(data): define Media/Metadata/Progress/Index Repository interfaces`
  - 文件: `data/src/main/kotlin/com/miruplay/tv/data/repository/*.kt`

- [x] 15. Room 数据库 + DAO + 实体

  **做什么**：
  - 在 `data` 模块创建 Room 数据库：
    - `MiruPlayDatabase`：@Database 注解，version = 1，entities = [AnimeEntity, EpisodeEntity, MediaSourceEntity, ProgressEntity, IndexEntryEntity]
  - 实体定义：
    - `AnimeEntity`：id, title, titleCn, summary, genres (TypeConverter to JSON), studio, director, episodeCount, airDate, rating, bangumiId, anilistId, tmdbId, posterUrl, fanartUrl, lastUpdated
    - `EpisodeEntity`：id, animeId, seasonNumber, episodeNumber, title, filePath, fileName, duration, thumbnailPath, lastUpdated
    - `MediaSourceEntity`：id, name, type (enum), url, username, password (encrypted?), extraConfig (JSON), isConnected, lastScanned
    - `ProgressEntity`：episodeId (PK), positionMs, lastWatched, playCount
    - `IndexEntryEntity`：id (auto), sourceId, path, animeName, seasonNumber, episodeNumber, isDirectory, fileSize, lastModified
  - DAO 定义：
    - `AnimeDao`：insert, update, getById, getAll, searchByTitle, deleteById
    - `EpisodeDao`：insertAll, getByAnimeId, getByPath, updateFilePath, deleteByAnimeId
    - `MediaSourceDao`：insert, update, delete, getAll, getById
    - `ProgressDao`：upsert, getByEpisodeId, getAll, deleteByEpisodeId, getContinueWatching (按 lastWatched DESC, LIMIT 20)
    - `IndexDao`：insertAll, queryBySourceId, queryByAnimeName, deleteBySourceId, search (LIKE query)
  - TypeConverters：`GenreListConverter` (List<String> ↔ JSON)、`DateConverter` (Long ↔ String)
  - 数据库单例模式（Hilt @Singleton @Provides）

  **完整实现**：
  - 所有表有完整索引：episodeId (ProgressEntity)、sourceId + animeName (IndexEntryEntity)、animeId + seasonNumber (EpisodeEntity)
  - `ProgressDao.getContinueWatching()` 支持首页"继续观看"功能
  - `IndexDao.search()` 支持模糊搜索（LIKE %query%）

  **不能做**：
  - 不要在实体中使用可空主键
  - 不要在 DAO 中使用 `SELECT *`（显式列出所需列）

  **推荐 Agent Profile**：
  - **Category**: `quick` — 标准 Room 配置
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 2（与接口定义并行）
  - **并行组**: Wave 2
  - **阻塞**: T17-T19（Repository 实现需要 DAO）
  - **被阻塞**: T5（需要领域模型定义实体字段）

  **验收标准**：
  - [ ] 5 个 Entity + 5 个 DAO 全部定义
  - [ ] `MiruPlayDatabase` @Database 注解 version=1
  - [ ] `ProgressDao.getContinueWatching()` 按 lastWatched DESC 排序 + LIMIT 20
  - [ ] `IndexDao.search()` 使用 LIKE 查询
  - [ ] TypeConverter 覆盖 List<String> 和 Date 类型
  - [ ] `./gradlew :data:compileKotlin` 编译通过

  **QA 场景**：

  ```
  场景: Room 数据库编译验证
    工具: Bash
    前置条件: T5, T14 完成
    步骤:
      1. 执行: ./gradlew :data:compileKotlin
      2. 检查 BUILD SUCCESSFUL
      3. 执行: ./gradlew :data:kaptDebugKotlin（如果使用 kapt）
    预期结果: Room 注解处理无错误
    证据: .sisyphus/evidence/task-15-room-compile.txt

  场景: DAO 查询编译验证
    工具: Bash
    前置条件: T15 完成
    步骤:
      1. 创建简单单元测试：getContinueWatching() 返回类型验证
      2. 执行: ./gradlew :data:testDebugUnitTest
    预期结果: 测试编译通过
    证据: .sisyphus/evidence/task-15-dao-test.txt
  ```

  **提交**: YES
  - 消息: `feat(data): add Room database, entities, DAOs, and TypeConverters`
  - 文件: `data/src/main/kotlin/com/miruplay/tv/data/db/*.kt`, `data/src/main/kotlin/com/miruplay/tv/data/entity/*.kt`, `data/src/main/kotlin/com/miruplay/tv/data/dao/*.kt`

- [x] 16. LocalMediaSource 实现 (TDD)

  **做什么**：
  - 在 `media-source` 模块实现 `LocalMediaSource`（生产级本地文件系统实现，非 fake）：
    - `listFiles(path)`：使用 `java.io.File` 遍历目录，返回 `List<FileEntry>`
    - `openStream(path)`：使用 `FileInputStream` 返回流，支持大文件
    - `getMetadata(path)`：使用 `MediaMetadataRetriever` 提取时长/分辨率/编解码信息
    - `testConnection()`：检查路径是否存在且可读
    - 实现 `MediaCapabilities(seekable = true, supportsRange = true, supportsList = true, supportsWrite = true)`
  - 处理边缘情况：
    - 文件不存在 → `Result.Error(MediaSourceError.NotFound)`
    - 权限不足 → `Result.Error(MediaSourceError.PermissionDenied)`
    - 超大目录 → 分页 lazyload（`listFiles(path, offset, limit)`）
    - 隐藏文件过滤（`.DS_Store`, `Thumbs.db`, `@eaDir`）
    - Unicode 文件名正确处理
  - 单元测试（TDD 红绿重构）：
    - 测试脚手架创建临时目录结构
    - 测试每个方法的成功路径和错误路径

  **完整实现**：
  - 生产质量，不是 "fake" 占位实现；本地文件系统是正式支持的源类型

  **不能做**：
  - 不要在 UI 线程执行文件操作（所有方法用 `withContext(Dispatchers.IO)`）
  - 不要缓存文件列表（让上层决定缓存策略）

  **推荐 Agent Profile**：
  - **Category**: `deep` — 需要 TDD + 文件系统 I/O 处理
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 3（与 T17-T19 并行，均依赖 T8/T14/T15 接口）
  - **并行组**: Wave 3
  - **阻塞**: T29（Scanner 需要 MediaSource 测试）
  - **被阻塞**: T8（MediaSource 接口）

  **验收标准**：
  - [ ] 所有测试通过：`./gradlew :media-source:testDebugUnitTest`
  - [ ] `listFiles()` 返回正确文件树（含大小、时间戳、mimeType）
  - [ ] `openStream()` 读取内容与源文件一致（byte-by-byte 比对）
  - [ ] 不存在的路径 → `MediaSourceError.NotFound`
  - [ ] Unicode 文件名（日文/中文/韩文）正确遍历
  - [ ] 隐藏文件（`.DS_Store` 等）被过滤

  **QA 场景**：

  ```
  场景: 列表 + 读取正常文件
    工具: Bash (gradle test)
    前置条件: 创建临时测试目录含已知文件
    步骤:
      1. 创建 /tmp/miruplay-test/anime/test.mkv（1KB 已知内容）
      2. 调用 localSource.listFiles("/tmp/miruplay-test")
      3. 断言返回 1 个 FileEntry，name="test.mkv"
      4. 调用 localSource.openStream("/tmp/miruplay-test/anime/test.mkv")
      5. 读取全部字节，比对内容一致
    预期结果: listFiles 和 openStream 正确
    证据: .sisyphus/evidence/task-16-happy-path.txt

  场景: 大文件流式读取不 OOM
    工具: Bash (gradle test)
    前置条件: 创建 100MB 测试文件
    步骤:
      1. 调用 openStream()
      2. 逐块读取（8KB buffer），不一次性加载到内存
      3. 验证全部内容正确
    预期结果: 内存使用稳定（不随文件大小线性增长）
    证据: .sisyphus/evidence/task-16-large-file.txt

  场景: 不存在路径错误
    工具: Bash (gradle test)
    前置条件: 使用不存在的路径
    步骤:
      1. 调用 localSource.listFiles("/nonexistent/path")
      2. 断言返回 Result.Error(MediaSourceError.NotFound)
    预期结果: 优雅返回错误，不抛异常
    证据: .sisyphus/evidence/task-16-not-found.txt
  ```

  **提交**: YES
  - 消息: `feat(media-source): implement production LocalMediaSource with TDD`
  - 文件: `media-source/src/main/kotlin/com/miruplay/tv/mediasource/local/*.kt`, `media-source/src/test/**`

- [x] 17. MediaRepository 实现 (TDD)

  **做什么**：
  - 在 `data` 模块实现 `MediaRepositoryImpl`（基于 Room）：
    - 使用 `MediaSourceDao` 操作 `MediaSourceEntity`
    - `addSource()`：插入前检查重复（url + type 组合唯一）
    - `removeSource()`：级联删除相关 IndexEntry
    - `getSources()`：按 `lastScanned` 降序返回
    - `updateSource()`：支持部分更新（仅修改连接状态等）
  - 密码字段加密存储（使用 AndroidX Security Crypto 或 Base64 编码 + 密钥存储）

  **不能做**：
  - 不要在主线程调用 DAO（所有 suspend 函数）
  - 不要在数据库中明文存储密码

  **推荐 Agent Profile**：
  - **Category**: `deep` — TDD + Room 实现
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 3（可与 T16, T18, T19 并行，各自独立）
  - **阻塞**: T24（UI 设置页需要添加源功能）
  - **被阻塞**: T14, T15

  **验收标准**：
  - [ ] 所有 CRUD 测试通过
  - [ ] 重复 url+type 组合 → 插入失败（唯一约束）
  - [ ] `removeSource()` 后相关 `IndexEntry` 被清空
  - [ ] 密码非明文存储（Base64 编码或 EncryptedSharedPreferences）

  **QA 场景**：

  ```
  场景: 增删改查完整流程
    工具: Bash (gradle test)
    前置条件: Room 内存数据库测试
    步骤:
      1. addSource(WebDAV source) → 断言返回 id 非 0
      2. getSources() → 断言列表包含刚添加的源
      3. updateSource(修改连接状态) → 断言更新后字段变化
      4. removeSource(id) → 断言 getSources() 列表为空
    预期结果: 完整 CRUD 流程通过
    证据: .sisyphus/evidence/task-17-crud-flow.txt
  ```

  **提交**: YES
  - 消息: `feat(data): implement MediaRepository with Room and TDD`
  - 文件: `data/src/main/kotlin/com/miruplay/tv/data/repository/MediaRepositoryImpl.kt`, `data/src/test/**`

- [x] 18. MetadataRepository 实现 (TDD)

  **做什么**：
  - 实现 `MetadataRepositoryImpl`（Room 缓存层）：
    - `cacheMetadata()`：upsert AnimeEntity + 批量 insert EpisodeEntity
    - `getCachedMetadata()`：查询 AnimeEntity + 关联 EpisodeEntity
    - `getCachedEpisodes()`：按 animeId 查询，按 seasonNumber/episodeNumber 排序
    - `invalidateCache()`：按 animeId 删除 AnimeEntity + 关联 EpisodeEntity
  - 缓存过期策略：基于 `lastUpdated` 字段，超过 `cacheDuration` 后返回 null（触发重新刮削）

  **不能做**：
  - 不要在网络线程中调用（Repository 本身不做网络请求）

  **推荐 Agent Profile**：
  - **Category**: `deep`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 3
  - **阻塞**: T34（ViewModel 集成）
  - **被阻塞**: T14, T15

  **验收标准**：
  - [ ] 缓存 + 读取 round-trip 测试通过
  - [ ] `invalidateCache()` 后 getCachedMetadata 返回 null
  - [ ] `getCachedEpisodes()` 按 season/episode 正确排序

  **QA 场景**：

  ```
  场景: 缓存 round-trip
    工具: Bash (gradle test)
    前置条件: 创建测试 Anime + Episodes
    步骤:
      1. cacheMetadata(anime with 3 episodes)
      2. getCachedMetadata(animeId) → 断言 anime 非空，episodes.size = 3
      3. invalidateCache(animeId)
      4. getCachedMetadata(animeId) → 断言返回 null
    预期结果: 缓存操作正确
    证据: .sisyphus/evidence/task-18-cache-roundtrip.txt
  ```

  **提交**: YES
  - 消息: `feat(data): implement MetadataRepository with Room cache and TDD`
  - 文件: `data/src/main/kotlin/com/miruplay/tv/data/repository/MetadataRepositoryImpl.kt`, `data/src/test/**`

- [x] 19. ProgressRepository 实现 (TDD)

  **做什么**：
  - 实现 `ProgressRepositoryImpl`（Room 进度存储）：
    - `saveProgress()`：upsert ProgressEntity（冲突时更新 positionMs, lastWatched, playCount++）
    - `getProgress()`：按 episodeId 查询，无记录返回 null
    - `getAllProgress()`：全量返回（供 SyncEngine 批量同步）
    - `deleteProgress()`：按 episodeId 删除
    - `getContinueWatching()`：lastWatched DESC + LIMIT 20（首页继续观看列表）

  **不能做**：
  - 不要在 saveProgress 时触发 NFO 同步（那是 SyncEngine 的事）

  **推荐 Agent Profile**：
  - **Category**: `deep`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 3
  - **阻塞**: T25（UI 继续观看列表）、T39（SyncEngine）
  - **被阻塞**: T14, T15

  **验收标准**：
  - [ ] upsert 行为正确：重复 episodeId → 更新而非插入新行
  - [ ] `getContinueWatching()` 返回最多 20 条、按 lastWatched DESC
  - [ ] playCount 在每次 saveProgress 时 +1

  **QA 场景**：

  ```
  场景: 进度 upsert + 继续观看列表
    工具: Bash (gradle test)
    前置条件: Room 内存数据库
    步骤:
      1. saveProgress("ep1", 5000, now) → 插入
      2. saveProgress("ep1", 8000, now+1) → 更新（同一 episodeId）
      3. getProgress("ep1") → 断言 positionMs = 8000, playCount = 2
      4. saveProgress("ep2", 1000, now+2)
      5. getContinueWatching() → 断言第一条是 ep2（更近的 lastWatched）
    预期结果: 进度正确更新，排序正确
    证据: .sisyphus/evidence/task-19-progress-upsert.txt
  ```

  **提交**: YES
  - 消息: `feat(data): implement ProgressRepository with upsert and continue watching`
  - 文件: `data/src/main/kotlin/com/miruplay/tv/data/repository/ProgressRepositoryImpl.kt`, `data/src/test/**`

- [x] 20. PlayerCore ExoPlayer 封装 (TDD)

  **做什么**：
  - 在 `player-core` 模块实现 `ExoPlaybackController`：
    - 封装 `ExoPlayer.Builder(context).build()`
    - `state: MutableStateFlow<PlaybackState>` 通过 `Player.Listener` 事件更新
    - `play(source)`：构建 `MediaItem`（从 `PlaybackSource.uri`），设置 startPosition，注册字幕轨道
    - `seekTo(positionMs)`：调用 `player.seekTo(positionMs)`
    - `pause()` / `resume()` / `stop()`
    - `setPlaybackSpeed(speed)`：调用 `player.setPlaybackSpeed(speed)`
    - `setSubtitleTrack(index)` / `setAudioTrack(index)`：使用 `TrackSelectionParameters`
    - `getAvailableSubtitles()` / `getAvailableAudioTracks()`：通过 `Player.Listener.onTracksChanged` 更新
  - 字幕加载策略：
    - 外部 ASS/SRT 优先于内嵌（`PlaybackConfig.respectEmbeddedSubtitles = false`）
    - 加载 `PlaybackSource.subtitleTracks` 中的外部字幕文件作为 `MediaItem.SubtitleConfiguration`
  - 自动恢复播放：`play(source)` 时若 `startPosition > 0`，播放器加载后自动 seek
  - 单元测试（TDD）：Mock ExoPlayer，验证控制方法调用顺序

  **完整实现**：
  - 完整 ExoPlayer 封装（非最小化），包含字幕管理、音轨切换、播放速度调整
  - 所有 Player.Listener 事件映射到 PlaybackState sealed class

  **不能做**：
  - 不要在 `play()` 中阻塞调用线程（使用协程）
  - 不要在 PlaybackController 中管理 MediaSession（那是 T21 的事）

  **推荐 Agent Profile**：
  - **Category**: `deep` — ExoPlayer 集成 + TDD
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 3（与数据层仓库并行）
  - **阻塞**: T21（MediaSessionService 依赖播放器）, T27（UI 播放器页）
  - **被阻塞**: T12

  **验收标准**：
  - [ ] `play()` → `state` 变为 `Playing`
  - [ ] `pause()` → `state` 变为 `Paused`
  - [ ] `seekTo(30000)` → `state.position` 更新为 ~30000
  - [ ] 播放结束 → `state` 变为 `Ended`
  - [ ] `setSubtitleTrack(1)` → `onTracksChanged` 回调触发
  - [ ] 外部字幕文件正确加载为 `SubtitleConfiguration`

  **QA 场景**：

  ```
  场景: 播放 + 暂停 + seek + 结束
    工具: Bash (gradle test) + 模拟 ExoPlayer
    前置条件: Mock ExoPlayer 实例
    步骤:
      1. play(source) → 断言 state 为 Playing
      2. pause() → 断言 state 为 Paused
      3. seekTo(30_000) → 断言 player.seekTo(30000) 被调用
      4. 模拟 onPlaybackStateChanged(STATE_ENDED) → 断言 state 为 Ended
    预期结果: 状态机正确转换
    证据: .sisyphus/evidence/task-20-state-machine.txt

  场景: 外部字幕加载
    工具: Bash (gradle test)
    前置条件: PlaybackSource 含字幕轨道
    步骤:
      1. 创建 PlaybackSource(subtitleTracks = [SubtitleTrack(path="/subs/ep01.ass")])
      2. play(source)
      3. 断言 MediaItem.SubtitleConfiguration 包含外部字幕 URI
    预期结果: 外部字幕优先于内嵌
    证据: .sisyphus/evidence/task-20-subtitle-loading.txt
  ```

  **提交**: YES
  - 消息: `feat(player-core): implement ExoPlayer wrapper with full state machine and TDD`
  - 文件: `player-core/src/main/kotlin/com/miruplay/tv/player/ExoPlaybackController.kt`, `player-core/src/test/**`

- [x] 21. MediaSessionService 集成

  **做什么**：
  - 在 `player-core` 模块创建 `MiruPlayMediaService`：
    - 继承 `MediaSessionService`
    - `onCreate()`：初始化 `ExoPlayer` + `MediaSession.Builder`
    - `onGetSession()`：返回 `MediaSession`
    - 设置 MediaSession 元数据：title（当前播放番剧名）、artist（集数）、artwork（海报）
    - 处理媒体按键回调：`onPlay()`、`onPause()`、`onSeekTo()`、`onSkipToNext()`、`onSkipToPrevious()`
    - 在 `AndroidManifest.xml` 中注册 Service（`android:foregroundServiceType="mediaPlayback"`）
  - 与 `PlaybackController` 集成：
    - 通过 `Player.Listener` 同步 MediaSession 播放状态
    - `PlaybackStateCompat` 映射到媒体会话状态

  **完整实现**：
  - 完整 MediaSession 集成（含 Now Playing 卡片元数据）
  - 支持播放队列（可扩展到下一集自动播放）

  **不能做**：
  - 不要在 Service 中直接操作 UI

  **推荐 Agent Profile**：
  - **Category**: `deep`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: NO — 依赖 T20（播放器实现）
  - **阻塞**: T27（UI 播放器页集成）
  - **被阻塞**: T20

  **验收标准**：
  - [ ] `MiruPlayMediaService` 在 Manifest 中声明 `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`
  - [ ] `adb shell dumpsys media_session` 可见 MiruPlay 会话
  - [ ] 媒体按键（DPAD_CENTER）触发播放/暂停
  - [ ] MediaSession 元数据包含 title/artist/artwork

  **QA 场景**：

  ```
  场景: MediaSession 可见性
    工具: Bash (adb)
    前置条件: App 安装并启动播放
    步骤:
      1. App 启动播放
      2. 执行: adb shell dumpsys media_session
      3. 检查输出包含 "com.miruplay.tv"
      4. 检查输出包含 "state=PlaybackState {state=3" (STATE_PLAYING)
    预期结果: MediaSession 注册且状态正确
    证据: .sisyphus/evidence/task-21-mediasession-dump.txt

  场景: 媒体按键响应
    工具: Bash (adb input keyevent)
    前置条件: 播放中
    步骤:
      1. 执行: adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE
      2. 等待 500ms
      3. 执行: adb shell dumpsys media_session | grep state
      4. 断言状态从 3 (PLAYING) 变为 2 (PAUSED)
    预期结果: 按键正确切换播放状态
    证据: .sisyphus/evidence/task-21-media-key.txt
  ```

  **提交**: YES
  - 消息: `feat(player-core): integrate MediaSessionService for TV Now Playing`
  - 文件: `player-core/src/main/kotlin/com/miruplay/tv/player/MiruPlayMediaService.kt`

- [x] 22. Hilt DI 模块配置

  **做什么**：
  - 创建 DI 模块（分散在各模块中，通过 Hilt @Module 组装）：
  - `AppModule`（`:app`）：
    - `@Provides @Singleton`：`OkHttpClient`（含超时/重试/日志拦截器）
    - `@Provides @Singleton`：`MiruPlayDatabase`（Room.databaseBuilder）
  - `MediaSourceModule`（`:media-source`）：
    - `@Binds`：`MediaSourceFactory` 绑定到 `DefaultMediaSourceFactory`
  - `PlayerModule`（`:player-core`）：
    - `@Provides @ActivityScoped`：`ExoPlayer` 实例
    - `@Provides @ActivityScoped`：`PlaybackController` 绑定到 `ExoPlaybackController`
  - `RepositoryModule`（`:data`）：
    - `@Binds @Singleton`：所有 Repository 接口绑定到 Impl
  - `ScraperModule`（`:scraper`）：
    - `@Provides @Singleton`：`MetadataScraper` 实例列表（AniList + Bangumi，通过 `@IntoSet` 多绑定）
  - `AppModule` 中配置 `@HiltAndroidApp` Application 类

  **不能做**：
  - 不要创建循环依赖
  - 不要在 @Singleton 组件中使用 @ActivityScoped 依赖

  **推荐 Agent Profile**：
  - **Category**: `quick` — DI 配置
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: YES（可与 T20, T21 并行，只需接口定义完成）
  - **并行组**: Wave 3
  - **阻塞**: T24-T28（UI 需要 DI 可用）
  - **被阻塞**: T14, T15

  **验收标准**：
  - [ ] `@HiltAndroidApp` Application 类存在
  - [ ] 所有 Repository 接口有 @Binds 实现
  - [ ] `PlaybackController` 可注入到 ViewModel
  - [ ] `MetadataScraper` 多绑定（@IntoSet）支持多个刮削源
  - [ ] 编译时 Hilt 处理无错误：`./gradlew :app:assembleDebug`

  **QA 场景**：

  ```
  场景: DI 图完整性验证
    工具: Bash (gradle assemble)
    前置条件: T5-T21 完成
    步骤:
      1. 执行: ./gradlew :app:assembleDebug
      2. 检查 BUILD SUCCESSFUL
      3. 搜索编译日志中的 Hilt 错误（[Hilt]）
    预期结果: 无 DI 绑定错误，所有依赖可解析
    失败指标: "cannot be provided without an @Provides" 或循环依赖
    证据: .sisyphus/evidence/task-22-di-build.txt
  ```

  **提交**: YES
  - 消息: `feat(di): configure Hilt modules for all components`
  - 文件: `app/src/main/kotlin/com/miruplay/tv/di/*.kt`, 各模块 di 包

- [x] 23. TV UI 主题 + 设计令牌 + 公共组件

  **做什么**：
  - 在 `ui-tv` 模块创建 Compose for TV 主题：
    - `MiruPlayTheme`：包装 `TvMaterialTheme`，自定义颜色/排版
    - 颜色方案：深色背景（#1A1A2E、#16213E）、主色（#E94560 动漫红）、辅色（#0F3460）
    - 排版：TV 专用大字体（标题 32sp、副标题 24sp、正文 18sp）
    - 焦点样式：`Modifier.focusable()` + `Modifier.onFocusChanged()` → 缩放动画 + 边框高亮
  - 公共 UI 组件：
    - `FocusableCard`：大尺寸可聚焦卡片（320×180dp），含海报图 + 标题 + 进度条
    - `TvButton`：DPAD 可聚焦按钮（宽 240dp、高 56dp、圆角 12dp）
    - `TvTextField`：TV 输入框（支持软键盘/语音输入）
    - `LoadingIndicator`：居中旋转加载动画
    - `ErrorMessage`：全屏错误提示 + 重试按钮
    - `OverscanContainer`：自动添加 5% 安全边距（48dp 两边、27dp 上下）

  **完整实现**：
  - 主题一次性完成（含亮色/暗色变体），不预留 TODO
  - 所有组件可独立预览（`@Preview` 注解）

  **不能做**：
  - 不要在组件中硬编码业务逻辑
  - 不要使用 mobile Material 组件（使用 `androidx.tv.material3`）

  **推荐 Agent Profile**：
  - **Category**: `visual-engineering` — TV UI 主题和组件
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: YES（Wave 4 内 T23-T28 均可并行启动，均只依赖 T22 DI 配置）
  - **并行组**: Wave 4
  - **阻塞**: T24-T28（UI 页面依赖主题和公共组件）
  - **被阻塞**: T22

  **参考资料**：
  - Compose for TV 官方文档：`https://developer.android.com/develop/ui/compose/tv`
  - JetStream 官方示例的 TvMaterial 使用（GitHub: android/tv-samples）
  - M3UAndroid 的 TvLazyRow 实现

  **验收标准**：
  - [ ] `MiruPlayTheme` 包装 `TvMaterialTheme`
  - [ ] `FocusableCard` 获得焦点时缩放至 1.05x + 显示边框
  - [ ] `OverscanContainer` 添加正确 padding（48dp 水平、27dp 垂直）
  - [ ] 所有组件支持 `@Preview`（无编译错误）

  **QA 场景**：

  ```
  场景: 焦点导航
    工具: Compose Testing
    前置条件: 3 个 FocusableCard 排列在 TvLazyRow
    步骤:
      1. 使用 createComposeRule() 渲染 TvLazyRow
      2. 模拟 DPAD_RIGHT 按键 → 焦点移到第 2 个卡片
      3. 断言第 2 个卡片有焦点边框
      4. 再按 DPAD_RIGHT → 焦点移到第 3 个卡片
    预期结果: DPAD 导航正确
    证据: .sisyphus/evidence/task-23-focus-nav.png

  场景: Overscan 安全边距
    工具: Compose Testing
    前置条件: OverscanContainer 包裹内容
    步骤:
      1. 渲染 OverscanContainer
      2. 获取 padding 值
      3. 断言水平 padding = 48dp, 垂直 padding = 27dp
    预期结果: 安全边距正确
    证据: .sisyphus/evidence/task-23-overscan.txt
  ```

  **提交**: YES
  - 消息: `feat(ui-tv): add TV theme, design tokens, and common composables`
  - 文件: `ui-tv/src/main/kotlin/com/miruplay/tv/ui/theme/*.kt`, `ui-tv/src/main/kotlin/com/miruplay/tv/ui/components/*.kt`

- [x] 24. UI — 添加源/设置页

  **做什么**：
  - 在 `ui-tv` 模块创建设置页 `AddSourceScreen`：
    - 源类型选择：Local / WebDAV / SMB（TvLazyRow 选择器）
    - 表单字段（DPAD 可导航）：
      - 源名称（TvTextField）
      - URL / 路径（TvTextField）
      - 用户名（TvTextField，可选）
      - 密码（TvTextField，密码模式）
    - "测试连接" 按钮（调用 `mediaSource.testConnection()`）
    - "保存" 按钮（调用 `mediaRepository.addSource()`）
  - `SourceListScreen`：已配置源列表（TvLazyColumn），每项显示类型图标 + 名称 + 连接状态 + 删除按钮
  - ViewModel：`SettingsViewModel`（@HiltViewModel）
    - `sources: StateFlow<List<MediaSourceInfo>>`
    - `testConnection(source): Result<Boolean>`
    - `addSource(source)` / `removeSource(sourceId)`
  - 状态：Loading / Success / Error（含重试）

  **完整实现**：
  - 全功能设置页（非最小），支持添加/删除/测试连接
  - TV 输入框有专门的软键盘处理

  **不能做**：
  - 不要在设置页中启动扫描（那是媒体库页的事）

  **推荐 Agent Profile**：
  - **Category**: `visual-engineering` — TV 表单 UI
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 4（与 T25-T28 并行）
  - **阻塞**: 无（被 T28 依赖用于导航集成）
  - **被阻塞**: T22, T23

  **验收标准**：
  - [ ] 源类型选择器 3 个选项（Local/WebDAV/SMB）
  - [ ] "测试连接" → 调用 `MediaSource.testConnection()`
  - [ ] "保存" → 调用 `MediaRepository.addSource()`
  - [ ] 源列表显示已配置的源（含连接状态指示）
  - [ ] DPAD 可在表单所有字段间导航

  **QA 场景**：

  ```
  场景: 添加源完整流程
    工具: Compose Testing + Mock ViewModel
    前置条件: App 启动到设置页
    步骤:
      1. 选择源类型 "WebDAV"
      2. 输入名称 "My NAS"、URL "https://192.168.1.100:8080/dav/"
      3. 点击 "测试连接"
      4. 断言 ViewModel.testConnection() 被调用
      5. 显示连接成功提示
      6. 点击 "保存"
      7. 断言 ViewModel.addSource() 被调用
    预期结果: 完整的添加源流程
    证据: .sisyphus/evidence/task-24-add-source.txt

  场景: 空源列表 → 引导提示
    工具: Compose Testing
    前置条件: sources = emptyList()
    步骤:
      1. 渲染 SourceListScreen
      2. 断言显示 "尚未配置媒体源，请添加源" 提示
      3. 断言 "添加源" 按钮可聚焦
    预期结果: 空状态有引导提示
    证据: .sisyphus/evidence/task-24-empty-state.png
  ```

  **提交**: YES
  - 消息: `feat(ui-tv): add source configuration and settings screen`
  - 文件: `ui-tv/src/main/kotlin/com/miruplay/tv/ui/settings/*.kt`

- [x] 25. UI — 媒体库列表页（首页）

  **做什么**：
  - `LibraryScreen`：Compose 首页
    - 顶部：App 标题 "MiruPlay" + 设置入口（齿轮图标按钮）
    - "继续观看" Row（TvLazyRow）：ProgressRepository.getContinueWatching() 结果，每项显示海报 + 标题 + 进度条 + 集数
    - "最近添加" Row（TvLazyRow）：按 lastScanned 排列的 Anime 列表
    - "所有番剧" Grid（TvLazyVerticalGrid）：全部已索引番剧
    - 每个番剧卡片（FocusableCard）：海报图、标题（日文/中文）、集数统计、未观看标记
    - 空白状态：无源配置 → "添加媒体源开始使用"（引导跳转到设置页）
  - ViewModel：`LibraryViewModel`（@HiltViewModel）
    - `continueWatching: StateFlow<List<ProgressWithEpisode>>`
    - `recentlyAdded: StateFlow<List<Anime>>`
    - `allAnime: StateFlow<List<Anime>>`
    - `isLoading: StateFlow<Boolean>`
    - `refresh()`：触发重新加载

  **完整实现**：
  - 功能完整的首页（3 个区域），非最小 list-only 版本
  - 进度条叠加在海报卡片上
  - 支持 TVLazyVerticalGrid 响应式列数（根据屏幕宽度自适应）

  **不能做**：
  - 不要在首页直接触发扫描（扫描由设置页或后台触发）

  **推荐 Agent Profile**：
  - **Category**: `visual-engineering`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 4
  - **阻塞**: 无（被 T28 依赖）
  - **被阻塞**: T22, T23, T17, T19

  **验收标准**：
  - [ ] "继续观看" Row 渲染 ProgressRepository 数据
  - [ ] "最近添加" Row 渲染已索引番剧
  - [ ] "所有番剧" Grid 完整列表
  - [ ] 空白状态显示引导文案
  - [ ] DPAD 可在 Row 和 Grid 之间切换

  **QA 场景**：

  ```
  场景: 继续观看 + 最近添加 数据展示
    工具: Compose Testing + Fake Repository
    前置条件: ProgressRepository 有 3 条进度, MetadataRepository 有 5 部番剧
    步骤:
      1. 渲染 LibraryScreen
      2. 断言 "继续观看" Row 有 3 个卡片
      3. 第一个卡片焦点后按 DPAD_DOWN → 焦点移到 "最近添加"
      4. 断言 "最近添加" Row 有 5 个卡片
    预期结果: 数据正确渲染，DPAD 导航流畅
    证据: .sisyphus/evidence/task-25-library-data.png

  场景: 空白状态引导
    工具: Compose Testing
    前置条件: sources = empty, anime = empty
    步骤:
      1. 渲染首页
      2. 断言显示 "添加媒体源开始使用" 文本
      3. 断言 "添加源" 按钮存在且可聚焦
    预期结果: 空白状态有明确引导
    证据: .sisyphus/evidence/task-25-empty-state.png
  ```

  **提交**: YES
  - 消息: `feat(ui-tv): add library home screen with continue watching and anime grid`
  - 文件: `ui-tv/src/main/kotlin/com/miruplay/tv/ui/library/*.kt`

- [x] 26. UI — 番剧详情页

  **做什么**：
  - `AnimeDetailScreen`：
    - 顶部横幅：fanart 背景图 + 渐变遮罩 + 标题（日文原名 + 中文名）+ 评分 + 话数 + 放送日期
    - 简介文本（可滚动，超出 3 行显示"更多"）
    - 标签行：分类标签（TvLazyRow）
    - "季" 选择器（如有多季）：TvLazyRow 切换不同 Season
    - 剧集列表（TvLazyColumn）：每集显示缩略图 + 集号 + 标题 + 进度指示器
      - 未观看：灰色圆点
      - 观看中：绿色进度条（百分比）
      - 已看完：绿色勾
    - "播放" 按钮（全宽 TvButton）+ "从开头重新播放" 按钮
    - "重新刮削元数据" 按钮
  - ViewModel：`AnimeDetailViewModel`（@HiltViewModel）
    - `anime: StateFlow<Anime?>`
    - `seasons: StateFlow<List<Season>>`
    - `selectedSeason: StateFlow<Int>`
    - `episodesWithProgress: StateFlow<List<EpisodeWithProgress>>`
    - `selectSeason(seasonNumber)`
    - `playEpisode(episode)`
    - `rescrapeMetadata()`

  **完整实现**：
  - 完整详情页（横幅 + 简介 + 季切换 + 剧集列表），非最小
  - 剧集进度指示器实时反映观看状态

  **不能做**：
  - 不要在此页面直接播放（跳转到播放器页）

  **推荐 Agent Profile**：
  - **Category**: `visual-engineering`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 4
  - **阻塞**: 无
  - **被阻塞**: T22, T23, T18, T19

  **验收标准**：
  - [ ] 横幅显示 fanart 背景 + 标题 + 评分 + 话数
  - [ ] 季选择器切换后剧集列表更新
  - [ ] 剧集列表每项进度指示器正确（未观看/观看中/已看完）
  - [ ] "播放" 按钮导航到播放器页

  **QA 场景**：

  ```
  场景: 季切换 + 剧集列表
    工具: Compose Testing + Fake 数据
    前置条件: Anime 有 2 季，Season 1 有 12 集
    步骤:
      1. 渲染 AnimeDetailScreen(animeId)
      2. 断言默认选中 Season 1，剧集列表有 12 项
      3. 选择 Season 2（DPAD_RIGHT + DPAD_CENTER）
      4. 断言剧集列表更新为 Season 2 的剧集
    预期结果: 季切换正确更新列表
    证据: .sisyphus/evidence/task-26-season-switch.png

  场景: 进度指示器
    工具: Compose Testing
    前置条件: Episode 3 观看至 50%, Episode 5 已看完
    步骤:
      1. 渲染剧集列表
      2. 断言 Episode 3 显示绿色进度条 50%
      3. 断言 Episode 5 显示绿色勾
      4. 断言 Episode 1 显示灰色圆点（未观看）
    预期结果: 进度指示器正确
    证据: .sisyphus/evidence/task-26-progress-indicators.png
  ```

  **提交**: YES
  - 消息: `feat(ui-tv): add anime detail screen with season selector and episode list`
  - 文件: `ui-tv/src/main/kotlin/com/miruplay/tv/ui/detail/*.kt`

- [x] 27. UI — 播放器页

  **做什么**：
  - `PlayerScreen`：
    - 视频渲染：`PlayerSurface`（Media3 Compose 集成）全屏
    - 播放控件覆盖层（DPAD_CENTER 显示/隐藏）：
      - 播放/暂停按钮（大图标，DPAD_CENTER 触发）
      - 进度条（`TvSlider`）：可 seek、当前位置/总时长显示
      - 快退 10s / 快进 30s 按钮
      - 字幕选择器（TvLazyRow 列出可用字幕轨道）
      - 音轨选择器（TvLazyRow 列出可用音轨）
      - 播放速度选择器（0.5x / 0.75x / 1.0x / 1.25x / 1.5x / 2.0x）
    - 控件自动隐藏（3 秒无操作后淡出）
    - 顶部信息栏：番剧名 + 集号
    - 错误状态：网络断开/编解码不支持 → ErrorMessage 覆盖层 + 重试按钮
    - DPAD_BACK：退出播放器，返回详情页
  - ViewModel：`PlayerViewModel`（@HiltViewModel）
    - `playbackState: StateFlow<PlaybackState>`
    - `currentPosition: StateFlow<Long>`（用于进度条）
    - `duration: StateFlow<Long>`
    - `availableSubtitles: StateFlow<List<SubtitleTrack>>`
    - `availableAudioTracks: StateFlow<List<AudioTrack>>`
    - `selectedSubtitle: StateFlow<Int>`
    - `selectedAudioTrack: StateFlow<Int>`
    - `playbackSpeed: StateFlow<Float>`
    - `controlsVisible: StateFlow<Boolean>`
    - `play(source)`, `pause()`, `seekTo(position)`, `skipForward()`, `skipBackward()`, `toggleControls()`
    - 定期保存进度到 `ProgressRepository`（每 15 秒）
  - 与 `MiruPlayMediaService` 集成：通过 MediaSession 同步播放状态

  **完整实现**：
  - 完整播放器 UI（控件栏 + 字幕/音轨/速度选择），非最小
  - 自动隐藏控件 + DPAD 恢复
  - 定期进度保存（15 秒间隔）

  **不能做**：
  - 不要在播放器页中实现 NFO 写回（那是 SyncEngine T39 的事）
  - 不要自定义视频渲染管线（使用 ExoPlayer PlayerSurface）

  **推荐 Agent Profile**：
  - **Category**: `visual-engineering` — 播放器 UI 最复杂
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 4
  - **阻塞**: 无
  - **被阻塞**: T20, T21, T22, T23

  **验收标准**：
  - [ ] 视频播放时 PlayerSurface 渲染画面
  - [ ] DPAD_CENTER → 播放/暂停切换
  - [ ] 进度条可 seek（DPAD_LEFT/RIGHT 微调）
  - [ ] 字幕/音轨选择器列出可用轨道
  - [ ] 控件 3 秒无操作后自动隐藏
  - [ ] DPAD_BACK → 退出播放器
  - [ ] 每 15 秒调用 `progressRepository.saveProgress()`

  **QA 场景**：

  ```
  场景: 播放控制完整流程
    工具: Compose Testing + adb
    前置条件: 播放测试视频
    步骤:
      1. 启动播放器 → 断言视频渲染
      2. DPAD_CENTER → 断言播放暂停
      3. DPAD_CENTER → 断言恢复播放
      4. DPAD_RIGHT 长按 → 进度条前进
      5. 等待 3 秒无操作 → 断言控件隐藏
      6. DPAD_CENTER → 断言控件显示
    预期结果: 播放控制正常
    证据: .sisyphus/evidence/task-27-playback-controls.png

  场景: 字幕切换
    工具: Compose Testing
    前置条件: 视频含 2 个字幕轨道
    步骤:
      1. 打开字幕选择器
      2. 选择第 2 个字幕轨道
      3. 断言 selectedSubtitle = 1
    预期结果: 字幕轨道切换生效
    证据: .sisyphus/evidence/task-27-subtitle-switch.txt
  ```

  **提交**: YES
  - 消息: `feat(ui-tv): add full player screen with controls, subtitles, and auto-save`
  - 文件: `ui-tv/src/main/kotlin/com/miruplay/tv/ui/player/*.kt`

- [x] 28. UI — 导航 + MainActivity + App 装配

  **做什么**：
  - 在 `app` 模块创建 `MainActivity`（@AndroidEntryPoint）：
    - `setContent { MiruPlayTheme { TvNavHost(...) } }`
  - 使用 Compose Navigation 定义路由：
    - `library` — 首页（默认）
    - `settings` — 设置/源管理
    - `anime/{animeId}` — 番剧详情
    - `player/{animeId}/{episodeId}` — 播放器
  - `TvNavHost`：使用 `NavHost` + `composable()` 定义路由
    - DPAD 导航自定义：处理焦点恢复（返回上一页时恢复之前焦点位置）
  - `MiruPlayApp`：@HiltAndroidApp Application 类
  - AndroidManifest.xml 最终集成：
    - `MainActivity` 声明 `LEANBACK_LAUNCHER` intent-filter
    - `MiruPlayMediaService` 注册 `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`
    - `banner` 资源引用

  **完整实现**：
  - 4 个路由 + DPAD 焦点管理，非最小
  - Activity 正确处理 `onKeyDown`（将媒体按键委托给 MediaSession）

  **不能做**：
  - 不要在 MainActivity 中写业务逻辑
  - 不要使用 Fragment（纯 Compose Navigation）

  **推荐 Agent Profile**：
  - **Category**: `visual-engineering`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: NO — 依赖 T24-T27 存在（路由指向的页面）
  - **阻塞**: 无（Wave 4 最后一个 UI 任务）
  - **被阻塞**: T24, T25, T26, T27

  **验收标准**：
  - [ ] 4 个路由都可导航（library → settings → anime/1 → player/1/3）
  - [ ] DPAD_BACK 正确返回上一页
  - [ ] 返回时焦点恢复到离开时的位置
  - [ ] `adb shell am start -n com.miruplay.tv/.MainActivity` → App 启动到首页
  - [ ] `adb shell dumpsys package com.miruplay.tv | grep "leanback"` → 确认 LEANBACK_LAUNCHER

  **QA 场景**：

  ```
  场景: 完整导航流程
    工具: Compose Testing
    前置条件: App 启动
    步骤:
      1. 首页 → 焦点选中第一个番剧 → DPAD_CENTER
      2. 断言导航到 anime/{id}
      3. 详情页 → 选中第一集 → "播放" → DPAD_CENTER
      4. 断言导航到 player/{animeId}/{episodeId}
      5. DPAD_BACK → 断言返回详情页
      6. DPAD_BACK → 断言返回首页
    预期结果: 完整导航流程无 crash
    证据: .sisyphus/evidence/task-28-navigation-flow.txt

  场景: TV Launcher 可见性
    工具: Bash (adb)
    前置条件: APK 安装到 TV 模拟器
    步骤:
      1. 执行: adb shell pm list packages | grep miruplay
      2. 执行: adb shell dumpsys package com.miruplay.tv | grep leanback
    预期结果: LEANBACK_LAUNCHER 存在
    证据: .sisyphus/evidence/task-28-launcher-verify.txt
  ```

  **提交**: YES
  - 消息: `feat(app): wire up navigation, MainActivity, and TV launcher`
  - 文件: `app/src/main/kotlin/com/miruplay/tv/MainActivity.kt`, `app/src/main/kotlin/com/miruplay/tv/navigation/*.kt`, `app/src/main/kotlin/com/miruplay/tv/MiruPlayApp.kt`

- [x] 29. Scanner — 剧集命名模式识别 (TDD)
- [x] 30. Scanner — 目录遍历 + 索引引擎 (TDD)
- [x] 31. Scanner 集成 Repository
- [x] 32. Metadata — NFO 解析器 (TDD)
- [x] 33. Metadata — NFO 写入器 (TDD)
- [x] 34. Metadata — ViewModel 层集成

  **做什么**：
  - 更新 `AnimeDetailViewModel` 和 `PlayerViewModel` 集成元数据：
    - `AnimeDetailViewModel`：加载详情时优先从 `MetadataRepository` 获取缓存，无缓存时检查 NFO 文件（通过 `NfoParser`），无 NFO 时显示"元数据不可用"
    - `PlayerViewModel`：播放开始时检查 NFO 是否存在以决定进度恢复策略
  - 创建 `MetadataRefreshUseCase`：
    - `refreshMetadata(animeId)`：依次检查本地缓存 → NFO 文件 → 触发刮削（未来 Wave 5）
  - UI 表现：
    - 有元数据：显示完整信息（标题、简介、评分、海报）
    - 无元数据：显示文件名作为后备标题，显示"元数据不可用"

  **完整实现**：
  - 完整元数据回退链：缓存 → NFO → 后备

  **不能做**：
  - 不要在此任务中实现刮削（那是 T35 的事）

  **推荐 Agent Profile**：
  - **Category**: `deep`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 4（可与其他任务并行）
  - **阻塞**: 无
  - **被阻塞**: T18, T32, T33

  **验收标准**：
  - [ ] 缓存命中 → 直接返回（不读 NFO）
  - [ ] 缓存未命中 + NFO 存在 → 解析 NFO 返回
  - [ ] 缓存未命中 + NFO 不存在 → 返回后备标题（文件名）
  - [ ] ViewModel 正确集成（StateFlow 更新触发 UI 重组）

  **QA 场景**：

  ```
  场景: 元数据回退链
    工具: Bash (gradle test)
    前置条件: 配置不同元数据可用性
    步骤:
      1. 缓存+NFO 都有 → 断言使用缓存（不读 NFO）
      2. 仅 NFO → 断言解析 NFO
      3. 都没有 → 断言返回后备标题
    预期结果: 回退链正确
    证据: .sisyphus/evidence/task-34-fallback-chain.txt
  ```

  **提交**: YES
  - 消息: `feat(metadata): integrate metadata into ViewModel layer with fallback chain`
  - 文件: `ui-tv/src/main/kotlin/com/miruplay/tv/ui/detail/AnimeDetailViewModel.kt`（更新），`player-core`/相关 usecase

- [x] 35. AniList Scraper + Bangumi Archive Scraper 实现

  **做什么**：
  - 在 `scraper` 模块实现两个刮削器：
  - **AniListScraper**（在线 GraphQL API）：
    - GraphQL endpoint：`https://graphql.anilist.co`
    - 查询：`searchAnime(query)` → `Page { media(search: $query, type: ANIME) { id, title { romaji, english, native }, description, episodes, seasonYear, averageScore, coverImage { large }, genres, studios { nodes { name } } } }`
    - 查询：`getAnimeDetails(id)` → 同上但按 ID 查询，含更多字段
    - 查询：`getEpisodes(id)` → 暂不可用（AniList API 不直接提供剧集列表，需通过其他方式）
    - HTTP 客户端：OkHttp + kotlinx.serialization JSON
    - 限流处理：AniList 限流 90 req/min → 内置 rate limiter（Token Bucket）
  - **BangumiArchiveScraper**（离线/在线 Archive）：
    - 参考 anime-organizer 的 Bangumi Archive 方案：
    - 数据源：`https://raw.githubusercontent.com/bangumi/archive/master/data/subject/{id}.json`
    - 批量 dump：`https://github.com/bangumi/archive/releases/latest/download/subject.jsonlines.gz`
    - `searchAnime(query)`：先本地 SQLite 别名匹配（AliasResolver），无匹配 → 回退到 AniList
    - `getAnimeDetails(id)`：直接获取 Bangumi subject JSON
    - `getEpisodes(id)`：从 episode dump 提取剧集列表
  - **AliasResolverImpl**（参考 anime-organizer）：
    - 别名匹配流程：规范化输入名 → 精确匹配 → 模糊匹配（Unicode 归一化 + 标点移除 + 大小写）
    - 支持自定义别名 JSON 文件导入
  - `ScraperManager`：管理多个 `MetadataScraper`，按优先级回退（AniList → Bangumi Archive）

  **完整实现**：
  - 两个刮削器 + 别名解析器完全实现，支持回退链

  **不能做**：
  - 不要在 UI 线程做网络请求
  - 不要为 Bangumi Archive 下载完整 dump 到移动设备（仅按需查询）

  **推荐 Agent Profile**：
  - **Category**: `unspecified-high` — 多 API 集成 + GraphQL + JSON
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 5（与 WebDAV/SMB 并行）
  - **阻塞**: T42（全局错误处理需要知道 scraper 错误类型）
  - **被阻塞**: T11, T5

  **验收标准**：
  - [ ] AniList `searchAnime("Bocchi the Rock")` 返回正确 results
  - [ ] Bangumi Archive `getAnimeDetails(328609)` 返回 "孤独摇滚！"
  - [ ] `AliasResolver.resolve("Bocchi the Rock!")` → bangumiId=328609
  - [ ] 两个 scraper 失败时回退到下一个
  - [ ] Rate limiter 限流起作用（不会 429 错误）

  **QA 场景**：

  ```
  场景: AniList 搜索
    工具: Bash (gradle test 或 curl)
    前置条件: 网络可用
    步骤:
      1. searchAnime("Bocchi the Rock")
      2. 断言 results 非空，第一条 title.romaji = "Bocchi the Rock!"
    预期结果: 搜索返回正确结果
    证据: .sisyphus/evidence/task-35-anilist-search.json

  场景: Bangumi 别名匹配
    工具: Bash (gradle test)
    前置条件: 别名规则已配置
    步骤:
      1. resolve("孤独摇滚") → 断言 bangumiId = 328609
      2. resolve("BOCCHI THE ROCK!") → 断言 bangumiId = 328609（大小写不敏感）
    预期结果: 别名匹配准确
    证据: .sisyphus/evidence/task-35-alias-match.txt
  ```

  **提交**: YES
  - 消息: `feat(scraper): implement AniList GraphQL and Bangumi Archive scrapers`
  - 文件: `scraper/src/main/kotlin/com/miruplay/tv/scraper/impl/*.kt`, `scraper/src/test/**`

- [x] 36. WebDAV MediaSource 实现 (TDD)

  **做什么**：
  - 实现 `WebDavMediaSource`（基于 OkHttp）：
    - `listFiles(path)`：发送 `PROPFIND` 请求 → 解析 XML 响应（`multistatus/response/href`）→ 返回 FileEntry 列表
      - 处理 URL 编码（Unicode 路径）
      - 处理不同 WebDAV 服务器的 XML 格式差异（Apache、nginx、NAS）
    - `openStream(path)`：`GET` 请求 + `Range` header 支持
      - 先检查 `Accept-Ranges: bytes`（HEAD 请求），不支持则返回 `MediaCapabilities(supportsRange=false)`
    - `getMetadata(path)`：HEAD 请求获取 Content-Length / Content-Type / Last-Modified
    - `testConnection()`：尝试 `PROPFIND /` 验证连通性
    - SSL 处理：自签名证书 → 用户确认后可信任
  - 认证：Basic Auth / Digest Auth（通过 OkHttp Authenticator）
  - 单元测试：使用 MockWebServer 模拟 WebDAV 响应

  **完整实现**：
  - 完整 WebDAV 实现（PROPFIND 解析 + Range 支持 + 认证 + SSL）

  **不能做**：
  - 不要缓存 WebDAV 响应（让上层 Repository 决定）

  **推荐 Agent Profile**：
  - **Category**: `unspecified-high` — HTTP + XML + 网络编程
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 5（与 T37 SMB 完全并行）
  - **阻塞**: T38
  - **被阻塞**: T8, T16

  **验收标准**：
  - [ ] `listFiles("/")` 返回正确文件树（通过 MockWebServer 验证）
  - [ ] `openStream()` 支持 Range 请求（断点续传）
  - [ ] Basic Auth 认证正确
  - [ ] 连接超时 → `MediaSourceError.Timeout`
  - [ ] SSL 证书错误 → `MediaSourceError.ConnectionLost`（含用户提示）
  - [ ] Unicode 路径正确 URL 编码

  **QA 场景**：

  ```
  场景: PROPFIND + GET + Range
    工具: Bash (gradle test + MockWebServer)
    前置条件: MockWebServer 返回预设 WebDAV 响应
    步骤:
      1. listFiles("/") → 断言返回文件列表
      2. HEAD /video.mkv → 断言 Accept-Ranges: bytes
      3. GET /video.mkv Range: bytes=1000- → 断言返回 206 Partial Content
    预期结果: WebDAV 协议正确实现
    证据: .sisyphus/evidence/task-36-webdav-protocol.txt

  场景: 认证失败
    工具: Bash (gradle test)
    前置条件: MockWebServer 返回 401
    步骤:
      1. testConnection() → 断言 Result.Error(MediaSourceError.AuthenticationFailed)
    预期结果: 认证错误正确区分
    证据: .sisyphus/evidence/task-36-auth-fail.txt
  ```

  **提交**: YES
  - 消息: `feat(media-source): implement WebDAV media source with PROPFIND and Range support`
  - 文件: `media-source/src/main/kotlin/com/miruplay/tv/mediasource/webdav/*.kt`, `media-source/src/test/**`

- [x] 37. SMB MediaSource 实现 (TDD)

  **做什么**：
  - 实现 `SmbMediaSource`（基于 jcifs-ng）：
    - `listFiles(path)`：使用 `SmbFile.listFiles()` 遍历，映射到 FileEntry
    - `openStream(path)`：`SmbFile.getInputStream()` 返回流，seekable via `SmbRandomAccessFile`
    - `getMetadata(path)`：`SmbFile.lastModified()` / `length()` / `isDirectory()`
    - `testConnection()`：尝试 `SmbFile(path).exists()`
  - SMB 会话管理：
    - 连接池（复用 `SmbFile` 会话）
    - 超时处理：`jcifs.smb.client.responseTimeout`
    - 匿名访问 / 用户认证两种模式
  - 平台兼容：
    - SMB2/SMB3 协商（通过 jcifs-ng 配置）
    - 中文/日文文件名编码处理
  - 单元测试：使用 Fake SMB 服务器或 Docker Samba 容器

  **完整实现**：
  - 完整 SMB 实现（会话管理 + 超时 + 编码处理）

  **不能做**：
  - 不要在 UI 线程做 SMB 操作
  - 不要硬编码 SMB 端口（支持自定义端口）

  **推荐 Agent Profile**：
  - **Category**: `unspecified-high` — SMB 协议 + jcifs-ng
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 5
  - **阻塞**: T38
  - **被阻塞**: T8, T16

  **验收标准**：
  - [ ] `listFiles("smb://host/share/")` 返回正确目录
  - [ ] `openStream()` 支持 seek（RandomAccessFile 机制）
  - [ ] 匿名访问和用户认证两种模式都工作
  - [ ] 连接失败 → `MediaSourceError.ConnectionLost`
  - [ ] 日文文件名（Shift-JIS/UTF-8）正确读取

  **QA 场景**：

  ```
  场景: SMB 列表 + 读取
    工具: Bash (gradle test + Fake SMB)
    前置条件: 模拟 SMB 共享
    步骤:
      1. listFiles("smb://test/share/anime/") → 断言返回文件列表
      2. openStream("smb://test/share/anime/ep01.mkv") → 断言可读取内容
      3. seekTo(5000) via RandomAccessFile → 断言位置跳转正确
    预期结果: SMB 操作正确
    证据: .sisyphus/evidence/task-37-smb-read-seek.txt

  场景: 连接失败优雅降级
    工具: Bash (gradle test)
    前置条件: 无效 SMB 地址
    步骤:
      1. testConnection("smb://invalid/share/") 
      2. 断言超时 < 10 秒
      3. 断言返回 MediaSourceError.ConnectionLost（非 crash）
    预期结果: 超时 + 错误处理
    证据: .sisyphus/evidence/task-37-timeout.txt
  ```

  **提交**: YES
  - 消息: `feat(media-source): implement SMB media source with jcifs-ng`
  - 文件: `media-source/src/main/kotlin/com/miruplay/tv/mediasource/smb/*.kt`, `media-source/src/test/**`

- [x] 38. RemoteRepository 适配层
- [x] 39. SyncEngine — 进度同步核心 (TDD)
- [x] 40. SyncEngine — 冲突检测与解决 (TDD)
- [x] 41. SyncEngine — 定期自动同步调度

  **做什么**：
  - 实现 `AutoSyncScheduler`：
    - 触发时机：
      - App 进入前台（`ON_RESUME`）→ 触发一次全量同步
      - 播放器每 15 秒保存进度 → 触发单集同步（轻量）
      - App 进入后台（`ON_PAUSE`）→ 触发一次紧急同步（保存所有待同步进度）
    - 使用 `CoroutineScope` + `delay()` 实现定时器（非 WorkManager，保持简单）
    - 去重：同一 episode 在冷却期内不重复同步（`minSyncInterval = 30s`）
  - `SyncQueue`：缓冲待同步的 episodeId，逐个处理
  - 错误处理：
    - 网络断开 → 暂停同步，等待网络恢复
    - NFO 写入失败 → 重试 3 次 → 放弃（标记为待下次同步）
  - 集成到 `MiruPlayApp` 的 `ActivityLifecycleCallbacks`

  **完整实现**：
  - 完整生命周期感知的自动同步调度器

  **不能做**：
  - 不要使用 WorkManager（过度设计）
  - 不要在后台持续运行（TV 不适用）

  **推荐 Agent Profile**：
  - **Category**: `deep`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 5
  - **阻塞**: 无
  - **被阻塞**: T22, T39

  **验收标准**：
  - [ ] App 启动时自动同步一次
  - [ ] 播放器每 15 秒触发保存 → 同步到 NFO
  - [ ] 退出 App 时紧急同步
  - [ ] 同一 episode 30 秒内不重复同步

  **QA 场景**：

  ```
  场景: 15 秒自动同步
    工具: Bash (adb + logcat)
    前置条件: 播放中
    步骤:
      1. 开始播放, 监控 logcat "SyncEngine"
      2. 等待 15 秒
      3. 断言 logcat 显示 "syncEpisode(ep1, position=15000)"
      4. 等待 30 秒
      5. 断言 logcat 显示 "syncEpisode(ep1, position=30000)"
    预期结果: 定期同步触发
    证据: .sisyphus/evidence/task-41-auto-sync-log.txt

  场景: App 退出时紧急同步
    工具: Bash (adb)
    前置条件: 播放中
    步骤:
      1. 按 BACK 退出播放器
      2. 监控 logcat → 断言 onPause 触发同步
      3. 验证 NFO 文件被更新
    预期结果: 退出时保存进度不丢失
    证据: .sisyphus/evidence/task-41-emergency-sync.txt
  ```

  **提交**: YES
  - 消息: `feat(sync-engine): add lifecycle-aware auto-sync scheduler`
  - 文件: `sync-engine/src/main/kotlin/com/miruplay/tv/sync/scheduler/*.kt`

- [x] 42. 全局错误处理 + 网络故障恢复

  **做什么**：
  - 创建 `GlobalErrorHandler`（注入到各 ViewModel）：
    - 统一 `Result.Error` → 用户消息转换
    - `NetworkMonitor`：观察 `ConnectivityManager`，检测网络状态变化
    - 网络恢复时：
      - 自动重试失败的同步
      - 自动重连断开的 MediaSource
      - 通知 UI 更新连接状态图标
  - 错误日志：记录到 Room `ErrorLogEntity`（最近 100 条，环形覆盖）
  - UI 层：
    - `ErrorMessage` 公共组件已含重试按钮
    - 全局 Snackbar / Toast 显示网络恢复提示

  **完整实现**：
  - 完整全局错误处理 + 网络监控 + 自动恢复

  **不能做**：
  - 不要在错误处理中执行长时间运行的操作

  **推荐 Agent Profile**：
  - **Category**: `deep`
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: Wave 5
  - **阻塞**: 无
  - **被阻塞**: T22, T35-T41

  **验收标准**：
  - [ ] 所有 `AppError` 子类型有对应中文用户消息
  - [ ] 网络断开 → UI 显示 "网络已断开" 提示
  - [ ] 网络恢复 → 自动重连 MediaSource + 触发同步
  - [ ] ErrorLog 记录最近 100 条错误（含时间戳和堆栈）

  **QA 场景**：

  ```
  场景: 网络断开 + 恢复
    工具: Bash (adb + 网络模拟)
    前置条件: WebDAV 源已连接
    步骤:
      1. 断开网络: adb shell svc wifi disable
      2. 断言 UI 显示网络断开提示
      3. 恢复网络: adb shell svc wifi enable
      4. 等待 5 秒
      5. 断言 UI 显示 "已重新连接"
      6. 断言自动同步触发
    预期结果: 网络恢复自动处理
    证据: .sisyphus/evidence/task-42-network-recovery.txt
  ```

  **提交**: YES
  - 消息: `feat(core): add global error handling and network recovery`
  - 文件: `core-common/src/main/kotlin/com/miruplay/tv/common/errorhandler/*.kt`, `app/src/main/kotlin/com/miruplay/tv/NetworkMonitor.kt`

- [x] 43. 集成测试 + 边缘情况修复

  **做什么**：
  - 端到端集成测试（使用 LocalMediaSource + Room 内存数据库）：
    - 测试 1：添加源 → 扫描 → 浏览 → 播放 → 进度保存 → 退出 → 重新打开 → 继续播放
    - 测试 2：添加 2 个源 → 扫描 → 合并索引（同名番剧跨源合并）
    - 测试 3：播放中网络断开 → 重连后自动恢复
    - 测试 4：NFO 冲突 → 用户选择 LOCAL_WINS → 验证结果
    - 测试 5：大目录扫描（500 文件）→ 验证性能 < 10 秒 + 无 OOM
    - 测试 6：Unicode 文件名全流程（日文/中文/韩文/阿拉伯文）
  - 边缘情况修复（基于集成测试发现的问题）：
    - 空目录扫描 → 不崩溃
    - 超长路径 → 跳过并记录
    - 并发扫描多源 → 各自独立不干扰
    - 播放器 seek 到文件末尾 → 正确处理 Ended 状态
    - Room 数据库版本迁移（测试 schema 变更）

  **完整实现**：
  - 6 个集成测试 + 边缘情况修复

  **不能做**：
  - 不要引入新的功能特性（仅修复测试发现的问题）

  **推荐 Agent Profile**：
  - **Category**: `deep` — 集成测试 + 调试
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: NO — 依赖所有前序任务
  - **阻塞**: F1-F4（最终验证）
  - **被阻塞**: T1-T42

  **验收标准**：
  - [ ] 6 个集成测试全部通过
  - [ ] 边缘情况修复后回归测试通过
  - [ ] `./gradlew :app:connectedDebugAndroidTest` 全部通过

  **QA 场景**：

  ```
  场景: 完整用户流程集成测试
    工具: Android Instrumentation Test
    前置条件: 模拟 TV 环境
    步骤:
      1. 添加 Local 源 → 扫描 → 断言番剧出现在首页
      2. 点击番剧 → 断言详情页显示
      3. 点击剧集 → 断言播放器启动
      4. 播放 10 秒 → 退出
      5. 重新打开 → 断言"继续观看"显示该剧集（position≈10s）
    预期结果: 端到端流程无 crash
    证据: .sisyphus/evidence/task-43-e2e-flow.txt
  ```

  **提交**: YES
  - 消息: `test: add integration tests and fix edge cases`
  - 文件: 各模块 `src/androidTest/**`，边缘情况修复涉及的源文件

---

## Final Verification Wave（强制 — 所有实现任务完成后）

> 4 个审查 Agent 并行运行。全部必须 APPROVE。汇总结果后呈现给用户，获得用户明确确认。
> **在获得用户确认前，不要标记 F1-F4 为已完成。**

- [ ] F1. **计划合规审计** — `oracle`

  通读整个计划。对每个 "Must Have"：验证实现存在（读文件、curl 端点、运行命令）。对每个 "Must NOT Have"：搜索代码库寻找禁止模式 — 如发现，返回 `file:line`。检查证据文件存在于 `.sisyphus/evidence/`。对照交付物。
  - [ ] 11 个模块编译通过：`./gradlew assembleDebug`
  - [ ] 所有 TDD 测试通过：`./gradlew testDebugUnitTest`
  - [ ] AndroidManifest 声明 `LEANBACK_LAUNCHER`
  - [ ] MediaSession 可见：`adb shell dumpsys media_session | grep miruplay`
  - [ ] 无禁止模式：自定义字幕渲染器 / 视频转码 / 云端同步 / 用户认证 / 社交功能 / Cast 支持 / 动态插件
  - [ ] 包名正确：`com.miruplay.tv`
  - **输出**：`Must Have [N/N] | Must NOT Have [N/N] | Tasks [43/43] | VERDICT: APPROVE/REJECT`

- [ ] F2. **代码质量审查** — `unspecified-high`

  运行 `./gradlew :app:lintDebug` + `./gradlew :app:assembleDebug`（含 R8）。审查所有变更文件：
  - [ ] 构建：`./gradlew assembleDebug` → PASS
  - [ ] Lint：`./gradlew lintDebug` → 无 warning 以上
  - [ ] R8/ProGuard：无保留规则缺失导致的运行时错误
  - [ ] AI slop 检测：无 `as any`/`@ts-ignore`，无空 catch 块，无 `console.log`，无法释出代码，无未用 import
  - [ ] 过度抽象检测：无只有 1 个实现的接口（除非设计中明确是扩展点），无 `data`/`result`/`item`/`temp` 等泛化命名
  - [ ] Kotlin 惯用写法：使用 `when` 穷举 sealed class，使用 `?.let`/`?:` 而非 if-null 检查
  - [ ] Room DAO：无 `SELECT *`（显式列出所需列）
  - **输出**：`Build [PASS/FAIL] | Lint [PASS/FAIL] | R8 [PASS/FAIL] | Files [N clean/N issues] | VERDICT`

- [ ] F3. **真实 QA 验证** — `unspecified-high`（+ `playwright` skill for UI）

  从干净状态启动。执行每个任务的 QA 场景 — 遵循精确步骤，捕获证据。测试跨任务集成（功能协同工作，而非孤立）。测试边缘情况：空状态、无效输入、快速操作。保存到 `.sisyphus/evidence/final-qa/`。
  - [ ] 场景 1：App 首次启动 → 显示空白状态引导
  - [ ] 场景 2：添加 Local 源 → 扫描 → 首页出现番剧
  - [ ] 场景 3：浏览番剧详情 → 播放 → 进度保存 → 退出 → 继续观看
  - [ ] 场景 4：WebDAV 源添加 + 连接测试 + 扫描
  - [ ] 场景 5：SMB 源添加 + 连接测试 + 扫描
  - [ ] 场景 6：网络断开 → 播放中卡顿 → 重连 → 恢复
  - [ ] 场景 7：退出 App 后 NFO 进度被正确写入
  - [ ] 场景 8：DPAD 导航全程不卡死（所有页面所有元素可达）
  - [ ] 场景 9：TalkBack 辅助功能（若支持）
  - **输出**：`Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [ ] F4. **范围一致性检查** — `deep`

  对每个任务：读取 "做什么"，读取实际 diff（`git diff`）。验证 1:1 — 规格中所有内容都已构建（无遗漏），未构建规格外内容（无蔓延）。检查 "不能做" 合规性。检测跨任务污染：Task N 触碰 Task M 的文件。标记未预见的变更。
  - [ ] 每个 TODO 的 "做什么" 与实际 diff 匹配
  - [ ] 每个 "不能做" 在代码库中无违反
  - [ ] 跨任务文件污染：0 实例
  - [ ] 未预见文件：列出并评审
  - **输出**：`Tasks [43/43 compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

| Wave | 任务 | 提交消息格式 |
|------|------|-------------|
| 1 | T1-T7 | 独立提交，每个任务按 `type(scope): desc` 格式 |
| 2 | T8-T15 | 每个接口定义独立提交 |
| 3 | T16-T22 | 每个实现独立提交（含测试文件） |
| 4 | T23-T34 | UI 任务独立提交，Scanner/Metadata 独立提交 |
| 5 | T35-T43 | 远程源独立提交，同步引擎独立提交 |
| FINAL | F1-F4 | 不提交（审查阶段） |

**提交消息前缀约定**：
- `chore:` — 构建/配置/基础设施
- `feat:` — 新功能/模块
- `test:` — 仅测试相关
- `fix:` — 修复/边缘情况

---

## 成功标准

### 验证命令

```bash
# 完整构建
./gradlew assembleDebug
# 预期: BUILD SUCCESSFUL

# 所有单元测试
./gradlew testDebugUnitTest
# 预期: 所有模块测试 PASS

# APK 安装到 TV 模拟器
adb install app/build/outputs/apk/debug/app-debug.apk
# 预期: Success

# TV Launcher 可见
adb shell dumpsys package com.miruplay.tv | grep leanback
# 预期: LEANBACK_LAUNCHER 存在

# MediaSession
adb shell dumpsys media_session | grep miruplay
# 预期: 可见 MiruPlay 会话
```

### 最终检查清单

- [ ] 所有 "Must Have" 存在（7 模块、TDD、MediaSession、DPAD UI、NFO 读写、Scraper、SyncEngine）
- [ ] 所有 "Must NOT Have" 不存在（自定义字幕渲染器、视频转码、云端同步等）
- [ ] 所有测试通过（单元 + 集成 + Compose UI）
- [ ] `.sisyphus/evidence/` 下所有 QA 证据文件存在
- [ ] minSdk 28 设备上可安装运行
- [ ] DPAD 导航所有页面无卡死
- [ ] 网络断开/重连优雅处理

- [x] 1. Gradle 构建系统搭建

  **做什么**：
  - 创建 Gradle Wrapper（`gradle wrapper --gradle-version 8.10`）
  - 创建根 `build.gradle.kts`：配置 `plugins { id("com.android.application") apply false; id("com.android.library") apply false; id("org.jetbrains.kotlin.android") apply false; id("com.google.dagger.hilt.android") apply false; id("com.google.devtools.ksp") apply false }`
  - 创建 `settings.gradle.kts`：启用 `dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }`，包含 `pluginManagement` 指向 `build-logic`（后续 convention plugin 使用）
  - 创建 `gradle.properties`：设置 `android.useAndroidX=true`、`kotlin.code.style=official`、`android.nonTransitiveRClass=true`、`org.gradle.jvmargs=-Xmx4g`、`hilt.enableExperimentalClasspathAggregation=true`
  - 创建 `local.properties` 模板（不含 sdk.dir 实际值 — 由开发者本地配置）

  **不能做**：
  - 不要在此任务中创建任何模块的 build.gradle.kts（那是 T4）
  - 不要配置具体的库版本（那是 T2 catalog 的事）
  - 不要添加 AGP 插件（仅声明 apply false）

  **推荐 Agent Profile**：
  - **Category**: `quick` — 纯 Gradle 配置，无复杂逻辑
  - **Skills**: `[]`
  - **理由**: 标准 Gradle 项目初始化，快速任务

  **并行化**：
  - **可并行**: YES
  - **并行组**: Wave 1（可与 T2 并行，均无相互依赖）
  - **阻塞**: T4（需要 root build.gradle.kts 中的插件声明）
  - **被阻塞**: 无（可立即开始）

  **参考资料**：
  - Android 官方多模块指南：`https://developer.android.com/topic/modularization`
  - Gradle 官方文档：`https://docs.gradle.org/8.10/userguide/multi_project_builds.html`
  - 参考项目 M3UAndroid 根构建文件结构（GitHub: oxyroid/M3UAndroid）

  **验收标准**：
  - [ ] `./gradlew --version` 输出 Gradle 8.10
  - [ ] `./gradlew projects` 列出根项目（目前无子模块）
  - [ ] `gradle.properties` 包含 `hilt.enableExperimentalClasspathAggregation=true`
  - [ ] `settings.gradle.kts` 正确配置 `dependencyResolutionManagement`

  **QA 场景**：

  ```
  场景: Gradle Wrapper 正常工作
    工具: Bash
    前置条件: 无
    步骤:
      1. 执行: ./gradlew --version
      2. 检查输出包含 "Gradle 8.10"
      3. 检查输出包含 "Kotlin: 2.0.21"（如已配置）
    预期结果: 输出版本信息，无错误
    证据: .sisyphus/evidence/task-1-gradle-version.txt

  场景: settings.gradle.kts 语法正确
    工具: Bash
    前置条件: T1 完成
    步骤:
      1. 执行: ./gradlew projects
      2. 检查输出包含 "Root project 'MiruPlay'"
    预期结果: 无语法错误，列出根项目
    失败指标: 任何 "Build failed" 或语法错误
    证据: .sisyphus/evidence/task-1-gradle-projects.txt
  ```

  **证据捕获**：
  - [ ] `task-1-gradle-version.txt` — Gradle 版本输出
  - [ ] `task-1-gradle-projects.txt` — 项目列表输出

  **提交**: YES（分组 T1-T2）
  - 消息: `chore: initialize Gradle build system`
  - 文件: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`

- [x] 2. Version Catalog (libs.versions.toml)

  **做什么**：
  - 创建 `gradle/libs.versions.toml`
  - 定义 `[versions]`：AGP 8.7.0、Kotlin 2.0.21、Compose BOM 2026.03.00、tv-material 1.0.0、Media3 1.10.0、Hilt 2.52、Room 2.6.1、KSP 2.0.21-1.0.28、Kotlinx Serialization 1.7.3、OkHttp 4.12.0、jcifs-ng 2.1.10、JUnit 5.11.0、MockK 1.13.12、Turbine 1.1.0、Compose Testing 1.7.5
  - 定义 `[libraries]`：所有依赖项，按类别分组（compose、tv、media3、hilt、room、network、testing）
  - 定义 `[plugins]`：android-application、android-library、kotlin-android、hilt、ksp、kotlin-serialization
  - 定义 `[bundles]`：compose、media3、room、testing

  **不能做**：
  - 不要包含不在当前范围使用的库
  - 不要使用 `strictly` 约束（除非确实需要版本收敛）

  **推荐 Agent Profile**：
  - **Category**: `quick` — 标准 version catalog 配置
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: YES
  - **并行组**: Wave 1（与 T1 完全独立）
  - **阻塞**: T3（convention plugin 引用 catalog）、T4（模块 build 引用 catalog）
  - **被阻塞**: 无

  **参考资料**：
  - Gradle Version Catalog 官方文档：`https://docs.gradle.org/8.10/userguide/platforms.html#sub:version-catalog`
  - Android 官方版本目录示例
  - 参考 M3UAndroid 的 `gradle/libs.versions.toml`

  **验收标准**：
  - [ ] `libs.versions.toml` 语法有效（TOML 解析无错误）
  - [ ] 所有 7 个模块的依赖都在 catalog 中有对应条目
  - [ ] 版本号不含冲突（同一库的多条引用版本一致）
  - [ ] bundles 覆盖常用组合（compose、media3、room、testing）

  **QA 场景**：

  ```
  场景: Version catalog 可解析
    工具: Bash
    前置条件: T1 完成
    步骤:
      1. 执行: ./gradlew dependencies --configuration classpath 2>&1 | head -20
      2. 检查无 "Could not resolve" 错误
    预期结果: 无解析错误（catalog 语法正确）
    证据: .sisyphus/evidence/task-2-catalog-verify.txt

  场景: 版本号一致性（无冲突）
    工具: Grep
    前置条件: libs.versions.toml 存在
    步骤:
      1. 搜索 kotlin 版本是否在多处定义
      2. 搜索 compose 版本是否一致
    预期结果: 每个库只有一个版本号（在 [versions] 中定义，其他地方引用）
    证据: .sisyphus/evidence/task-2-version-consistency.txt
  ```

  **提交**: YES（分组 T1-T2）
  - 消息: `chore: add Gradle version catalog`
  - 文件: `gradle/libs.versions.toml`

- [x] 3. Convention Plugin (build-logic)

  **做什么**：
  - 创建 `build-logic/settings.gradle.kts`：`dependencyResolutionManagement { versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } } }`
  - 创建 `build-logic/convention/build.gradle.kts`：依赖 `com.android.library`、`org.jetbrains.kotlin.android`、`com.google.dagger.hilt.android`、`com.google.devtools.ksp` 插件
  - 创建 Android Library Convention Plugin：`build-logic/convention/src/main/kotlin/miruplay.android.library.gradle.kts`
    - 应用 `com.android.library`、`org.jetbrains.kotlin.android`、`com.google.dagger.hilt.android`、`com.google.devtools.ksp`
    - 设置 `compileSdk = 35`、`minSdk = 28`、`targetSdk = 35`
    - 配置 `composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }`
    - 配置 `buildFeatures { compose = true }`
    - 统一 `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`
  - 创建 Android Application Convention Plugin（给 `:app` 模块用）：`build-logic/convention/src/main/kotlin/miruplay.android.application.gradle.kts`
    - 继承 library plugin 的所有配置
    - 应用 `com.android.application`
    - 配置 `applicationId = "com.miruplay.tv"`、`versionCode = 1`、`versionName = "0.1.0"`

  **不能做**：
  - 不要在 convention plugin 中指定具体的 implementation 依赖（那属于各模块自己的 build.gradle.kts）
  - 不要为 convention plugin 创建过多变体（目前只需 library + application 两种）

  **推荐 Agent Profile**：
  - **Category**: `quick` — 标准 Gradle convention plugin 配置
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: NO — 依赖 T2（需要 catalog 可用）
  - **并行组**: Wave 1
  - **阻塞**: T4（模块 build 文件引用 convention plugin）
  - **被阻塞**: T2

  **参考资料**：
  - Gradle Convention Plugins 官方文档：`https://docs.gradle.org/8.10/userguide/sharing_build_logic_between_subprojects.html`
  - Android 官方 Now in Android 项目的 convention plugin 实现

  **验收标准**：
  - [ ] `miruplay.android.library.gradle.kts` 正确配置 compileSdk/minSdk/targetSdk
  - [ ] `miruplay.android.application.gradle.kts` 正确配置 applicationId
  - [ ] Convention plugin 可被 T4 中的模块 build 文件正确引用

  **QA 场景**：

  ```
  场景: Convention plugin 语法编译
    工具: Bash
    前置条件: T1, T2 完成
    步骤:
      1. 执行: ./gradlew :build-logic:convention:build
      2. 检查 BUILD SUCCESSFUL
    预期结果: Convention plugin 编译成功
    失败指标: 编译错误（缺少依赖、语法错误）
    证据: .sisyphus/evidence/task-3-convention-build.txt
  ```

  **提交**: YES
  - 消息: `chore: add convention plugins for multi-module build`
  - 文件: `build-logic/settings.gradle.kts`, `build-logic/convention/build.gradle.kts`, `build-logic/convention/src/main/kotlin/miruplay.android.library.gradle.kts`, `build-logic/convention/src/main/kotlin/miruplay.android.application.gradle.kts`

- [x] 4. 所有模块目录 + 各模块 build.gradle.kts

  **做什么**：
  - 创建 11 个模块目录（`app`, `core-model`, `core-common`, `media-source`, `player-core`, `scanner`, `metadata`, `scraper`, `sync-engine`, `data`, `ui-tv`）
  - 每个模块创建标准目录结构：`src/main/kotlin/com/miruplay/tv/{module}/`、`src/main/res/`（仅 app 和 ui-tv 需要）、`src/test/kotlin/com/miruplay/tv/{module}/`、`src/androidTest/kotlin/com/miruplay/tv/{module}/`
  - 每个模块创建 `build.gradle.kts`：
    - 应用 convention plugin（`miruplay.android.library` 或 `miruplay.android.application`）
    - 声明 namespace（`com.miruplay.tv.{module}`）
    - 声明模块间依赖（api vs implementation 正确区分）
    - 声明外部库依赖（通过 libs catalog）
  - 更新 `settings.gradle.kts`：添加所有 11 个模块的 `include()`
  - `:app` 模块：配置 applicationId、签名配置（debug）、AndroidManifest.xml 模板

  **依赖关系设计**：
  ```
  :app → :ui-tv, :data, :player-core, :scraper
  :ui-tv → :core-model, :core-common
  :data → :core-model, :core-common, :media-source
  :player-core → :core-model, :media-source
  :scanner → :core-model, :core-common, :media-source
  :metadata → :core-model
  :scraper → :core-model
  :sync-engine → :core-model, :metadata, :media-source
  :media-source → :core-model, :core-common
  :core-common → (无依赖)
  :core-model → (无依赖)
  ```

  **不能做**：
  - 不要添加源代码文件（那是 T5+ 的事）
  - 不要创建不存在的依赖关系
  - core-model 和 core-common 必须是纯 Kotlin 模块（不依赖 Android SDK）

  **推荐 Agent Profile**：
  - **Category**: `quick` — 目录结构和 build 文件创建
  - **Skills**: `[]`

  **并行化**：
  - **可并行**: NO — 依赖 T1（root build）, T2（catalog）, T3（convention plugin）
  - **并行组**: Wave 1
  - **阻塞**: T5-T7（需要模块存在）
  - **被阻塞**: T1, T2, T3

  **参考资料**：
  - Android 官方多模块项目结构指南
  - 参考 AsukaPlayer 的模块依赖图（GitHub: qianmokano/Asukaplayer）
  - Gradle 官方文档：声明模块间依赖

  **验收标准**：
  - [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL（所有 11 个模块编译通过，即使无源码）
  - [ ] `./gradlew :app:dependencies --configuration debugRuntimeClasspath` 输出依赖树，无循环依赖
  - [ ] core-model 和 core-common 的 build.gradle.kts 不含 Android 插件（纯 Kotlin/JVM）

  **QA 场景**：

  ```
  场景: 全模块编译通过
    工具: Bash
    前置条件: T1-T3 完成
    步骤:
      1. 执行: ./gradlew assembleDebug
      2. 检查 BUILD SUCCESSFUL
      3. 执行: ./gradlew projects 验证所有模块列出
    预期结果: 11 个模块全部编译通过
    失败指标: 任何模块编译失败
    证据: .sisyphus/evidence/task-4-assemble-success.txt

  场景: 无循环依赖
    工具: Bash
    前置条件: T4 完成
    步骤:
      1. 执行: ./gradlew :app:dependencies --configuration debugRuntimeClasspath
      2. 检查输出无循环引用模式
    预期结果: 依赖树是 DAG，无循环
    证据: .sisyphus/evidence/task-4-dependency-tree.txt
  ```

  **提交**: YES
  - 消息: `chore: create multi-module project structure with 11 modules`
  - 文件: 所有 11 个模块的 `build.gradle.kts`、`settings.gradle.kts`（更新）、模块目录结构
