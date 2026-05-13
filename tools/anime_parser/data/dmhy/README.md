# DMHY Dataset Snapshot

This directory keeps only small metadata files in git. Large generated JSONL
datasets and model checkpoints are ignored and should be published as release
assets when they need to be shared.

Current exported SQLite waterline:

- Source DB: `D:\WorkSpace\Python\dmhy-parser\dmhy_anime.db`
- Last exported `files.id`: `689304`
- Labeled samples: `263042`
- Export manifest: `dmhy_weak.manifest.json`

Use `--min-id 689305` for the next incremental export after the crawler has
finished collecting more rows.

Suggested release assets for this snapshot:

- `dmhy_weak.jsonl`
- `mixed_train.jsonl`
- `checkpoints/dmhy-finetune/final/`
