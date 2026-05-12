# Anime Filename Parser

A Tiny BERT-based model that parses anime filenames into structured metadata (title, season, episode, group, resolution, source, etc.).

**Model**: Tiny BERT (3.6M parameters, `hidden_size=256`, 4 layers, 8 heads)
**Training**: Pure CPU, no GPU required
**Data**: Synthetically generated (100K samples) using template filling with BIO label annotations

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
├── tokenizer.py           # Custom regex-based structure tokenizer (3-layer)
├── data_generator.py      # Synthetic training data generator with BIO labels
├── dataset.py             # PyTorch Dataset with label alignment
├── model.py               # Tiny BERT model definition
├── train.py               # CPU training script (HuggingFace Trainer)
├── inference.py           # Inference script with CLI and postprocessing
├── requirements.txt       # Python dependencies
└── README.md              # This file
```

## Tokenizer Design

The tokenizer uses a **3-layer regex-based approach** (not BPE/WordPiece):

1. **Bracket Protection**: Content in `[...]`, `(...)`, `【...】`, `《...》` is kept as single tokens
2. **Format Token Recognition**: Known patterns (resolutions, codecs, season/episode markers, etc.) are preserved as tokens
3. **Remainder Splitting**: Remaining text is split by separators, with CJK characters at individual character level and English/numbers kept whole

This design ensures that structural information like `[1080P]`, `S2`, `x265` is never fragmented.

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
