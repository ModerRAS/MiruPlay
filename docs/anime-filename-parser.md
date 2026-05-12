# BERT 文件名解析与扫描/整理接入

这份文档解释 MiruPlay 里的 BERT 文件名解析器怎么训练、怎么导出到 Android、以及它在现有扫描、刮削和 CloudDrive 自动整理流程里的位置。

## 一句话结论

- 扫描器会看完整路径，但 BERT 模型本身只解析视频文件名，不解析文件夹名。
- 文件夹名仍由规则逻辑处理，用来识别番剧目录、Season 目录和 NFO 归属。
- 文件名先走已有规则，再按需调用 BERT/ONNX 补全 title、season、episode。
- CloudDrive RSS 自动整理也接入同一套分类器，移动文件前会先把下载文件识别成番剧名、季、集。
- 解析结果进入索引表后，再继续走现有 Bangumi 元数据刮削和缓存流程。

## 目录和产物

| 内容 | 路径 | 说明 |
|------|------|------|
| 训练工程 | `tools/anime_parser/` | 普通子目录，不是 Git submodule |
| PyTorch 最终模型 | `tools/anime_parser/checkpoints/final/` | 训练脚本输出的 HuggingFace/PyTorch 模型 |
| ONNX 导出脚本 | `tools/anime_parser/export_onnx.py` | 把 PyTorch 模型导出为 Android 可用 ONNX |
| ONNX 导出产物 | `tools/anime_parser/exports/anime_filename_parser.onnx` | 本地留存的导出模型 |
| Android assets | `scraper/src/main/assets/anime_parser/` | APK 打包时读取的模型、词表、配置 |
| Android 运行时 | `scraper/src/main/kotlin/com/miruplay/tv/scraper/filename/AnimeFilenameParser.kt` | ONNX Runtime 推理、分词、BIO 后处理 |
| 公共接口 | `core/model/src/main/kotlin/com/miruplay/tv/model/FilenameMetadataParser.kt` | scanner 只依赖这个接口 |
| 扫描接入 | `scanner/src/main/kotlin/com/miruplay/tv/scanner/ScanCoordinator.kt` | 创建分类器并传入 parser |
| 自动整理接入 | `sync-engine/src/main/kotlin/com/miruplay/tv/sync/rss/CloudDriveLibraryOrganizer.kt` | RSS 下载后移动文件前使用同一 parser |
| 分类规则 | `scanner/src/main/kotlin/com/miruplay/tv/scanner/VideoDirectoryClassifier.kt` | 合并文件夹规则、文件名规则和 BERT 结果 |

Android 运行需要这三个 assets：

```text
scraper/src/main/assets/anime_parser/anime_filename_parser.onnx
scraper/src/main/assets/anime_parser/vocab.json
scraper/src/main/assets/anime_parser/config.json
```

## 扫描数据流

```text
MediaSource
  -> ScanCoordinator
  -> VideoDirectoryClassifier
       -> 文件夹/路径规则
       -> 文件名规则
       -> BERT 文件名解析补全
  -> IndexRepository
  -> MetadataRepository
  -> BangumiScraper
```

## CloudDrive 整理数据流

```text
CloudDrive RSS offline download
  -> CloudDriveLibraryOrganizer
       -> VideoDirectoryClassifier
            -> 文件夹/路径规则
            -> 文件名规则
            -> BERT 文件名解析补全
       -> moveFiles 到 <整理目录>/<番剧名>/Season <季数>/
  -> ScanCoordinator
  -> IndexRepository
  -> BangumiScraper
```

更具体一点：

1. `ScanCoordinator` 从媒体源递归列目录。
2. 遇到目录时，只把目录当成遍历入口和规则上下文。
3. 遇到视频文件时，调用 `classifyVideo(file.path, file.name)`。
4. `VideoDirectoryClassifier` 从 `file.path` 拆父目录和 Season 目录。
5. `VideoDirectoryClassifier` 从 `file.name` 做 release/episode 规则解析。
6. 当规则缺 title、season 或 episode 时，才懒加载调用 `FilenameMetadataParser`。
7. `AnimeFilenameParser` 在 Android 端用 ONNX Runtime 跑模型。
8. 分类结果写入 index，然后生成或更新 episode，再触发元数据缓存。

## 文件夹名和文件名分别负责什么

### 文件夹名

文件夹名不送进 BERT。它主要负责这些场景：

- 找番剧根目录：例如 `/media/葬送的芙莉莲/03.mkv`。
- 找 Season 目录：例如 `Season 2`、`S02`、`第2季`。
- 给 NFO 写入和读取提供番剧归属。
- 当文件名只有 `03.mkv` 这类弱信息时，提供标题。

示例：

```text
/Anime/葬送的芙莉莲/Season 2/03.mkv
```

这里标题通常来自 `葬送的芙莉莲` 目录，季数来自 `Season 2`，集数来自 `03.mkv`。

### 文件名

文件名负责解析发布组、标题、季、集、清晰度、来源等信息。BERT 的输入就是去掉扩展名后的文件名：

```kotlin
parser.parse(stripVideoExtension(fileName))
```

示例输入：

```text
[ANi] 葬送的芙莉莲 S2 03 [1080P][WEB-DL]
```

不会传入：

```text
/sdcard/Download/raw/[ANi] 葬送的芙莉莲 S2 03 [1080P][WEB-DL].mkv
```

也不会只传入：

```text
raw
```

## 优先级

BERT 不是唯一来源，也不是所有情况下最高优先级。

### Release 文件名已经足够完整

如果 `ReleaseFilenameParser` 能解析出标题、季、集，就优先用规则结果，不额外调用 BERT。

### 文件夹提供番剧名和 Season

如果父目录或 Season 目录已经能提供可靠上下文，就优先用目录上下文。BERT 只在缺字段时补位。

### 普通 fallback 场景

当文件夹没有明显标题，文件名规则也不完整时，BERT 的标题、季、集会优先用于兜底。

典型场景：

```text
/raw/[ANi] 葬送的芙莉莲 S2 03 [1080P][WEB-DL].mkv
```

期望结果：

```text
title=葬送的芙莉莲
season=2
episode=3
```

## 训练和导出

训练工程在 `tools/anime_parser/`。当前代码是一个 Tiny BERT token classification 模型，使用 BIO 标签标注 title、season、episode、group、resolution、source 等字段。

常用命令：

```bash
cd tools/anime_parser
python -m pip install -r requirements.txt
python data_generator.py --num-samples 100000
python train.py
```

导出到 Android assets：

```bash
cd tools/anime_parser
python export_onnx.py --model-dir checkpoints/final --android-assets-dir ../../scraper/src/main/assets/anime_parser
```

导出后会更新：

```text
tools/anime_parser/exports/anime_filename_parser.onnx
tools/anime_parser/exports/anime_filename_parser.metadata.json
scraper/src/main/assets/anime_parser/anime_filename_parser.onnx
scraper/src/main/assets/anime_parser/vocab.json
scraper/src/main/assets/anime_parser/config.json
```

当前 ONNX 输入输出形状：

```text
input_ids: int64[1,64]
attention_mask: int64[1,64]
logits: float32[1,64,15]
```

## Android 运行时

Android 端依赖 `com.microsoft.onnxruntime:onnxruntime-android`，版本在 `gradle/libs.versions.toml` 管理。

运行时特点：

- `AnimeFilenameParser` 是 Hilt 单例。
- ONNX session 懒加载，第一次需要模型解析时才创建。
- 分词器读取 assets 里的 `vocab.json`。
- BIO 标签映射读取 assets 里的 `config.json`。
- 输出统一转成 `FilenameParseResult`。

`scraper` 模块通过 Hilt 把实现绑定为公共接口：

```kotlin
FilenameMetadataParser -> AnimeFilenameParser
```

这样 `scanner` 模块不用知道 ONNX Runtime，也不用直接依赖训练工程。

## 本地验证

单测：

```bash
./gradlew :scanner:testDebugUnitTest --tests "com.miruplay.tv.scanner.ScanCoordinatorTest" --tests "com.miruplay.tv.scanner.VideoDirectoryClassifierTest"
```

构建：

```bash
./gradlew :app:assembleDebug
```

检查 APK 是否包含 assets：

```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep anime_parser
```

Windows PowerShell 可用：

```powershell
tar -tf app\build\outputs\apk\debug\app-debug.apk | Select-String anime_parser
```

## ADB 实机验证流程

示例设备：

```bash
adb connect 10.137.32.118:5555
adb -s 10.137.32.118:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 10.137.32.118:5555 shell am start -n com.miruplay.tv/.MainActivity
```

准备测试文件：

```bash
adb -s 10.137.32.118:5555 shell mkdir -p /sdcard/Download/MiruPlayBertAdbTest/raw
adb -s 10.137.32.118:5555 shell touch '/sdcard/Download/MiruPlayBertAdbTest/raw/[ANi] 葬送的芙莉莲 S2 03 [1080P][WEB-DL].mkv'
adb -s 10.137.32.118:5555 shell touch '/sdcard/Download/MiruPlayBertAdbTest/raw/Sousou no Frieren Season 2 EP03 1080P WEB-DL.mkv'
```

通过 WebControl API 添加本地源并扫描：

```bash
curl -X POST http://10.137.32.118:9978/api/sources \
  -H "Content-Type: application/json" \
  --data '{"name":"ADB BERT Test","type":"LOCAL","location":"/sdcard/Download/MiruPlayBertAdbTest","displayName":"ADB BERT Test"}'

curl -X POST http://10.137.32.118:9978/api/sources/4/scan
```

PowerShell 下可以把上面的 `curl` 写成 `curl.exe`，或者改成单行执行。

期望扫描响应：

```json
{
  "ok": true,
  "data": {
    "episodesFound": 2,
    "newEpisodes": 2
  },
  "error": null
}
```

查询设备数据库：

```bash
adb -s 10.137.32.118:5555 shell "run-as com.miruplay.tv sqlite3 databases/miruplay.db 'SELECT source_id,path,anime_name,season_number,episode_number FROM index_entry WHERE source_id=4 ORDER BY path;'"
```

实测结果：

```text
4|/sdcard/Download/MiruPlayBertAdbTest/raw/Sousou no Frieren Season 2 EP03 1080P WEB-DL.mkv|Sousou no Frieren|2|3
4|/sdcard/Download/MiruPlayBertAdbTest/raw/[ANi] 葬送的芙莉莲 S2 03 [1080P][WEB-DL].mkv|葬送的芙莉莲|2|3
```

logcat 期望看到：

```text
Scan done: ADB BERT Test -> 2 files, 2 new episodes
```

## 常见问题

### 为什么同一个 Bangumi 条目会出现多个本地 anime id

当前库里的 anime id 仍以扫描出的本地标题为 key。例如 `葬送的芙莉莲` 和 `Sousou no Frieren` 会各自作为本地 id，但都能刮削到同一个 Bangumi 条目。后续如果要合并同义标题，需要在 metadata/index 层增加统一 subject id 或 alias 机制。

### 为什么某些文件没有调用 BERT

这是预期行为。规则已经能确定标题、季、集时，不需要模型。这样更快，也避免模型把规则已经正确的结果改坏。

### 为什么文件夹名不送进 BERT

模型训练目标是文件名结构解析。文件夹名通常是更高层的组织信息，交给目录规则更稳定。如果以后要让模型解析完整路径，需要重新设计训练数据和标签策略。

### `/sdcard/...` 和 `/storage/emulated/0/...` 有什么关系

Android 上 `/sdcard` 通常是 `/storage/emulated/0` 的别名。扫描边界检查会把本地路径规范化后再判断，避免同一目录因为别名不同被误判成越界。

### 替换模型后 Android 没变化

确认这几件事：

- `scraper/src/main/assets/anime_parser/anime_filename_parser.onnx` 已更新。
- `vocab.json` 和 `config.json` 与模型来自同一个 checkpoint。
- 重新执行了 `./gradlew :app:assembleDebug`。
- 设备上重新安装了新的 APK。

## 维护约定

- `tools/anime_parser/` 必须保持普通目录，不要改成 Git submodule。
- 不要提交 `.gitmodules`。
- 不要让 scanner 直接依赖 PyTorch 或 Python 工程。
- BERT 输入保持为去扩展名后的文件名，除非同步更新训练数据、导出脚本和本文档。
- 新增解析行为时，优先补 `VideoDirectoryClassifierTest` 或 `ScanCoordinatorTest`。
