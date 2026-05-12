"""Smoke test for the full training pipeline."""
import json
import os
import torch
from config import Config
from tokenizer import AnimeTokenizer
from model import create_model, count_parameters
from dataset import AnimeDataset

cfg = Config()

# Load tokenizer
tok = AnimeTokenizer(vocab_file='data/vocab.json')
cfg.vocab_size = tok.vocab_size
print(f'Vocab: {tok.vocab_size}, Labels: {cfg.num_labels}')

# Create model
model = create_model(cfg)
total_params = count_parameters(model)
print(f'Model params: {total_params:,} / 5M limit')
assert total_params < 5_000_000, f'Model too large: {total_params:,}'

# Load a tiny dataset
with open('data/synthetic.jsonl', 'r', encoding='utf-8') as f:
    samples = [json.loads(line) for line in f][:100]

temp_file = 'data/test_smoke.jsonl'
with open(temp_file, 'w', encoding='utf-8') as f:
    for s in samples:
        f.write(json.dumps(s, ensure_ascii=False) + '\n')

ds = AnimeDataset(temp_file, tok, cfg.label2id, cfg.max_seq_length)
print(f'Dataset: {len(ds)} samples')
sample = ds[0]
print(f'Input IDs shape: {sample["input_ids"].shape}')
print(f'Labels shape: {sample["labels"].shape}')
print(f'Attention mask shape: {sample["attention_mask"].shape}')

# Forward pass
with torch.no_grad():
    out = model(
        input_ids=sample['input_ids'].unsqueeze(0),
        attention_mask=sample['attention_mask'].unsqueeze(0),
        labels=sample['labels'].unsqueeze(0),
    )
print(f'Loss: {out.loss.item():.4f}')
print(f'Logits shape: {out.logits.shape}')
print()
print('Smoke test PASSED!')
print(f'Model is ready for training: {total_params:,} params < 5M [OK]')
