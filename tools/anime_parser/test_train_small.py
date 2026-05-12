"""Quick test: train with a small subset to verify the pipeline."""
import json
import os
import sys
import tempfile

from transformers import (
    Trainer, TrainingArguments, DataCollatorForTokenClassification
)

from config import Config
from tokenizer import AnimeTokenizer
from model import create_model, count_parameters
from dataset import AnimeDataset
from train import compute_metrics

cfg = Config()

# Load tokenizer
tok = AnimeTokenizer(vocab_file='data/vocab.json')
cfg.vocab_size = tok.vocab_size

# Create model
model = create_model(cfg)
print(f'Model params: {count_parameters(model):,}')

# Use first 5000 samples
with open('data/synthetic.jsonl', 'r', encoding='utf-8') as f:
    all_data = [json.loads(line) for line in f][:5000]

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
    output_dir='./test_checkpoints',
    num_train_epochs=2,
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
trainer.save_model(save_path)
tok.save_pretrained(save_path)
print(f'Saved to {save_path}')
print('Training test PASSED!')
