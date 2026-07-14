# CloudDrive2 RSS 离线下载与整理

这份文档说明 MiruPlay 的 CloudDrive2 RSS 自动化流程：怎么拉 RSS、怎么把 torrent 交给 CloudDrive2/115 离线下载、下载后怎么整理到动漫库，以及自动整理和扫描时文件名解析模型在哪里生效。

## 一句话结论

- RSS 通过配置的 HTTP 代理拉取。
- 普通直链或 magnet 会直接提交给 CloudDrive2 `AddOfflineFiles`。
- `.torrent` 链接会先下载到本地缓存，再上传原始 torrent 到 CloudDrive2 隐藏暂存目录，同时从 torrent 内容解析出 magnet，并用 magnet 提交离线下载。
- 下载完成后的文件先落到下载目录，再由整理器识别番剧名、季、集，并移动到动漫库目录。
- 整理器和后续扫描都会接入 BERT/ONNX 文件名解析器；BERT 只解析视频文件名，文件夹名用于番剧目录和 Season 兜底。

## 示例测试配置

| 项目 | 值 |
|------|----|
| CloudDrive2 服务端 | `http://<clouddrive-host>:19798` |
| API Token | 通过本地参数传入，不写入文档 |
| HTTP Proxy | `http://<proxy-host>:7890` |
| RSS | `https://api.ani.rip/ani-torrent.xml` |
| 下载目录 | `/115open/下载/Ani` |
| 整理目录 | `/115open/影音/动漫` |
| ADB 设备 | `<android-tv-device-id>` |
| Web 控制端口 | `http://<android-tv-host>:9978` |

Token 验证需要这些 CloudDrive2 权限：

- `allowList`
- `allowCreateFolder`
- `allowCreateFile`
- `allowWrite`
- `allowMove`
- `allowAddOfflineDownload`

## 运行流程

```text
RSS subscription
  -> RssFeedFetcher
       -> HTTP proxy
  -> CloudDriveRssAutomationEngine
       -> filterRegex 过滤条目
       -> 生成 itemKey，跳过已处理条目
       -> 准备提交链接
       -> CloudDrive2 AddOfflineFiles
  -> CloudDriveLibraryOrganizer
       -> 扫描下载目录
       -> VideoDirectoryClassifier
       -> BERT/ONNX 文件名解析补全
       -> 移动视频到整理目录
  -> ScanCoordinator
       -> 扫描 WebDAV 媒体源
       -> VideoDirectoryClassifier
       -> BERT/ONNX 文件名解析补全
       -> Bangumi 元数据缓存
```

## torrent 处理

CloudDrive2/115 对直接提交上传后的 torrent 文件路径会返回类似 `错误的链接` 的错误。因此现在的实现分两步：

1. `TorrentFileDownloader` 把 RSS 里的 `.torrent` 下载到本地缓存目录。
2. `TorrentMagnetParser` 解析 bencode，取原始 `info` 字典计算 BTIH，生成 magnet 链接。
3. 原始 torrent 文件通过 CloudDrive2 `CreateFile` + `WriteToFile` 上传到下载目录下的隐藏暂存目录：

```text
/115open/下载/Ani/.miruplay-torrents
```

4. 实际离线下载提交的是 magnet，而不是远端 torrent 文件路径。
5. 本地缓存里的 torrent 文件会在提交准备结束后删除。

隐藏暂存目录会被整理器跳过，不会被当作番剧目录递归整理。

## 下载与整理目录

下载目录和整理目录必须都是非根目录，并且整理目录不能位于下载目录内部。

本次配置：

```text
下载目录: /115open/下载/Ani
整理目录: /115open/影音/动漫
```

整理器会递归下载目录，跳过隐藏目录和 `.trickplay` 目录，只处理视频扩展名文件。每个视频都会调用同一套分类器：

```kotlin
classifier.classifyVideo(file.path, file.name)
```

分类器会结合目录规则、文件名规则和 BERT/ONNX 结果，得到番剧名、季数和集数。分类结果会决定目标路径：

```text
/115open/影音/动漫/<番剧名>/Season <季数>/<文件名>
```

例如这次 RSS 测试条目最终进入：

```text
/115open/影音/动漫/百鬼夜行抄/Season 1/[ANi] 百鬼夜行抄 - 05 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4
```

## 整理与扫描识别

整理器和扫描器都会调用：

```kotlin
classifier.classifyVideo(file.path, file.name)
```

完整路径和文件名都会进入分类器，但职责不同：

- 文件夹名：识别番剧目录、`Season 1` / `S02` / `第2季` 这类 Season 目录，以及文件名信息太少时的标题兜底。
- 文件名：识别发布组、标题、季、集、清晰度、来源等；BERT/ONNX 的输入是去掉扩展名后的 `file.name`。

也就是说，BERT 模型扫的是文件名，不是文件夹名；文件夹名仍然由规则逻辑使用。

## 实机验证记录

在 `<android-tv-device-id>` 上安装 debug APK 后，用 Web 控制接口触发了一次 RSS 运行。

首次运行提交了新 torrent 条目：

```json
{"submitted":1,"skipped":60,"failed":0,"organized":0}
```

当 CloudDrive2/115 完成离线下载后，再次运行整理和扫描：

```json
{"submitted":0,"skipped":61,"failed":0,"organized":1}
```

扫描日志显示 WebDAV 源完成索引：

```text
Scan done: CloudDrive -> 2139 files, 2125 new episodes
```

库详情接口确认新下载文件已经识别为 `百鬼夜行抄` 第 5 集，并补上 Bangumi 单集标题：

```text
animeId=百鬼夜行抄
seasonNumber=1
episodeNumber=5
title=被遮起來之物 ~遮眼鬼~
fileName=[ANi] 百鬼夜行抄 - 05 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4
```

## 常用接口

触发一次 RSS 离线下载、整理和扫描：

```powershell
curl.exe -s -X POST http://<android-tv-host>:9978/api/cloud-drive/run
```

查询库中某个番：

```powershell
curl.exe -s -G http://<android-tv-host>:9978/api/library --data-urlencode "query=百鬼夜行抄"
```

查询番剧详情：

```powershell
$id=[uri]::EscapeDataString('百鬼夜行抄')
curl.exe -s "http://<android-tv-host>:9978/api/anime/$id"
```

## 注意事项

- 第一次提交离线任务时，文件可能还没下载完成，所以 `organized` 可能是 `0`；下载完成后再跑一次会整理。
- RSS 条目用 `guid` 或 `title|url` 的 SHA-1 作为去重键，已提交条目会跳过。
- CloudDrive2 目录浏览接口可能受远端缓存影响，刚移动后的目录列表看起来可能滞后；库索引接口更适合确认扫描结果。
- `runOnce` 会处理所有启用的订阅，然后整理整个下载目录并扫描配置的 WebDAV 源，目录很大时会耗时较久。
- 当前实现会保存原始 torrent 到隐藏暂存目录，但离线下载提交的是 magnet，这是为了兼容 115 对 torrent 路径的限制。
