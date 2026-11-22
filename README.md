# MiruPlay

> Android TV 动漫媒体管理器 — 本地刮削、云端同步、多源播放

[English](./README.en.md) | [日本語](./README.ja.md)

## 当前状态

Android TV 版是主入口，基于 Jetpack Compose TV、Media3 与 Hilt，同时支持视频（动漫）与音乐两种模式。CI 持续发布稳定版本（当前版本线 2.19.x）。

## 特性

- **多源媒体管理** — 支持本地文件、WebDAV、SMB，以及 AnimeOrganizer 的 MLIP library.db 源
- **元数据刮削** — 自动从 Bangumi 获取动漫信息、海报、剧集列表与剧集评论，海报本地缓存
- **新番时间表** — 媒体库顶栏入口 + 设置分组；接入 AirRadar 时间表 API，按设备时区显示每周播出时间，支持本地缓存与媒体库匹配
- **智能文件名解析** — 内置 AniFileBERT 模型（Android ONNX 运行时）解析文件名，供扫描、刮削与云盘自动整理使用
- **RSS 云盘同步** — 基于 RSS 订阅自动同步新剧集，支持 CloudDrive2 离线下载与自动整理
- **音乐模式** — 专辑/曲目库、播放队列、CUE 分轨与整轨播放，SRC 三档采样率绕过
- **多后端播放器** — Media3 (ExoPlayer) 为主，可选内嵌 mpv / ijkplayer 后端；支持 ASS 字幕（libass 渲染）、外挂字幕与外挂音轨、偏好字幕语言、断点续播
- **音频 DSP** — REW 均衡器（per-channel PEQ）、NEON FIR 32/64-bit 滤波、FFT 分析
- **远程控制** — 内置 HTTP 服务器 + WebUI + gRPC 接口，支持浏览器 / 第三方客户端操控，与 TV 端设置保持同步
- **TV 遥控器优化** — Compose + Leanback 的 TV 界面，完全适配遥控器交互

## 新番时间表（AirRadar）

媒体库顶栏的「时间表」按钮进入「新番时间表」页；数据源地址在「设置 → 新番时间表」分组里配置（保存地址 / 打开时间表 / 清除地址，只保存在本机）。未配置时页面会引导去设置。

- **入口**：媒体库顶栏「时间表」；设置 → 新番时间表（摘要显示「已配置」/「未配置」）。
- **五态**：未配置（引导去设置）→ 加载中（正在获取时间表…）→ 空（服务端 404 或无条目）→ 错误（网络 / 超时 / HTTP 状态 / 数据格式，可重试）→ 内容（按设备本地时区周几分组的整周列表）。刷新失败但存在旧缓存时显示「缓存」徽标，并按真实失败原因给出提示（不会把数据格式问题误报成网络问题）。
- **数据与缓存**：只请求 `GET {baseUrl}/api/schedule/latest`，超时 10 秒；成功结果做内存 + 磁盘（应用 cacheDir）两级缓存，TTL 1 小时；网络失败时回退旧缓存。
- **展示**：以「JST 周几 + HH:mm」为权威换算到设备本地时区，按周一分组、本地时间升序；跨零点的条目标注「次日/前日」；`time: null` / `timeKnown: false` 的条目按周几展示并标注「时间未知」（排在已知时间之后），`disputed: true` 标注「时间有争议」；按 Bangumi subject id 匹配媒体库（未匹配显示「未在媒体库」），匹配到的可打开详情。
- **WebAPI**：`GET /api/settings/schedule` 返回 `{ "dataSourceUrl": "...", "configured": true }`；`PUT /api/settings/schedule` 提交 `{ "dataSourceUrl": "..." }`，空值清除设置，非法地址返回错误且不覆盖旧值。
- **地址校验/归一化**：只接受以 `http://` 或 `https://` 开头的完整地址；必须有 host，禁止空白字符、query、fragment、userinfo，端口须在 1–65535；保存时去掉末尾 `/`，粘贴完整的 `.../api/schedule/latest` 会自动去掉接口后缀；仓库不内置任何默认地址。

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.0.0 |
| UI | Jetpack Compose TV |
| DI | Hilt (Android) |
| 数据库 | Room |
| 播放器 | Media3 (ExoPlayer) + 可选 mpv / ijkplayer 后端 |
| 字幕 | libass 渲染 ASS 字幕 |
| 网络 | OkHttp + NanoHTTPD + gRPC + Protobuf |
| 序列化 | Kotlinx Serialization |
| 机器学习 | AniFileBERT（ONNX Runtime）文件名解析 |
| 构建 | Gradle 8.10 + AGP 8.6.0，JDK 21，minSdk 28 / targetSdk 35 |

## 构建

**环境要求：**
- JDK 21 (Temurin)
- Android SDK 35

```bash
# Debug 构建
./gradlew assembleDebug

# Debug 构建并写入构建号（版本名显示为 2.19.<BUILD_NUMBER>）
./gradlew assembleDebug -PBUILD_NUMBER=123

# Release 构建（需签名）
./gradlew assembleRelease -PVERSION_NAME=2.19.0 -PVERSION_CODE=100

# 运行测试
./gradlew test

# 代码检查
./gradlew lint
```

## 项目结构

```
MiruPlay/
├── app/                   # 应用入口、导航、Hilt 注入
├── core/
│   ├── model/            # 领域模型、数据类
│   └── common/           # 公共工具、Result 类型
├── data/                 # Room 数据库、DAO、Repository
├── ui-tv/                # TV Compose UI（页面、组件、主题）
├── ui-design/            # Android TV 共用视觉和输入约定
├── player-core/          # Media3 播放器集成、音乐队列、音频 DSP 运行时
├── player-mpv-android/   # 内嵌 mpv 播放后端
├── player-ijkplayer-android/ # ijkplayer 播放后端
├── audio-dsp-core/       # 音频 DSP 逻辑（PEQ 等）
├── audio-dsp-native/     # NEON FIR / FFT 原生实现
├── media-source-api/     # 媒体源接口
├── media-source/         # Android 媒体源实现（本地、WebDAV、SMB、MLIP）
├── repository-api/       # repository / 展示 helper
├── scanner/              # 本地文件扫描（含音频目录识别、标签读取）
├── scraper/              # 动漫元数据刮削（Bangumi）
├── scraper-core/         # 共享刮削接口和 Bangumi 映射逻辑
├── sync-engine/          # RSS 同步引擎
├── sync-engine-shared/   # 共享 Cloud/RSS 动作与目录浏览逻辑
├── cloud-drive/          # 云盘集成
├── cloud-drive-api/      # CloudDrive 共享契约
├── cloud-drive-core/     # 共享 CloudDrive 逻辑
├── metadata/             # NFO 元数据读写
├── metadata-core/        # 共享 NFO 解析/写入逻辑
├── translation/          # 翻译辅助
├── background-task/      # 后台任务
├── web-control/          # HTTP 控制服务器 + WebUI 前端
├── web-control-core/     # 共享 WebUI HTTP 路由和 DTO
└── gradle/               # 依赖版本目录
```

## 文档

- [BERT 文件名解析与扫描接入](./docs/anime-filename-parser.md) — AniFileBERT 训练产物、Android ONNX 运行时、扫描流程、文件夹/文件名职责、ADB 验证步骤
- [AniFileBERT 维护手册](./docs/anifilebert-maintenance.md) — MiruPlay / AniFileBERT / AnimeName 三仓库关系，数据更新、重训、发布流程
- [CloudDrive2 RSS 离线下载与整理](./docs/cloud-drive-rss-offline-download.md) — RSS 拉取、离线下载、自动整理流程
- [Media Source Content Mode](./docs/media-source-content-mode.md) — 媒体源内容模式（视频/音乐/混合）说明
- [Metadata Search Aggregation Refactor](./docs/metadata-search-aggregation-refactor.md) — 元数据搜索聚合重构记录
- [Android TV 播放器交互约定](./docs/android-tv-player-controls.md) — 遥控器播放控制的用户批准契约
- [Android TV ADB 行为测试](./docs/android-tv-behavior-tests.md) — 基于 adb 的 TV 交互行为测试套件

另有 `docs/agents/`（代理工作流清单）、`docs/verification/`（真机验证记录）、`docs/workflows/`（发布验证流程）等工程文档。

## 许可证

[GNU General Public License v3.0](./LICENSE)
