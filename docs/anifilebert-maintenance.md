# AniFileBERT 维护手册

这份文档记录 MiruPlay、AniFileBERT 和 AnimeName 三个仓库之间的关系，以及后续更新数据、重新训练、发布模型和更新 submodule 指针的固定流程。

## 仓库关系

| 仓库 | 地址 | 作用 |
|------|------|------|
| MiruPlay | `https://github.com/ModerRAS/MiruPlay` | Android TV 主项目 |
| AniFileBERT | `https://huggingface.co/ModerRAS/AniFileBERT` | BERT 模型、训练脚本、ONNX 导出脚本 |
| AnimeName | `https://huggingface.co/datasets/ModerRAS/AnimeName` | DMHY 弱标注数据、混合训练集、A/B 数据集 |

当前 submodule 结构：

```text
MiruPlay
  tools/anime_parser -> ModerRAS/AniFileBERT
    datasets/AnimeName -> ModerRAS/AnimeName
```

当前指针：

```text
tools/anime_parser                 b7f546570cca5d34fa7f42839a8732ce53cfecec
tools/anime_parser/datasets/AnimeName 081fd450aafd59992f2df794c5b0110dc3cdd42b
```

注意：截至 2026-06-03，AniFileBERT 最新提交记录的 nested AnimeName 指针 `081fd450aafd59992f2df794c5b0110dc3cdd42b` 无法从 `ModerRAS/AnimeName` 远端直接 fetch；本地 nested submodule 可能停在远端 `main` 的 `255d53ecf84d339b87618c34a593c7f2f3a0040b`，因此 `tools/anime_parser` 会显示 modified content。更新 MiruPlay Android assets 不依赖这个数据集 checkout。

## 首次拉取

```bash
git clone --recursive https://github.com/ModerRAS/MiruPlay.git
```

已有工作区补 submodule：

```bash
git submodule update --init --recursive
```

如果只想刷新模型工程和数据集：

```bash
git submodule update --remote --recursive tools/anime_parser
```

## 当前数据水位

DMHY SQLite 导出水位记录在：

```text
tools/anime_parser/data/dmhy/dmhy_weak.manifest.json
tools/anime_parser/datasets/AnimeName/dmhy_weak.manifest.json
```

当前快照：

```text
source_db: D:\WorkSpace\Python\dmhy-parser\dmhy_anime.db
last_file_id: 689304
next_min_id: 689305
labeled_samples: 263042
mixed_train_samples: 363042
```

后续爬完新数据后，增量导出从 `--min-id 689305` 开始。

## 更新 AnimeName 数据集

在 AniFileBERT submodule 内操作：

```bash
cd tools/anime_parser
git submodule update --init --recursive
```

导出新的弱标注数据。完整重导出：

```bash
python dmhy_dataset.py \
  --db D:/WorkSpace/Python/dmhy-parser/dmhy_anime.db \
  --output data/dmhy/dmhy_weak.jsonl \
  --base-vocab data/vocab.json \
  --max-vocab-size 3000
```

只导出增量：

```bash
python dmhy_dataset.py \
  --db D:/WorkSpace/Python/dmhy-parser/dmhy_anime.db \
  --output data/dmhy/dmhy_weak_incremental.jsonl \
  --min-id 689305 \
  --base-vocab data/vocab.json \
  --max-vocab-size 3000
```

混合模板数据和 DMHY 数据：

```bash
python mix_datasets.py \
  --synthetic data/synthetic.jsonl \
  --dmhy data/dmhy/dmhy_weak.jsonl \
  --output data/dmhy/mixed_train.jsonl \
  --seed 42
```

把需要公开的数据文件复制或移动到 nested dataset repo：

```bash
Copy-Item data/dmhy/dmhy_weak.jsonl datasets/AnimeName/ -Force
Copy-Item data/dmhy/dmhy_weak.manifest.json datasets/AnimeName/ -Force
Copy-Item data/dmhy/mixed_train.jsonl datasets/AnimeName/ -Force
Copy-Item data/dmhy/mixed_train.manifest.json datasets/AnimeName/ -Force
Copy-Item data/dmhy/vocab.json datasets/AnimeName/ -Force
```

提交并推送数据集：

```bash
git -C datasets/AnimeName status --short
git -C datasets/AnimeName add .
git -C datasets/AnimeName commit -m "Update DMHY dataset snapshot"
git -C datasets/AnimeName push origin main
```

然后回到 AniFileBERT，提交 nested submodule 指针：

```bash
git add datasets/AnimeName data/dmhy/*.manifest.json data/dmhy/vocab.json README.md
git commit -m "Update AnimeName dataset pointer"
git push origin main
```

## 训练 AniFileBERT

使用 Hugging Face dataset submodule 里的混合训练集：

```bash
cd tools/anime_parser
python -m pip install -r requirements.txt
python train.py \
  --data-file datasets/AnimeName/mixed_train.jsonl \
  --vocab-file datasets/AnimeName/vocab.json \
  --save-dir checkpoints/dmhy-finetune \
  --init-model-dir . \
  --epochs 1 \
  --batch-size 128 \
  --learning-rate 0.0003 \
  --warmup-steps 300 \
  --seed 42
```

如果要做 tokenizer A/B，使用平衡子集：

```bash
python mix_datasets.py \
  --synthetic data/synthetic.jsonl \
  --dmhy datasets/AnimeName/dmhy_weak.jsonl \
  --output data/dmhy/ab_mix_100k.jsonl \
  --synthetic-limit 50000 \
  --dmhy-limit 50000 \
  --seed 20260513

python train.py \
  --tokenizer regex \
  --data-file data/dmhy/ab_mix_100k.jsonl \
  --vocab-file datasets/AnimeName/vocab.json \
  --save-dir checkpoints/ab-dmhy-regex-100k \
  --epochs 1 \
  --batch-size 128 \
  --learning-rate 0.0003 \
  --warmup-steps 300 \
  --seed 42

python train.py \
  --tokenizer char \
  --data-file data/dmhy/ab_mix_100k.jsonl \
  --vocab-file data/dmhy/vocab.char.ab100k.json \
  --save-dir checkpoints/ab-dmhy-char-100k \
  --epochs 1 \
  --batch-size 128 \
  --learning-rate 0.0003 \
  --warmup-steps 300 \
  --seed 42 \
  --max-seq-length 128 \
  --rebuild-vocab
```

已知 A/B 结论：regex tokenizer 明显优于 char tokenizer，`S01E07` 两边都能识别，但 char 在季/集边界和长标题上明显更弱。

## 发布模型仓库

训练完成后，把最终 checkpoint 放到 AniFileBERT repo 根目录，让 Hugging Face 可以直接加载：

```powershell
Copy-Item checkpoints/dmhy-finetune/final/config.json . -Force
Copy-Item checkpoints/dmhy-finetune/final/model.safetensors . -Force
Copy-Item checkpoints/dmhy-finetune/final/tokenizer_config.json . -Force
Copy-Item checkpoints/dmhy-finetune/final/training_args.bin . -Force
Copy-Item checkpoints/dmhy-finetune/final/vocab.json . -Force
```

提交并推送模型：

```bash
git status --short
git add .
git commit -m "Update AniFileBERT checkpoint"
git push origin main
```

## 导出 Android assets

在 AniFileBERT submodule 内导出 ONNX，并同步到 MiruPlay Android assets：

```bash
cd tools/anime_parser
python -m tools.export_onnx \
  --model-dir . \
  --max-length 128 \
  --android-assets-dir ../../scraper/src/main/assets/anime_parser
```

导出后 MiruPlay 主仓库会出现这些改动：

```text
scraper/src/main/assets/anime_parser/anime_filename_parser.onnx
scraper/src/main/assets/anime_parser/config.json
scraper/src/main/assets/anime_parser/vocab.json
tools/anime_parser
```

其中 `tools/anime_parser` 是 submodule 指针变化，前三个是 APK 内置运行资产。

## 更新 MiruPlay 指针

当 AniFileBERT 已经 push 到 Hugging Face 后，在 MiruPlay 根目录执行：

```bash
git submodule update --remote --recursive tools/anime_parser
git submodule status --recursive
git add tools/anime_parser scraper/src/main/assets/anime_parser
git commit -m "Update AniFileBERT parser assets"
git push origin master
```

如果只更新文档或模型仓库指针，没有 Android assets 变化，就只提交：

```bash
git add tools/anime_parser
git commit -m "Update AniFileBERT submodule"
```

## GitHub Release 资产

Hugging Face dataset repo 是训练数据主位置。GitHub Release 可以作为可下载快照备份。

当前快照 release：

```text
https://github.com/ModerRAS/MiruPlay/releases/tag/anime-parser-dmhy-snapshot-20260513
```

建议 release assets：

```text
dmhy_weak.jsonl.zip
mixed_train.jsonl.zip
ab_mix_100k.jsonl.zip
dmhy-finetune-final.zip
ab-dmhy-regex-100k-final.zip
ab-dmhy-char-100k-final.zip
*.manifest.json
SHA256SUMS.txt
```

## 验证清单

更新数据或模型后至少检查：

```bash
git status --short
git submodule status --recursive
python -m compileall anifilebert tools
```

更新 Android assets 后检查：

```bash
./gradlew :scraper:compileDebugKotlin
./gradlew :scanner:testDebugUnitTest --tests "com.miruplay.tv.scanner.VideoDirectoryClassifierTest"
```

最后确认 APK 里有模型：

```powershell
tar -tf app\build\outputs\apk\debug\app-debug.apk | Select-String anime_parser
```
