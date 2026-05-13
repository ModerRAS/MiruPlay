"""Create a shuffled JSONL training mix from multiple anime parser datasets."""

import argparse
import json
import random
from pathlib import Path
from typing import Iterable


def iter_jsonl(path: Path, limit: int | None = None) -> Iterable[dict]:
    count = 0
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            item = json.loads(line)
            yield {"tokens": item["tokens"], "labels": item["labels"]}
            count += 1
            if limit is not None and count >= limit:
                break


def main() -> None:
    parser = argparse.ArgumentParser(description="Mix synthetic and weakly-labeled DMHY datasets")
    parser.add_argument("--synthetic", default="data/synthetic.jsonl")
    parser.add_argument("--dmhy", default="data/dmhy/dmhy_weak.jsonl")
    parser.add_argument("--output", default="data/dmhy/mixed_train.jsonl")
    parser.add_argument("--synthetic-limit", type=int, default=None)
    parser.add_argument("--dmhy-limit", type=int, default=None)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    random.seed(args.seed)
    records = []
    synthetic_count = 0
    dmhy_count = 0

    for item in iter_jsonl(Path(args.synthetic), args.synthetic_limit):
        records.append(item)
        synthetic_count += 1
    for item in iter_jsonl(Path(args.dmhy), args.dmhy_limit):
        records.append(item)
        dmhy_count += 1

    random.shuffle(records)
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as handle:
        for item in records:
            handle.write(json.dumps(item, ensure_ascii=False) + "\n")

    manifest = {
        "synthetic": args.synthetic,
        "dmhy": args.dmhy,
        "output": args.output,
        "synthetic_count": synthetic_count,
        "dmhy_count": dmhy_count,
        "total_count": len(records),
        "seed": args.seed,
    }
    output_path.with_suffix(".manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
