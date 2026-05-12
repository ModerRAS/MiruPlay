# Android export and runtime

This folder is a normal MiruPlay subdirectory, not a Git submodule. It contains
the Python training pipeline plus an ONNX export path for Android.

## Export

From `tools/anime_parser`:

```bash
python -m pip install -r requirements.txt
python export_onnx.py --model-dir checkpoints/final --android-assets-dir ../../scraper/src/main/assets/anime_parser
```

The exporter writes:

- `exports/anime_filename_parser.onnx`
- `exports/anime_filename_parser.metadata.json`
- `scraper/src/main/assets/anime_parser/anime_filename_parser.onnx`
- `scraper/src/main/assets/anime_parser/vocab.json`
- `scraper/src/main/assets/anime_parser/config.json`

The ONNX graph uses fixed Android inputs:

- `input_ids`: `int64[1,64]`
- `attention_mask`: `int64[1,64]`
- `logits`: `float32[1,64,15]`

The current export was verified against PyTorch with max absolute logits
difference `2.5033950805664062e-05`.

## Runtime

Android runs the exported graph through ONNX Runtime Android. Tokenization and
BIO postprocessing are implemented in:

`scraper/src/main/kotlin/com/miruplay/tv/scraper/filename/AnimeFilenameParser.kt`

The app exposes it through `FilenameMetadataParser` in `core:model`. During a
scan, `ScanCoordinator` passes that parser into `VideoDirectoryClassifier`; the
classifier keeps the existing release/folder regexes first and lazily calls the
model only when those heuristics are missing title, season, or episode data.

Example Kotlin usage:

```kotlin
val parsed = animeFilenameParser.parse("[ANi] 葬送的芙莉莲 S2 - 03 [1080P][WEB-DL]")
```

Expected fields:

```text
title=葬送的芙莉莲, season=2, episode=3, group=ANi, resolution=1080P, source=WEB-DL
```
