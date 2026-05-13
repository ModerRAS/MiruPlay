"""
PyTorch Dataset for anime filename token classification.

Loads JSONL data (tokens + BIO labels) and converts to model inputs.
Handles token-ID conversion, label encoding, padding, and truncation.
"""

import json
import torch
from torch.utils.data import Dataset
from typing import Dict, List, Optional

from config import Config
from tokenizer import AnimeTokenizer


class AnimeDataset(Dataset):
    """
    Dataset for anime filename token classification.

    Loads pre-tokenized data from JSONL files and prepares model inputs.
    Each sample has:
        - input_ids: token IDs with [CLS] prefix and [SEP] suffix
        - attention_mask: 1 for real tokens, 0 for padding
        - labels: integer label IDs, -100 for special/padding tokens
    """

    def __init__(
        self,
        data_path: str,
        tokenizer: AnimeTokenizer,
        label2id: Dict[str, int],
        max_length: int = 64,
    ):
        """
        Args:
            data_path: Path to JSONL file with tokens and labels.
            tokenizer: AnimeTokenizer instance.
            label2id: Mapping from label string to integer ID.
            max_length: Maximum sequence length (including special tokens).
        """
        self.tokenizer = tokenizer
        self.label2id = label2id
        self.max_length = max_length

        # Load data
        self.data: List[Dict] = []
        with open(data_path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line:
                    self.data.append(json.loads(line))

    def __len__(self) -> int:
        return len(self.data)

    def __getitem__(self, idx: int) -> Dict[str, torch.Tensor]:
        """
        Get a preprocessed sample.

        Returns:
            Dictionary with input_ids, attention_mask, labels as LongTensors.
        """
        item = self.data[idx]
        tokens: List[str] = item["tokens"]
        labels: List[str] = item["labels"]
        tokens, labels = align_tokens_for_tokenizer(tokens, labels, self.tokenizer)

        # Convert tokens to IDs
        input_ids = self.tokenizer.convert_tokens_to_ids(tokens)

        # Add [CLS] at start and [SEP] at end
        input_ids = [self.tokenizer.cls_token_id] + input_ids + [self.tokenizer.sep_token_id]

        # Convert labels to IDs, with -100 for special tokens
        label_ids: List[int] = [-100]  # [CLS] → -100 (ignored in loss)
        for label in labels:
            label_ids.append(self.label2id.get(label, 0))  # default to O
        label_ids.append(-100)  # [SEP] → -100

        # Attention mask: 1 for real tokens
        attention_mask = [1] * len(input_ids)

        # Truncate if needed (keep CLS at 0, SEP at end)
        if len(input_ids) > self.max_length:
            # Keep first token (CLS), truncate middle, keep last token (SEP)
            input_ids = [input_ids[0]] + input_ids[1:self.max_length - 1] + [input_ids[-1]]
            label_ids = [label_ids[0]] + label_ids[1:self.max_length - 1] + [label_ids[-1]]
            attention_mask = [attention_mask[0]] + attention_mask[1:self.max_length - 1] + [attention_mask[-1]]

        # Pad to max_length
        pad_len = self.max_length - len(input_ids)
        if pad_len > 0:
            input_ids += [self.tokenizer.pad_token_id] * pad_len
            label_ids += [-100] * pad_len
            attention_mask += [0] * pad_len

        return {
            "input_ids": torch.tensor(input_ids, dtype=torch.long),
            "attention_mask": torch.tensor(attention_mask, dtype=torch.long),
            "labels": torch.tensor(label_ids, dtype=torch.long),
        }


def align_tokens_for_tokenizer(
    tokens: List[str],
    labels: List[str],
    tokenizer: AnimeTokenizer,
) -> tuple[List[str], List[str]]:
    """
    Align pre-labeled JSONL samples to the selected tokenizer.

    The existing datasets store regex-tokenized samples. For the char A/B run,
    each original token is split into characters while preserving BIO spans:
    B-X stays on the first character, and the rest become I-X.
    """
    if getattr(tokenizer, "tokenizer_variant", "regex") != "char":
        return tokens, labels

    aligned_tokens: List[str] = []
    aligned_labels: List[str] = []

    for token, label in zip(tokens, labels):
        pieces = tokenizer.tokenize(token)
        if not pieces:
            continue

        aligned_tokens.extend(pieces)
        aligned_labels.append(label)

        if label.startswith(("B-", "I-")):
            continuation = "I-" + label.split("-", 1)[1]
        else:
            continuation = label
        aligned_labels.extend([continuation] * (len(pieces) - 1))

    return aligned_tokens, aligned_labels


def create_datasets(
    data_path: str,
    tokenizer: AnimeTokenizer,
    config: Config,
) -> tuple:
    """
    Create train and validation datasets from a JSONL file.

    The file is split by the first N samples for training,
    the rest for validation based on config.train_split.

    Returns:
        (train_dataset, eval_dataset)
    """
    # Load all data to determine split
    with open(data_path, 'r', encoding='utf-8') as f:
        all_data = [json.loads(line) for line in f if line.strip()]

    split_idx = int(len(all_data) * config.train_split)
    train_data = all_data[:split_idx]
    eval_data = all_data[split_idx:]

    # Write temp files for each split
    import tempfile
    import os

    train_file = os.path.join(tempfile.gettempdir(), "anime_train.jsonl")
    eval_file = os.path.join(tempfile.gettempdir(), "anime_eval.jsonl")

    with open(train_file, 'w', encoding='utf-8') as f:
        for item in train_data:
            f.write(json.dumps(item, ensure_ascii=False) + '\n')

    with open(eval_file, 'w', encoding='utf-8') as f:
        for item in eval_data:
            f.write(json.dumps(item, ensure_ascii=False) + '\n')

    train_dataset = AnimeDataset(
        data_path=train_file,
        tokenizer=tokenizer,
        label2id=config.label2id,
        max_length=config.max_seq_length,
    )
    eval_dataset = AnimeDataset(
        data_path=eval_file,
        tokenizer=tokenizer,
        label2id=config.label2id,
        max_length=config.max_seq_length,
    )

    return train_dataset, eval_dataset


if __name__ == "__main__":
    # Quick test
    from config import Config
    cfg = Config()

    tok = AnimeTokenizer()
    # Build a minimal vocab
    tok.build_vocab([["[ANi]", "test", "S2", "-", "03"],
                     ["[Baha]", "anime", "01"]])

    ds = AnimeDataset(
        data_path="data/synthetic.jsonl",
        tokenizer=tok,
        label2id=cfg.label2id,
        max_length=cfg.max_seq_length,
    )

    print(f"Dataset size: {len(ds)}")
    if len(ds) > 0:
        sample = ds[0]
        print(f"input_ids shape: {sample['input_ids'].shape}")
        print(f"attention_mask shape: {sample['attention_mask'].shape}")
        print(f"labels shape: {sample['labels'].shape}")
        print(f"input_ids: {sample['input_ids'].tolist()}")
        print(f"labels: {sample['labels'].tolist()}")
        print(f"attention_mask: {sample['attention_mask'].tolist()}")
