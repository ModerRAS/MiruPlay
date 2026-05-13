# Anime Filename Parser

A Tiny BERT-based model that parses anime filenames into structured metadata (title, season, episode, group, resolution, source, etc.).

For the MiruPlay Android integration, scanner data flow, and ADB verification steps, see:

- [`../../docs/anime-filename-parser.md`](../../docs/anime-filename-parser.md)

**Model**: Tiny BERT (3.6M parameters, `hidden_size=256`, 4 layers, 8 heads)
**Training**: Pure CPU, no GPU required
**Data**: Synthetic templates plus optional weak-labeled DMHY filename snapshots

## Quick Start

```bash
# Install dependencies
pip install -r requirements.txt

# Generate synthetic training data (100K samples)
python data_generator.py --num-samples 100000

# Train the model
python train.py

# Parse a filename
python inference.py "[ANi] 葬送的芙莉莲 S2 - 03 [1080P][WEB-DL]"
```

## Project Structure

```
anime_parser/
├── config.py              # All configurable hyperparameters
├── tokenizer.py           # Regex and character-level tokenizer variants
├── data_generator.py      # Synthetic training data generator with BIO labels
├── dataset.py             # PyTorch Dataset with label alignment
├── model.py               # Tiny BERT model definition
├── train.py               # CPU training script (HuggingFace Trainer)
├── inference.py           # Inference script with CLI and postprocessing
├── requirements.txt       # Python dependencies
└── README.md              # This file
```

## Tokenizer Design

The default tokenizer uses a **3-layer regex-based approach** (not BPE/WordPiece):

1. **Bracket Protection**: Content in `[...]`, `(...)`, `【...】`, `《...》` is kept as single tokens
2. **Format Token Recognition**: Known patterns (resolutions, codecs, season/episode markers, etc.) are preserved as tokens
3. **Remainder Splitting**: Remaining text is split by separators, with CJK characters at individual character level and English/numbers kept whole

This design ensures that structural information like `[1080P]`, `S2`, `x265` is never fragmented.

For A/B testing, `CharAnimeTokenizer` is also available. It tokenizes every
character as a token and `AnimeDataset` expands the existing BIO labels so the
same JSONL data can be reused:

```bash
# Regex baseline
python train.py --tokenizer regex --save-dir ./checkpoints_regex

# Character-level variant
python train.py --tokenizer char --max-seq-length 128 --save-dir ./checkpoints_char --rebuild-vocab
```

Initial 10K synthetic-sample A/B run (`3` epochs, batch size `64`, seed `42`):

| Variant | Max length | Vocab | Params | Eval F1 | Accuracy | Train runtime |
|---------|------------|-------|--------|---------|----------|---------------|
| `regex` | 64 | 1539 | 3.59M | 0.9914 | 0.9970 | 502.9s |
| `char` | 128 | 510 | 3.33M | 0.7732 | 0.9600 | 852.9s |

Balanced mixed-data A/B run (`50K` synthetic + `50K` DMHY weak labels, `1`
epoch, batch size `128`, seed `42`):

| Variant | Max length | Vocab | Params | Eval F1 | Accuracy | Train runtime |
|---------|------------|-------|--------|---------|----------|---------------|
| `regex` | 64 | 3000 | 3.96M | 0.9911 | 0.9951 | 827s |
| `char` | 128 | 2654 | 3.88M | 0.8142 | 0.9637 | 1983s |

Field-level F1 on the same validation split:

| Field | `regex` | `char` |
|-------|---------|--------|
| `GROUP` | 0.9962 | 0.9516 |
| `TITLE` | 0.9761 | 0.7983 |
| `SEASON` | 0.9880 | 0.6290 |
| `EPISODE` | 0.9950 | 0.8082 |

The regex tokenizer remains the default. Both variants can parse simple
`S01E07`, but the char model was much weaker on bare season/episode pairs and
long title boundaries.

## Label Scheme (BIO Format)

| Label | Description | Example |
|-------|-------------|---------|
| `O` | Outside / irrelevant | spaces, separators, decorations |
| `B-TITLE` / `I-TITLE` | Anime title | "葬送的芙莉莲", "Mushoku Tensei" |
| `B-SEASON` / `I-SEASON` | Season number | "S2", "Season 1", "第二季" |
| `B-EPISODE` / `I-EPISODE` | Episode number | "03", "EP21", "第7话" |
| `B-SPECIAL` / `I-SPECIAL` | Special type | "OVA", "Movie", "SP" |
| `B-GROUP` / `I-GROUP` | Release group | "[ANi]", "【喵萌奶茶屋】" |
| `B-RESOLUTION` / `I-RESOLUTION` | Resolution | "1080P", "4K" |
| `B-SOURCE` / `I-SOURCE` | Source/Codec/Language | "WEB-DL", "x265", "CHT" |

## Data Generation

Synthetic data is generated using template-based filling with content pools:

- **200+ titles**: Chinese, English, Japanese, mixed
- **50+ groups**: Fansub groups with various bracket styles
- **20+ season variations**: S1-S5, Season 1-3, 第一季~第四季
- **15+ episode patterns**: 01-99, EP01-EP99, 第1话~第99话
- **Meta tokens**: Resolutions, sources, codecs, audio formats, languages

Data augmentation includes random ordering, decorations, and noise.

## DMHY Weak Dataset Artifacts

`dmhy_dataset.py` exports weak BIO labels from the local DMHY SQLite crawl, and
`mix_datasets.py` mixes them with synthetic samples. Generated JSONL datasets
and training checkpoints are intentionally ignored by git because they are
large and reproducible.

Current snapshot manifest:

- `data/dmhy/dmhy_weak.manifest.json`
- `last_file_id`: `689304`
- `labeled_samples`: `263042`
- `video_rows`: `363921`

When the crawler finishes, continue from `--min-id 689305` to label only the
new rows. Large snapshot files such as `dmhy_weak.jsonl`, `mixed_train.jsonl`,
and trained checkpoint directories should be packaged as GitHub Release assets
instead of committed to the repository.

## Training

The model uses HuggingFace `BertForTokenClassification` from scratch (no pretrained weights):

| Parameter | Value |
|-----------|-------|
| Hidden size | 256 |
| Layers | 4 |
| Attention heads | 8 |
| Parameters | ~3.6M |
| Training data | 100K synthetic samples |
| Epochs | 15 |
| Batch size | 64 |
| Learning rate | 1e-3 |
| Device | CPU |

```bash
python train.py
```

The training script outputs checkpoints to `./checkpoints/` and saves the final model to `./checkpoints/final/`.

## Inference

### Single filename

```bash
python inference.py "[ANi] 葬送的芙莉莲 S2 - 03 [1080P][WEB-DL]"
```

Output:
```json
{
  "title": "葬送的芙莉莲",
  "season": 2,
  "episode": 3,
  "group": "ANi",
  "resolution": "1080P",
  "source": "WEB-DL",
  "special": null
}
```

More examples:

```bash
python inference.py "[SubsPlease] Mushoku Tensei - 12 (1080p) [x265][AAC]"
python inference.py "86 Eighty Six - 01 [1080P][Baha]"
```

### Batch processing

```bash
python inference.py --input-file filenames.txt --output-file results.jsonl
```

Each line in `filenames.txt` is a filename to parse. Results are saved as JSONL.

### Using a custom model

```bash
python inference.py "filename" --model-dir ./my_checkpoints/final
```

## Configuration

Key parameters in `config.py`:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `synthetic_data_size` | 100000 | Number of training samples |
| `hidden_size` | 256 | BERT hidden dimension |
| `num_hidden_layers` | 4 | Number of transformer layers |
| `num_attention_heads` | 8 | Number of attention heads |
| `max_seq_length` | 64 | Maximum sequence length |
| `batch_size` | 64 | Training batch size |
| `learning_rate` | 1e-3 | Peak learning rate |
| `num_epochs` | 15 | Training epochs |

## Dependencies

- PyTorch >= 2.0.0
- Transformers >= 4.30.0
- Datasets >= 2.12.0
- Accelerate >= 1.1.0
- seqeval >= 1.2.2
- numpy >= 1.24.0
- tqdm >= 4.65.0

## Known Limitations

- Titles spanning multiple words separated by spaces may be returned as fragments
- Mixed-content bracket tokens (e.g., `[BDRip 1920x1080 FLAC]`) are treated as single tokens, which limits granularity
- The model is trained on synthetic data only and may not generalize to all real-world filename patterns
