# MiruPlay

> Android TV 动漫媒体管理器 — 本地刮削、云端同步、多源播放

[English](./README.en.md) | [日本語](./README.ja.md)

## 特性

- **多源媒体管理** — 支持本地文件、WebDAV、SMB 等多种媒体源
- **元数据刮削** — 自动从 Bangumi / AniList 获取动漫信息、海报、剧集列表
- **RSS 云盘同步** — 基于 RSS 订阅自动同步新剧集到本地
- **远程控制** — 内置 HTTP 服务器 + gRPC 接口，支持浏览器 / 第三方客户端操控
- **TV 遥控器优化** — Compose + Leanback 的 TV 界面，完全适配遥控器交互
- **播放进度追踪** — 记录播放进度，支持断点续播

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.0.0 |
| UI | Jetpack Compose + TV Material |
| DI | Hilt |
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
├── player-core/     # Media3 播放器集成
├── media-source/    # 媒体源抽象（本地 / WebDAV / SMB）
├── scanner/         # 本地文件扫描
├── scraper/         # 动漫元数据刮削（Bangumi / AniList）
├── sync-engine/     # RSS 同步引擎
├── cloud-drive/     # 云盘集成
├── metadata/        # NFO 元数据读写
├── web-control/     # HTTP 控制服务器
└── gradle/          # 依赖版本目录
```

## 许可证

[GNU General Public License v3.0](./LICENSE)
