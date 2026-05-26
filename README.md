# MiruPlay

> Android TV + Windows 桌面动漫媒体管理器 — 本地刮削、云端同步、多源播放

[English](./README.en.md) | [日本語](./README.ja.md)

## 当前状态

- Android TV 版仍是主入口，使用 Jetpack Compose TV、Media3 和 Hilt。
- Windows 桌面版已接入 Compose Desktop、Local/WebDAV/SMB 媒体源、Bangumi 元数据、CloudDrive2/RSS 配置、WebUI、mpv 播放和可选 RIFE 运行时。
- Windows 端仍以 [Windows port roadmap](./docs/windows-port-roadmap.md) 为准收尾：真实 CloudDrive2/RSS live QA、目标硬件 RIFE 矩阵、签名安装包和更广的设备端到端证据需要在对应环境补齐。

## 特性

- **多源媒体管理** — 支持本地文件、WebDAV、SMB 等多种媒体源
- **元数据刮削** — 自动从 Bangumi 获取动漫信息、海报、剧集列表
- **RSS 云盘同步** — 基于 RSS 订阅自动同步新剧集到本地
- **远程控制** — 内置 HTTP 服务器 + gRPC 接口，支持浏览器 / 第三方客户端操控
- **TV 遥控器优化** — Compose + Leanback 的 TV 界面，完全适配遥控器交互
- **Windows 桌面端** — Compose Desktop 界面、mpv 播放、可选 RIFE 后端和桌面 JSON 存储
- **播放进度追踪** — 记录播放进度，支持断点续播

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.0.0 |
| UI | Jetpack Compose TV + Compose Desktop |
| DI | Hilt (Android) |
| 数据库 | Room (Android) + JSON store (Windows desktop) |
| 播放器 | Media3 (ExoPlayer, Android) + mpv (Windows) |
| 网络 | OkHttp + NanoHTTPD + gRPC + Protobuf |
| 序列化 | Kotlinx Serialization |

## 构建

**环境要求：**
- JDK 21 (Temurin)
- Android SDK 35

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建（需签名）
./gradlew assembleRelease -PVERSION_NAME=1.0.0 -PVERSION_CODE=100

# 运行测试
./gradlew test

# 代码检查
./gradlew lint

# Windows 桌面轻量安装包（不复制本地 mpv 运行时）
./gradlew :desktop-app:installDist -PbundleMpvRuntime=false

# Windows port 安全本地门禁
powershell -ExecutionPolicy Bypass -File tools/verify-windows-port.ps1
```

## 项目结构

```
MiruPlay/
├── app/              # 应用入口、导航、Hilt 注入
├── core/
│   ├── model/       # 领域模型、数据类
│   └── common/      # 公共工具、Result 类型
├── data/            # Room 数据库、DAO、Repository
├── ui-tv/           # TV Compose UI（页面、组件、主题）
├── ui-design/       # Android TV / Windows 共用视觉和输入约定
├── desktop-app/     # Windows Compose Desktop 入口、设置、WebUI 桥接
├── player-core/     # Media3 播放器集成
├── player-mpv/      # Windows mpv 命令、IPC、运行时校验
├── media-source-api/# 跨平台媒体源接口
├── media-source/    # Android 媒体源实现
├── media-source-desktop/ # Windows 本地 / WebDAV / SMB 媒体源
├── repository-api/  # 跨平台 repository / 展示 helper
├── repository-desktop/ # Windows JSON-backed repository
├── scanner/         # 本地文件扫描
├── scanner-desktop/ # Windows 扫描器
├── scraper/         # 动漫元数据刮削（Bangumi）
├── scraper-core/    # 共享刮削接口和 Bangumi 映射逻辑
├── scraper-desktop/ # Windows Bangumi 客户端
├── sync-engine/     # RSS 同步引擎
├── sync-engine-shared/ # 共享 Cloud/RSS 动作与目录浏览逻辑
├── sync-engine-desktop/ # Windows CloudDrive2/RSS runner 和 scheduler
├── cloud-drive/     # 云盘集成
├── cloud-drive-api/ # CloudDrive 共享契约
├── cloud-drive-desktop/ # Windows CloudDrive2 gRPC 客户端
├── metadata/        # NFO 元数据读写
├── metadata-core/   # 共享 NFO 解析/写入逻辑
├── web-control/     # HTTP 控制服务器
├── web-control-core/# 共享 WebUI HTTP 路由和 DTO
├── runtime/mpv/     # 本地 mpv/RIFE payload 占位；大文件不进 Git
└── gradle/          # 依赖版本目录
```

## 文档

- [Windows port roadmap](./docs/windows-port-roadmap.md) — Windows 版完成度、验收证据和剩余 live/target-host QA
- [Windows port plan](./docs/windows-port-plan.md) — 架构拆分、桌面入口、mpv/RIFE 打包策略
- [Windows port audit](./docs/windows-port-audit.md) — 已覆盖证据与风险审计
- [mpv runtime packaging](./docs/mpv-runtime-packaging.md) — Windows mpv/RIFE runtime 准备和发行门禁
- [BERT 文件名解析与扫描接入](./docs/anime-filename-parser.md) — 训练产物、Android ONNX 运行时、扫描流程、文件夹/文件名职责、ADB 验证步骤

## 许可证

[GNU General Public License v3.0](./LICENSE)
