# MiruPlay Windows Port Plan

目标：把 MiruPlay 从 Android TV 应用扩展成 Windows 桌面版，复用媒体管理、刮削、进度、同步等核心能力，并在 Windows 上用 mpv 作为播放底层，默认可携带 RIFE 插帧运行时。

完成度审计见 `docs/windows-port-audit.md`；该审计明确列出已覆盖证据和仍缺失的目标硬件 RIFE 验证、桌面同步 live QA 等事项。

## 关键判断

- 先保留 Android app，不做大拆大改。Windows 版新增独立入口，逐步复用纯 Kotlin 模块。
- 播放器先走 `mpv.exe` 子进程 + JSON IPC。mpv 官方文档说嵌入式播放器最终更推荐 libmpv，但子进程模式可以直接复用 mpv-lazy 的 `portable_config`、脚本、VapourSynth 生态，迁移风险更低。
- RIFE 不写进 MiruPlay 自己的渲染链，而是作为 mpv VapourSynth video filter 挂载。`mpv_PlayKit` 已经维护了 `portable_config/vs/MEMC_RIFE_NV.vpy`、`MEMC_RIFE_DML.vpy`、`MEMC_RIFE_STD.vpy` 这类脚本，可以作为 Windows 打包模板。
- UI 约束：Android TV 继续使用 Compose TV/AndroidX Compose；Windows 端也必须使用 Compose Multiplatform Desktop，不再把 Swing 当作长期 UI 方向。Windows 版视觉应和 TV 版保持同一套语言：深蓝背景、红色焦点/主操作、8dp 卡片、左侧导航、海报/详情式内容区和 10-foot 大字号。复用 ViewModel 之前先把状态和 use case 下沉到纯 Kotlin 层。

## 当前已落地的第一步

- `:core:common` 和 `:core:model` 已改成真正的 JVM/Kotlin 模块，Windows 端可以直接依赖番剧、剧集、播放源、播放状态、进度模型；番剧标题/字幕、播放源标题、文件大小、播放时间和外挂字幕 track 解析也已下沉到 `:core:model`，避免 TV/桌面 UI 各写一套格式化逻辑。
- CI 的 debug build job 现在除了 Android `assembleDebug`，还会运行 `checkDesktopComposeOnly`、`checkUiPaletteDrift`、`:core:model:test`、`:repository-api:test`、`:player-mpv:test`、`:cloud-drive-desktop:test`、`:sync-engine-desktop:test`、`:desktop-app:test` 和轻量 `:desktop-app:installDist -PbundleMpvRuntime=false`，避免 Windows/JVM port 只在本地验证。
- 新增 `:player-mpv` JVM 模块，负责构造和启动 Windows mpv 命令：
  - `--config-dir=<portable_config>` 隔离 MiruPlay 自带配置。
  - `--input-ipc-server=<pipe>` 预留 JSON IPC 控制通道。
  - `MpvIpcClient` 通过 mpv JSON IPC 发送 pause、seek、quit 等基础播放控制命令。
  - `--vf-append=vapoursynth=...` 挂载 RIFE/VapourSynth 脚本。
  - 外挂字幕用 `--sub-file=<path>` 传给 mpv。
  - 续播位置用 `--start=<seconds>` 传给 mpv。
  - `MpvRuntimeDiscovery` 会优先发现发行包旁边的 `runtime/mpv`，也支持 `miruplay.mpv.runtime` 系统属性和 `MIRUPLAY_MPV_RUNTIME` 环境变量覆盖。
  - `MpvRuntimeVerifier` 会检查 `mpv.exe`、`portable_config/`、`portable_config/vs/` 和 RIFE 脚本；如果勾选了缺失的 RIFE 后端，启动前直接阻止并提示具体脚本路径。
  - `:desktop-app:smokeMpvRuntime` 可对真实 payload 执行 `mpv.exe --version`，用于发行前确认运行时不只是文件结构完整。
  - 已用 mpv_PlayKit `20260510` 标准 `mpv-lazy-20260510.exe` 作为 base，并叠加 `mpv-lazy-20260510-vsNV.7z.001` overlay 准备 `runtime/mpv`；`mpv.exe --version` smoke 输出版本为 `mpv v0.41.0-615-g7b057f66f`，`tools/smoke-mpv-rife.ps1 -Backend DIRECTML` 的两帧 Y4M 烟测已成功退出。
- 新增 `:desktop-app` JVM 桌面入口，默认主入口已切到 Compose Multiplatform Desktop：
  - `MiruPlayDesktopComposeApp.kt` 提供 TV 风格的 Windows Compose 桌面播放面板，复刻 TV 版的深蓝/红色视觉 token、左侧导航、8dp 面板、海报式播放卡和大字号排版，并把左侧导航改成可点击的 Library / Details / Player / Settings 分区。深蓝/红色/蓝色/文本/卡片色已下沉到 `:ui-design` 的 `MiruPlayPalette`，Android TV `Theme.kt` 和桌面 Compose 入口都从同一组常量派生，减少视觉漂移；根 Gradle 任务 `checkUiPaletteDrift` 会扫描 `ui-tv/src` 和 `desktop-app/src`，阻止共享 palette literal 被重新硬编码。默认窗口已调整为 1280x820、最小 1100x720，截图 QA 覆盖了 Library / Details / Player / Settings 首屏，并修正了默认 800x600 窗口下的操作按钮挤压、Bangumi batch 按钮溢出和 Player RIFE 后端选择器截断。当前覆盖本地媒体库源添加、已保存源切换、扫描索引、索引搜索、当前源索引清空/源删除、WebDAV/SMB 源打开与目录浏览、远程源扫描、单条 Bangumi 搜索/匹配应用/外部 metadata 清除、批量 Bangumi 候选切换、继续观看列表、条目选择回填播放路径、mpv runtime 检查、RIFE 后端选择、媒体 URI/字幕/续播输入、mpv 命令预览、Launch/Stop，以及 CloudDrive2 登录/API token 验证。
  - Gradle `mainClass` 已改为 `com.miruplay.tv.desktop.MiruPlayDesktopComposeAppKt`，媒体详情面板已由 `DesktopMediaDetailRows` 提供更接近旧 presenter 的字段覆盖；`tools/capture-desktop-ui.ps1` 已从截图产物检查扩展到窗口尺寸、深色主题、红色强调色、浅色文字、视觉多样性和四个分区互异的图像断言。
  - 旧 `MiruPlayDesktopApp.kt` Swing 壳已从生产源码移除；Windows 入口只保留 Compose Desktop 实现，并由根 Gradle 任务 `checkDesktopComposeOnly` 阻止 Swing UI 依赖重新进入桌面模块。
  - 选择 `mpv.exe`、`portable_config`、媒体库根目录、媒体文件和外挂字幕。
  - 默认路径指向随发行包携带的 `runtime/mpv/mpv.exe` 与 `runtime/mpv/portable_config`，并提供 `Check runtime` 状态检查。
  - `Check runtime` 会读取 `runtime-manifest.json`（若存在），在弹窗里展示运行时来源、准备时间和必需 RIFE 后端，便于发行包审计。
  - Compose 启动时会恢复已保存的 Local/WebDAV/SMB 配置，并提供 `Saved sources` 下拉切换。
  - 重新添加相同路径/URL 的媒体源会保留原 id 并更新名称、凭证和连接状态，便于修正 WebDAV/SMB 配置。
  - 浏览本地媒体库目录，双击目录进入，双击视频填入播放路径。
  - 可输入 WebDAV URL、用户名和密码，在 Compose `Remote sources` 面板打开并浏览远程目录。
  - 可输入 SMB URL、域、用户名和密码，在 Compose `Remote sources` 面板打开并浏览局域网共享目录。
  - 可扫描、搜索、清空当前活动源索引，也可以删除当前媒体源；删除源时会同步清理 Compose 状态和仓库中的对应索引。
  - 索引搜索结果会同步回填媒体列表，可直接双击搜索命中的视频播放。
  - 选中文件或搜索结果时会显示媒体详情；搜索结果可展示扫描/NFO 得到的番名、季、集数、单集标题、简介、大小、修改时间、路径、Bangumi source/id/title 和最近播放续播点/播放次数/最后观看时间。
  - 远程媒体播放会先走桌面本机 loopback HTTP bridge，把 WebDAV/SMB 数据流交给 mpv，避免把 SMB 凭证塞进 mpv 命令行；bridge 已支持基础 HTTP Range/seek。
  - 远程播放的最近记录保存原始媒体路径，不保存临时 `127.0.0.1` bridge URL。
  - Compose 桌面播放会话会在启动后估算播放进度，并在播放期间每 10 秒通过 mpv IPC `get_property time-pos` 同步真实位置；停止 mpv 时会再次优先保存真实位置，IPC 不可用时回退到估算值，并刷新继续观看列表。可选择最近记录回填媒体路径和续播秒数，也可清除单条最近记录。Pause/Resume、-10s、+30s 控制会同步修正估算续播点。
  - 开关 RIFE，并选择 NVIDIA / DirectML / Standard 脚本后端。
  - 预览最终 mpv 命令，并通过 `:player-mpv` 启动/停止 mpv。
  - mpv 启动时使用每个窗口独立的随机 pipe 名称，桌面壳提供 Pause/Resume、-10s、+30s 控制按钮。
- 新增 `:media-source-desktop` JVM 模块：
  - 当前实现 Windows/桌面本地文件源的目录列表、文件流打开、元数据读取和连接检测。
  - 当前实现 WebDAV 桌面源，支持 PROPFIND 列表、GET 读取、Basic auth 和路径分段编码。
  - 当前实现 SMB 桌面源，支持 `smb://` 和 Windows UNC 路径规范化、NTLM 凭证、目录列表、文件流打开和元数据读取。
  - Android `:media-source` 保持不动，避免 SAF/Hilt 依赖污染桌面端。
- 新增 `:repository-api` 和 `:repository-desktop`：
  - `repository-api` 放跨平台媒体源、播放进度、媒体索引接口。
  - `repository-api` 还承载可复用的媒体索引展示 helper 和 metadata batch planner；`desktop-app` 的旧 presenter 名字只做薄转发，Android TV 或后续 KMP UI 可以复用同一套 query derivation、ready/review/conflict 规划和索引展示逻辑。
  - `repository-desktop` 当前用 JSON 文件持久化媒体源、进度、索引、Cloud/RSS 自动化配置、RSS 订阅、已处理 RSS 条目、下载任务，以及 file-backed CloudDrive/Bangumi 凭证，默认路径为用户目录下 `.miruplay/desktop-store.json`。
  - `desktop-app` 启动时恢复本地媒体源，并在启动 mpv 时写入最近播放记录。
  - 索引条目已支持可选的外部元数据来源、ID 和标题，桌面端可单条 upsert，避免重建整个索引。
- 新增 `:scanner-desktop` JVM 模块：
  - 递归扫描桌面本地媒体源，过滤视频文件并生成 `MediaIndexEntry`。
  - 从文件名推断番名、季度和集数，结果写入 `repository-desktop` 的索引。
  - 扫描视频时会读取同目录同名 `.nfo` 的 `episodedetails` 元数据，并优先采用 `showtitle`、`title`、`plot`、`season`、`episode`；NFO 缺失或格式不匹配时回退到文件名推断。
  - 扫描目录时会读取 `tvshow.nfo` 的 `title` / `originaltitle`，作为该目录下视频的番名兜底。
  - `desktop-app` 已加入 `Scan library` 和 `Search index` 操作。
- 新增 `:scraper-desktop` JVM 模块：
  - 提供不依赖 Android/Hilt/SecurePreferences 的 Bangumi 搜索、番剧详情和剧集列表客户端。
  - Compose `desktop-app` 已加入 `Use selected`、`Search`、`Apply match` 和 `Clear metadata` 操作，可用查询框、选中索引番名或文件名搜索在线元数据，并把 Bangumi source/id/title 写回单个索引条目；同时提供 `Batch preview`、`Apply batch`、`Accept review` 和 `Undo batch`，批量预览会拆分 ready/review/conflict，显示可选 review 队列，保留每个查询的多个 Bangumi 候选，允许应用或人工接受前切换候选，只自动应用无冲突的高置信度匹配，低置信度匹配可人工接受，并把最近一次批量应用前的索引条目写入桌面 JSON store，重启后仍可撤销。
- 新增 `:cloud-drive-desktop` JVM 模块：
  - 复用 `cloud-drive/src/main/proto/clouddrive.proto` 生成 gRPC lite 客户端代码，提供不依赖 Android/Hilt 的 `GrpcCloudDriveClient`。
  - 支持登录、API token info、离线下载提交、上传 torrent 文件、目录列表、建目录和移动文件，供 Windows RSS 自动化复用 CloudDrive2 能力。
  - `:cloud-drive-desktop:test` 已增加本地 loopback gRPC server 集成测试，覆盖生成 stub 的登录、API token info、Bearer 授权目录列表，以及 Bearer 被拒后的 raw token 兼容回退。
  - `:cloud-drive-desktop:smokeCloudDrive2 -PcloudDriveEndpoint=... -PcloudDriveToken=... -PcloudDrivePath=/Downloads` 可对真实 CloudDrive2 服务执行 token info 与目录 listing 烟测，输出 token friendly name、权限摘要和列表结果，但不会打印 token。
- 新增 `:sync-engine-desktop` JVM 模块：
  - 提供不依赖 Android/Hilt 的 RSS feed fetcher、torrent 下载与 magnet 转换、CloudDrive path policy、桌面 CloudDrive organizer 和 `DesktopCloudDriveRssAutomationEngine`。
  - `desktop-app` 的 Cloud/RSS 面板已接入 CloudDrive2 `Login`、`Verify token`、`Run sync now`、`Start scheduler` 和 `Stop scheduler`，会读取桌面 JSON 仓库中的配置/订阅/凭证，执行 RSS 过滤、去重、提交离线下载、记录 processed item/download task、整理 inbox 到 library，并更新 `lastRunAt`。
  - 同步成功后，桌面端还会按配置的关联源执行 post-sync 索引重扫，方便 CloudDrive2/WebDAV 的内容变化回流到媒体库索引。
  - 已增加桌面 RSS runner/scheduler 单元测试，覆盖新条目提交、filter skip、task 持久化、`lastRunAt` 更新、根目录 inbox 拒绝，以及 scheduler 触发 due sync、暴露运行状态和 `lastRunCompletedAt`。另有 loopback CloudDrive2 gRPC 集成测试，用真实 `GrpcCloudDriveClient` 验证 RSS 离线提交、Bearer metadata、processed item/download task 持久化和 organizer list 调用。
  - `:sync-engine-desktop:smokeCloudDriveRssDryRun -PcloudDriveEndpoint=... -PcloudDriveToken=... -PcloudDriveRssUrl=... -PcloudDriveInbox=/Downloads -PcloudDriveLibrary=/Library -PcloudDriveRssReportPath=build/cloud-rss-smoke/report.json` 可先在真实服务上验证 token、inbox/library listing、RSS fetch/parse、过滤规则和 would-submit 统计，并输出不含 token 的 JSON 证据报告；不会调用离线下载提交 API。

## 推荐模块演进

1. `core:*`
   继续保持纯 Kotlin/JVM。这里放跨平台模型、错误类型、路径工具、播放状态和解析逻辑。

2. `media-source`
   拆成 `media-source-core` + 平台实现：
   - `media-source-core`：接口、WebDAV、SMB、路径/URL 规范化。
   - `media-source-android`：`content://`、SAF、Android 权限。
   - `media-source-desktop`：Windows 本地文件、UNC 路径、盘符扫描。

3. `data`
   现有 Room/Android Security 不能直接搬到 Windows。已新增 `repository-api`，Android `data` 模块后续应逐步实现/绑定这些跨平台接口。Windows 当前先用 JSON 文件存储，后续可替换为桌面数据库。可选方向：
   - SQLDelight：跨 JVM/Android 稳，适合长期 KMP。
   - Room KMP：后续可评估，但当前项目 Room 仍是 Android 形态。

4. `player-core` / `player-mpv`
   - Android 继续 `player-core` + Media3。
   - Windows 使用 `player-mpv`。
   - 两边共享一个更薄的 `PlaybackController` 接口，接口里不要暴露 Android `Player`。

5. `desktop-app`
   已新增 Windows Compose Desktop 主入口，并迁入了本地源添加、已保存源切换、扫描、索引搜索、当前源索引清空/删除、WebDAV/SMB 打开浏览、远程源扫描、媒体详情面板、单条 Bangumi metadata 应用/清除、批量 Bangumi 预览/应用/候选切换/人工接受/撤销、继续观看列表，以及 CloudDrive2/RSS 配置、凭证、订阅管理、手动 `Run sync now` 和 Start/Stop scheduler。Compose 左侧导航已拆成 Library / Details / Player / Settings 分区，后续应补齐 live CloudDrive2 验证：
   - 首页/库/详情/设置应继续复用 TV 版视觉语言和信息层级；桌面交互可以适配鼠标键盘，但界面观感要和 TV 版一致。当前 `tools/capture-desktop-ui.ps1` 已自动捕获 Library / Details / Player / Settings 首屏截图，并校验窗口尺寸、深色主题、红色强调色、浅色文字、视觉多样性和分区截图互异。
   - 播放时可以先外置 mpv 窗口，稳定后再研究 libmpv 嵌入。
   - Bangumi 搜索结果目前可人工应用到单个索引条目，也可清除单条外部 metadata；批量预览并应用高置信度匹配后，最近一次批量应用可跨会话撤销；已有 metadata 冲突的条目会被跳过；低置信度匹配可在队列中选中后人工接受；batch review 可从多个 Bangumi 候选中切换后再应用。

## mpv + RIFE 打包形态

建议发行包结构：

```text
MiruPlay-Windows/
  MiruPlay.exe
  runtime/
    mpv/
      mpv.exe
      portable_config/
        mpv.conf
        input.conf
        scripts/
        script-opts/
        vs/
          MEMC_RIFE_NV.vpy
          MEMC_RIFE_DML.vpy
          MEMC_RIFE_STD.vpy
      Lib/
      vs-plugins/
        models/
```

默认策略：

- NVIDIA 用户优先 `MEMC_RIFE_NV.vpy`。
- 其他现代 GPU 可试 `MEMC_RIFE_DML.vpy`。
- `MEMC_RIFE_STD.vpy` 作为显式可选后端保留；当前 `20260510` 标准 base + `vsNV` overlay 缺少它运行所需的 `rife` VapourSynth 插件，所以默认 release gate 不再要求 Standard。
- `:desktop-app` 的 Gradle distribution 默认从仓库根目录 `runtime/mpv/` 复制到发行包中；如果传入 `-PmpvRuntimeSource=...`，则只使用该来源准备发行包运行时，避免同时复制仓库 runtime 和显式 source。UI-only 开发循环可用 `-PbundleMpvRuntime=false` 跳过大体积运行时复制。`runtime/mpv/` 在 Git 中只保留 `README.md`，实际 mpv_PlayKit、VapourSynth、模型文件由本地打包缓存、安装器或 Release asset 提供。
- 已核对 mpv_PlayKit `20260510` / `2026FM` Release：`noVS` 小包不适合作为内置 RIFE 发行源。当前可复现路径是以标准 `mpv-lazy-20260510.exe` 为 base，再用 `mpv-lazy-20260510-vsNV.7z.001` 作为 `-OverlaySource` 合并 VapourSynth/RIFE 文件；split archive 的 `.002` 必须和 `.001` 放在同一目录。
- `tools/prepare-mpv-runtime.ps1` 可从已解压目录、`.exe` 自解压包或 `.7z/.7z.001` 运行时包准备 `runtime/mpv`，并支持 `-OverlaySource` 叠加 RIFE/VapourSynth payload；可选校验下载资产 SHA256（含 split archive 多文件 digest 列表），校验必需 RIFE 脚本并写入 `runtime-manifest.json`，减少手工复制出错。
  - `tools/smoke-mpv-rife.ps1` 会生成一个两帧 Y4M 测试片段并用指定后端运行 mpv VapourSynth filter；当前本机通过 `-Backend DIRECTML`，`-Backend ALL -AllowFailures` 会打印 NVIDIA / DIRECTML / STANDARD 的矩阵摘要。加上 `-ReportPath` 后会输出 JSON 证据包，包含 mpv 版本、Windows/CPU/GPU 诊断、后端状态、exit code 和日志路径，便于在目标 NVIDIA/Standard 主机上补齐审计证据。NVIDIA/Standard 仍取决于目标机器驱动和插件栈。
- 首次启动只检测运行时是否存在，不把大体积二进制和模型提交进 Git。

本地打包流程：

1. 准备一个 mpv_PlayKit base payload，目录或解包结果顶层应直接包含 `mpv.exe` 和 `portable_config/`；当前验证过的 base 是 `mpv-lazy-20260510.exe`。
2. 准备 RIFE overlay payload，至少包含目标后端脚本，例如 `portable_config/vs/MEMC_RIFE_DML.vpy`；当前验证过的 overlay 是 `mpv-lazy-20260510-vsNV.7z.001`，并要求 `.002` 同目录存在。
3. 推荐：运行 `.\tools\prepare-mpv-runtime.ps1 -Source D:\path\to\mpv-lazy-20260510.exe -OverlaySource D:\path\to\mpv-lazy-20260510-vsNV.7z.001 -Destination .\runtime\mpv -RequiredRifeBackends 'NVIDIA,DIRECTML' -Force`，让脚本解包、合并、校验并生成 `runtime-manifest.json`。
4. 运行 `.\gradlew.bat :desktop-app:installDist -PmpvRuntimeSource=runtime\mpv`，该参数支持绝对路径或相对仓库根目录的路径，并会把来源复制到发行包的 `runtime/mpv/`。仅做 UI QA 时可运行 `.\gradlew.bat :desktop-app:installDist -PbundleMpvRuntime=false` 获得轻量安装包。
5. 从 `desktop-app/build/install/desktop-app/bin/desktop-app.bat` 启动，点击 `Check runtime` 验证发行包内运行时。
6. 运行 `.\tools\smoke-mpv-rife.ps1 -RuntimeRoot .\runtime\mpv -Backend DIRECTML -ReportPath .\build\mpv-smoke\rife-directml-report.json` 做真实 RIFE filter 烟测并保留 JSON 证据；在合适的 NVIDIA 驱动机器上再跑 `-Backend NVIDIA -ReportPath ...`。

开发机注意：当前 Android Gradle Plugin/Gradle 组合应使用 JDK 21；如果系统 `java` 已切到 JDK 25，可在命令前临时设置：

```powershell
$env:JAVA_HOME='C:\Users\adqew\scoop\apps\temurin21-jdk\current'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## 风险和注意

- RIFE/VapourSynth 对 GPU、驱动、模型文件、Python/VapourSynth 插件版本都敏感。当前 DirectML 路径已在本机烟测通过；NVIDIA 路径能进入 `vsmlrt`/TensorRT 但本机 CUDA driver 版本不足；Standard 脚本存在但需要额外 `rife` 插件。Windows 设置页必须有“关闭插帧”和“切换后端”。
- mpv JSON IPC 是本地控制接口，不带认证，pipe 名称要随机化或限定在本机用户上下文。
- `mpv_PlayKit` 是配置和懒人包参考，不等于可直接整体并入。打包前要核对 mpv、VapourSynth、脚本、模型各自许可证。
- WebDAV/SMB 播放在 Windows 上尽量让 mpv 直接播放 URL 或 UNC 路径；需要认证时优先由 MiruPlay 生成可播放 URL，避免把密码写进日志。
- 当前桌面 bridge 已支持基础 HTTP Range，并通过 `DesktopMediaSource.openStream(path, range)` 下推 range。WebDAV 已发送远端 HTTP `Range`；本地文件使用 seekable channel；SMB 使用 `SmbRandomAccessFile.seek()` 随机读。

## 参考

- mpv 官方手册：`--config-dir`、`--input-ipc-server`、JSON IPC、VapourSynth filter。
- hooke007/mpv_PlayKit：Windows mpv-lazy、`portable_config`、RIFE/VapourSynth 脚本参考。
