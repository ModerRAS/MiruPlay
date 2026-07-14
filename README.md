# MiruPlay

> Android TV 动漫媒体管理器 — 本地刮削、云端同步、多源播放

[English](./README.en.md) | [日本語](./README.ja.md)

## 当前状态

- Android TV 版仍是主入口，使用 Jetpack Compose TV、Media3 和 Hilt。

## 特性

- **多源媒体管理** — 支持本地文件、WebDAV、SMB 等多种媒体源
- **元数据刮削** — 自动从 Bangumi 获取动漫信息、海报、剧集列表
- **RSS 云盘同步** — 基于 RSS 订阅自动同步新剧集到本地
- **远程控制** — 内置 HTTP 服务器 + gRPC 接口，支持浏览器 / 第三方客户端操控
- **TV 遥控器优化** — Compose + Leanback 的 TV 界面，完全适配遥控器交互
- **播放进度追踪** — 记录播放进度，支持断点续播

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.0.0 |
| UI | Jetpack Compose TV |
| DI | Hilt (Android) |
| 数据库 | Room |
| 播放器 | Media3 (ExoPlayer) |
| 网络 | OkHttp + NanoHTTPD + gRPC + Protobuf |
| 序列化 | Kotlinx Serialization |

## 构建

**环境要求：**
- JDK 21 (Temurin)
- Android SDK 35

```bash
# Debug 构建
./gradlew assembleDebug

# Debug 构建并写入构建号（版本名会显示为 0.1.<BUILD_NUMBER>）
./gradlew assembleDebug -PBUILD_NUMBER=123

# Release 构建（需签名）
./gradlew assembleRelease -PVERSION_NAME=1.0.0 -PVERSION_CODE=100

# 运行测试
./gradlew test

# 代码检查
./gradlew lint

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
├── ui-design/       # Android TV 共用视觉和输入约定
├── player-core/     # Media3 播放器集成
├── media-source-api/# 媒体源接口
├── media-source/    # Android 媒体源实现
├── repository-api/  # repository / 展示 helper
├── scanner/         # 本地文件扫描
├── scraper/         # 动漫元数据刮削（Bangumi）
├── scraper-core/    # 共享刮削接口和 Bangumi 映射逻辑
├── sync-engine/     # RSS 同步引擎
├── sync-engine-shared/ # 共享 Cloud/RSS 动作与目录浏览逻辑
├── cloud-drive/     # 云盘集成
├── cloud-drive-api/ # CloudDrive 共享契约
├── metadata/        # NFO 元数据读写
├── metadata-core/   # 共享 NFO 解析/写入逻辑
├── web-control/     # HTTP 控制服务器
├── web-control-core/# 共享 WebUI HTTP 路由和 DTO
└── gradle/          # 依赖版本目录
```

## 文档

- [BERT 文件名解析与扫描接入](./docs/anime-filename-parser.md) — 训练产物、Android ONNX 运行时、扫描流程、文件夹/文件名职责、ADB 验证步骤

## 许可证

[GNU General Public License v3.0](./LICENSE)
