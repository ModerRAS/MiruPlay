"""
Training script for anime filename parser.

Trains a Tiny BERT model for token classification on synthetic anime filename data.
Uses HuggingFace Trainer for CPU training.

Usage:
    python train.py
"""

import os
import sys
import json
import tempfile
import argparse
import random
from typing import Dict, List, Optional

import numpy as np
import torch
from transformers import (
    Trainer,
    TrainingArguments,
    DataCollatorForTokenClassification,
    BertForTokenClassification,
)
from seqeval.metrics import classification_report, accuracy_score, f1_score, precision_score, recall_score

from config import Config
from tokenizer import AnimeTokenizer, create_tokenizer
from model import create_model, print_model_summary, count_parameters
from dataset import AnimeDataset, align_tokens_for_tokenizer


def compute_metrics(p):
    """Compute token-level and entity-level metrics using seqeval."""
    predictions, labels = p
    predictions = np.argmax(predictions, axis=2)

    # Remove ignored index (special tokens)
    true_predictions = []
    true_labels = []

    id2label = Config().id2label

    for pred_seq, label_seq in zip(predictions, labels):
        preds = []
        lbls = []
        for p, l in zip(pred_seq, label_seq):
            if l != -100:
                preds.append(id2label[p])
                lbls.append(id2label[l])
        true_predictions.append(preds)
        true_labels.append(lbls)

    # Entity-level metrics (via seqeval)
    return {
        "precision": precision_score(true_labels, true_predictions),
        "recall": recall_score(true_labels, true_predictions),
        "f1": f1_score(true_labels, true_predictions),
        "accuracy": accuracy_score(true_labels, true_predictions),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train anime filename parser")
    parser.add_argument("--tokenizer", choices=["regex", "char"], default="regex",
                        help="Tokenizer variant for A/B testing")
    parser.add_argument("--data-file", default=None, help="Training JSONL file")
    parser.add_argument("--vocab-file", default=None,
                        help="Tokenizer vocab JSON. Defaults to data/vocab.json or data/vocab.char.json")
    parser.add_argument("--save-dir", default=None, help="Checkpoint output directory")
    parser.add_argument("--init-model-dir", default=None, help="Optional checkpoint to fine-tune from")
    parser.add_argument("--epochs", type=float, default=None, help="Number of training epochs")
    parser.add_argument("--batch-size", type=int, default=None, help="Per-device train/eval batch size")
    parser.add_argument("--learning-rate", type=float, default=None, help="Learning rate")
    parser.add_argument("--warmup-steps", type=int, default=None, help="Warmup steps")
    parser.add_argument("--train-split", type=float, default=None, help="Train split ratio")
    parser.add_argument("--max-seq-length", type=int, default=None, help="Maximum sequence length")
    parser.add_argument("--seed", type=int, default=42, help="Random seed")
    parser.add_argument("--limit-samples", type=int, default=None,
                        help="Use only the first N samples for quick A/B smoke runs")
    parser.add_argument("--rebuild-vocab", action="store_true",
                        help="Rebuild vocab from the selected data file before training")
    parser.add_argument("--no-shuffle", action="store_true", help="Do not shuffle before train/eval split")
    return parser.parse_args()


def resolve_vocab_path(data_file: str, tokenizer_variant: str, explicit_path: Optional[str]) -> str:
    if explicit_path:
        return explicit_path
    name = "vocab.json" if tokenizer_variant == "regex" else "vocab.char.json"
    return os.path.join(os.path.dirname(data_file), name)


def build_vocab_from_data(data: List[Dict], tokenizer: AnimeTokenizer, vocab_path: str) -> None:
    token_lists: List[List[str]] = []
    for item in data:
        tokens, labels = align_tokens_for_tokenizer(item["tokens"], item["labels"], tokenizer)
        token_lists.append(tokens)

    tokenizer.build_vocab(token_lists)
    save_dir = os.path.dirname(vocab_path) or "."
    os.makedirs(save_dir, exist_ok=True)
    with open(vocab_path, "w", encoding="utf-8") as f:
        json.dump(tokenizer.get_vocab(), f, ensure_ascii=False, indent=2)


def main():
    args = parse_args()
    config = Config()
    if args.data_file is not None:
        config.data_file = args.data_file
    if args.save_dir is not None:
        config.save_dir = args.save_dir
    elif args.tokenizer == "char":
        config.save_dir = "./checkpoints_char"
    if args.epochs is not None:
        config.num_epochs = args.epochs
    if args.batch_size is not None:
        config.batch_size = args.batch_size
    if args.learning_rate is not None:
        config.learning_rate = args.learning_rate
    if args.warmup_steps is not None:
        config.warmup_steps = args.warmup_steps
    if args.train_split is not None:
        config.train_split = args.train_split
    if args.max_seq_length is not None:
        config.max_seq_length = args.max_seq_length

    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)

    print("Loading dataset...")
    with open(config.data_file, 'r', encoding='utf-8') as f:
        all_data = [json.loads(line) for line in f if line.strip()]
    if args.limit_samples is not None:
        all_data = all_data[:args.limit_samples]
    if not args.no_shuffle:
        random.shuffle(all_data)

    # Load tokenizer
    print("Loading tokenizer...")
    vocab_path = resolve_vocab_path(config.data_file, args.tokenizer, args.vocab_file)
    tokenizer = create_tokenizer(args.tokenizer)
    if args.rebuild_vocab or not os.path.isfile(vocab_path):
        print(f"  Building {args.tokenizer} vocab: {vocab_path}")
        build_vocab_from_data(all_data, tokenizer, vocab_path)
    tokenizer = create_tokenizer(args.tokenizer, vocab_file=vocab_path)
    print(f"  Variant: {args.tokenizer}")
    print(f"  Vocab size: {tokenizer.vocab_size}")

    # Update config with actual vocab size
    config.vocab_size = tokenizer.vocab_size

    # Create model
    if args.init_model_dir:
        print(f"Loading model for fine-tuning: {args.init_model_dir}")
        model = BertForTokenClassification.from_pretrained(args.init_model_dir)
        if model.config.vocab_size != config.vocab_size:
            print(f"  Resizing token embeddings: {model.config.vocab_size} -> {config.vocab_size}")
            model.resize_token_embeddings(config.vocab_size)
        model.config.num_labels = config.num_labels
        model.config.id2label = config.id2label
        model.config.label2id = config.label2id
    else:
        print("Creating model...")
        model: BertForTokenClassification = create_model(config)
    total_params = print_model_summary(model)

    if total_params >= 5_000_000:
        print("WARNING: Model exceeds 5M parameter limit. Consider reducing hidden_size or layers.")
        sys.exit(1)

    split_idx = int(len(all_data) * config.train_split)
    train_data = all_data[:split_idx]
    eval_data = all_data[split_idx:]

    # Write split files (temp)
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

    print(f"  Train samples: {len(train_dataset)}")
    print(f"  Eval samples: {len(eval_dataset)}")

    # Training arguments
    training_args = TrainingArguments(
        output_dir=config.save_dir,
        num_train_epochs=config.num_epochs,
        per_device_train_batch_size=config.batch_size,
        per_device_eval_batch_size=config.batch_size,
        eval_strategy="epoch",
        save_strategy="epoch",
        logging_steps=config.log_interval,
        learning_rate=config.learning_rate,
        weight_decay=config.weight_decay,
        warmup_steps=config.warmup_steps,
        use_cpu=True,
        report_to="none",
        save_total_limit=2,
        load_best_model_at_end=True,
        metric_for_best_model="f1",
        greater_is_better=True,
        dataloader_num_workers=config.num_workers,
    )

    # Data collator
    data_collator = DataCollatorForTokenClassification(tokenizer)

    # Trainer
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=eval_dataset,
        data_collator=data_collator,
        compute_metrics=compute_metrics,
    )

    # Train
    print("Starting training...")
    trainer.train()

    # Set proper label mappings in model config before saving
    model.config.id2label = config.id2label
    model.config.label2id = config.label2id
    model.config.tokenizer_variant = args.tokenizer
    model.config.max_seq_length = config.max_seq_length

    # Save final model
    final_save_path = os.path.join(config.save_dir, "final")
    trainer.save_model(final_save_path)
    tokenizer.save_pretrained(final_save_path)
    print(f"Model saved to: {final_save_path}")

    # Final evaluation
    print("\nFinal evaluation:")
    eval_results = trainer.evaluate()
    for key, value in eval_results.items():
        print(f"  {key}: {value:.4f}")


if __name__ == "__main__":
    main()
