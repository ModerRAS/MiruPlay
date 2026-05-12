"""Verify generated dataset quality."""
import json
from collections import Counter

with open('data/synthetic_small.jsonl', 'r', encoding='utf-8') as f:
    samples = [json.loads(line) for line in f]

print(f'Total samples: {len(samples)}')

# Check a few samples
for i in range(min(5, len(samples))):
    s = samples[i]
    print(f'\nSample {i}:')
    print(f'  Tokens: {s["tokens"]}')
    print(f'  Labels: {s["labels"]}')
    
    assert len(s['tokens']) == len(s['labels']), f'Mismatch: {len(s["tokens"])} != {len(s["labels"])}'
    
    # Check BIO format validity
    for j, label in enumerate(s['labels']):
        if label.startswith('I-'):
            if j == 0:
                print(f'  ERROR: First token is {label}')
            else:
                prev = s['labels'][j-1]
                expected_prefix = 'B-' + label[2:]
                if prev != label and prev != expected_prefix:
                    print(f'  WARN: I- without B- at pos {j}: {prev} -> {label}')

# Label distribution
print('\nLabel distribution:')
all_labels = [l for s in samples for l in s['labels']]
total = len(all_labels)
for label, count in Counter(all_labels).most_common():
    print(f'  {label}: {count} ({count*100/total:.1f}%)')

# Sequence length stats
lengths = [len(s['tokens']) for s in samples]
print(f'\nSequence length: min={min(lengths)}, max={max(lengths)}, avg={sum(lengths)/len(lengths):.1f}')
