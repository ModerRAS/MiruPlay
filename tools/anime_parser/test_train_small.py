"""Quick test: train with a small subset to verify the pipeline."""
import json
import os
import argparse
import sys
import tempfile

from transformers import (
    Trainer, TrainingArguments, DataCollatorForTokenClassification
)

from config import Config
from tokenizer import create_tokenizer
from model import create_model, count_parameters
from dataset import AnimeDataset, align_tokens_for_tokenizer
from train import compute_metrics

parser = argparse.ArgumentParser(description="Quick test: train a small A/B subset")
parser.add_argument("--tokenizer", choices=["regex", "char"], default="regex")
parser.add_argument("--limit-samples", type=int, default=5000)
parser.add_argument("--epochs", type=float, default=2)
parser.add_argument("--max-seq-length", type=int, default=None)
args_cli = parser.parse_args()

cfg = Config()
if args_cli.max_seq_length is not None:
    cfg.max_seq_length = args_cli.max_seq_length

# Load tokenizer
vocab_file = 'data/vocab.json' if args_cli.tokenizer == 'regex' else 'data/vocab.char.json'
tok = create_tokenizer(args_cli.tokenizer)
if not os.path.isfile(vocab_file):
    with open('data/synthetic.jsonl', 'r', encoding='utf-8') as f:
        vocab_data = [json.loads(line) for line in f][:args_cli.limit_samples]
    tok.build_vocab([
        align_tokens_for_tokenizer(item['tokens'], item['labels'], tok)[0]
        for item in vocab_data
    ])
    with open(vocab_file, 'w', encoding='utf-8') as f:
        json.dump(tok.get_vocab(), f, ensure_ascii=False, indent=2)
tok = create_tokenizer(args_cli.tokenizer, vocab_file=vocab_file)
cfg.vocab_size = tok.vocab_size

# Create model
model = create_model(cfg)
print(f'Model params: {count_parameters(model):,}')

# Use first N samples
with open('data/synthetic.jsonl', 'r', encoding='utf-8') as f:
    all_data = [json.loads(line) for line in f][:args_cli.limit_samples]

split_idx = int(len(all_data) * cfg.train_split)
train_data = all_data[:split_idx]
eval_data = all_data[split_idx:]

train_file = os.path.join(tempfile.gettempdir(), 'test_train.jsonl')
eval_file = os.path.join(tempfile.gettempdir(), 'test_eval.jsonl')

with open(train_file, 'w', encoding='utf-8') as f:
    for item in train_data:
        f.write(json.dumps(item, ensure_ascii=False) + '\n')
with open(eval_file, 'w', encoding='utf-8') as f:
    for item in eval_data:
        f.write(json.dumps(item, ensure_ascii=False) + '\n')

train_ds = AnimeDataset(train_file, tok, cfg.label2id, cfg.max_seq_length)
eval_ds = AnimeDataset(eval_file, tok, cfg.label2id, cfg.max_seq_length)

print(f'Train: {len(train_ds)}, Eval: {len(eval_ds)}')

args = TrainingArguments(
    output_dir='./test_checkpoints' if args_cli.tokenizer == 'regex' else './test_checkpoints_char',
    num_train_epochs=args_cli.epochs,
    per_device_train_batch_size=64,
    per_device_eval_batch_size=64,
    eval_strategy='steps',
    eval_steps=20,
    logging_steps=20,
    save_strategy='no',
    learning_rate=1e-3,
    weight_decay=0.01,
    warmup_steps=50,
    use_cpu=True,
    report_to='none',
    dataloader_num_workers=0,
)

trainer = Trainer(
    model=model,
    args=args,
    train_dataset=train_ds,
    eval_dataset=eval_ds,
    data_collator=DataCollatorForTokenClassification(tok),
    compute_metrics=compute_metrics,
)

print('Starting training...')
trainer.train()

print('Evaluating...')
results = trainer.evaluate()
for k, v in results.items():
    print(f'  {k}: {v:.4f}')

# Save
save_path = './test_checkpoints/final'
if args_cli.tokenizer == 'char':
    save_path = './test_checkpoints_char/final'
trainer.save_model(save_path)
model.config.tokenizer_variant = args_cli.tokenizer
model.config.max_seq_length = cfg.max_seq_length
tok.save_pretrained(save_path)
print(f'Saved to {save_path}')
print('Training test PASSED!')
