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
from tokenizer import AnimeTokenizer
from model import create_model, print_model_summary, count_parameters
from dataset import AnimeDataset


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


def main():
    config = Config()

    # Load tokenizer
    print("Loading tokenizer...")
    vocab_path = os.path.join(os.path.dirname(config.data_file), "vocab.json")
    tokenizer = AnimeTokenizer(vocab_file=vocab_path)
    print(f"  Vocab size: {tokenizer.vocab_size}")

    # Update config with actual vocab size
    config.vocab_size = tokenizer.vocab_size

    # Create model
    print("Creating model...")
    model: BertForTokenClassification = create_model(config)
    total_params = print_model_summary(model)

    if total_params >= 5_000_000:
        print("WARNING: Model exceeds 5M parameter limit. Consider reducing hidden_size or layers.")
        sys.exit(1)

    # Create datasets
    print("Loading dataset...")
    with open(config.data_file, 'r', encoding='utf-8') as f:
        all_data = [json.loads(line) for line in f if line.strip()]

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
