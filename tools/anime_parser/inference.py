"""
Inference script for anime filename parser.

Loads a trained model and tokenizer, parses anime filenames,
and outputs structured metadata.

Usage:
    python inference.py "[ANi] 葬送的芙莉莲 S2 - 03 [1080P][WEB-DL]"
    python inference.py --input-file filenames.txt --output-file results.jsonl
"""

import argparse
import json
import os
import re
import sys
from typing import Dict, List, Optional

import torch
from transformers import BertForTokenClassification

from config import Config
from tokenizer import AnimeTokenizer


# Chinese number mapping
CN_NUM_MAP: Dict[str, int] = {
    "一": 1, "二": 2, "三": 3, "四": 4, "五": 5,
    "六": 6, "七": 7, "八": 8, "九": 9, "十": 10,
}


def extract_season_number(text: str) -> Optional[int]:
    """
    Extract season number from various season formats.

    Examples:
        "S2" → 2, "Season 2" → 2, "第二季" → 2, "1st Season" → 1
    """
    # Arabic digits
    match = re.search(r'(\d+)', text)
    if match:
        return int(match.group(1))

    # Chinese digits
    for cn, num in CN_NUM_MAP.items():
        if cn in text:
            return num

    return None


def extract_episode_number(text: str) -> Optional[int]:
    """
    Extract episode number from various episode formats.

    Examples:
        "03" → 3, "EP21" → 21, "第7话" → 7, "#01" → 1
    """
    match = re.search(r'(\d+)', text)
    if match:
        return int(match.group(1))
    return None


def extract_resolution(text: str) -> Optional[str]:
    """Extract resolution string (e.g., '1080P', '4K', '1920x1080')."""
    # Strip brackets for matching
    clean = text.strip("[]()【】")
    return clean if clean else None


def postprocess(tokens: List[str], labels: List[str]) -> Dict:
    """
    Convert BIO-labeled tokens into structured metadata.

    Merges consecutive B- / I- tokens of the same entity type,
    then extracts structured fields.
    """
    result: Dict = {
        "title": None,
        "season": None,
        "episode": None,
        "group": None,
        "resolution": None,
        "source": None,
        "special": None,
    }

    # Merge consecutive B- / I- tokens into entities
    entities: List[tuple] = []
    current_entity: Optional[str] = None
    current_tokens: List[str] = []

    for token, label in zip(tokens, labels):
        if label.startswith("B-"):
            # Finalize previous entity
            if current_entity:
                entities.append((current_entity, "".join(current_tokens)))
            current_entity = label[2:]  # Remove "B-"
            current_tokens = [token]
        elif label.startswith("I-"):
            entity_type = label[2:]
            if current_entity == entity_type:
                current_tokens.append(token)
            else:
                # Orphaned I- — start new entity
                if current_entity:
                    entities.append((current_entity, "".join(current_tokens)))
                current_entity = entity_type
                current_tokens = [token]
        else:  # O
            if current_entity:
                entities.append((current_entity, "".join(current_tokens)))
                current_entity = None
                current_tokens = []

    if current_entity:
        entities.append((current_entity, "".join(current_tokens)))

    # Fill result
    for entity_type, text in entities:
        if entity_type == "TITLE":
            result["title"] = result["title"] or text.strip()
            # If we find multiple title fragments, concatenate them
            # (handles "That" + ... + "Time" etc.)
        elif entity_type == "SEASON":
            season_num = extract_season_number(text)
            if season_num is not None:
                # Keep the highest/last season number if multiple
                result["season"] = season_num
        elif entity_type == "EPISODE":
            ep_num = extract_episode_number(text)
            if ep_num is not None:
                if result["episode"] is None:
                    result["episode"] = ep_num
        elif entity_type == "GROUP":
            group = text.strip("[]()【】")
            if result["group"] is None:
                result["group"] = group
        elif entity_type == "SPECIAL":
            special = text.strip("[]()【】")
            result["special"] = special
        elif entity_type == "RESOLUTION":
            res = extract_resolution(text)
            if res:
                result["resolution"] = res
        elif entity_type == "SOURCE":
            src = text.strip("[]()【】")
            result["source"] = src

    # Handle multi-fragment titles: concatenate all TITLE fragments
    # (This is needed because O tokens between words break entity continuity)
    title_fragments = [t for e, t in entities if e == "TITLE"]
    if title_fragments:
        result["title"] = " ".join(f.strip() for f in title_fragments if f.strip())

    return result


def parse_filename(
    filename: str,
    model: BertForTokenClassification,
    tokenizer: AnimeTokenizer,
    id2label: Dict[int, str],
    max_length: int = 64,
) -> Dict:
    """
    Parse an anime filename and extract structured metadata.

    Args:
        filename: Raw anime filename string.
        model: Trained BertForTokenClassification model.
        tokenizer: AnimeTokenizer instance.
        id2label: Mapping from label ID to label string.
        max_length: Maximum sequence length (including special tokens).

    Returns:
        Dict with parsed fields (title, season, episode, etc.).
    """
    # Tokenize
    tokens = tokenizer.tokenize(filename)
    if not tokens:
        return {"title": None, "season": None, "episode": None,
                "group": None, "resolution": None, "source": None,
                "special": None}

    # Convert to input IDs
    input_ids = tokenizer.convert_tokens_to_ids(tokens)

    # Add special tokens
    input_ids = [tokenizer.cls_token_id] + input_ids + [tokenizer.sep_token_id]
    attention_mask = [1] * len(input_ids)

    # Truncate if needed
    if len(input_ids) > max_length:
        input_ids = input_ids[:max_length]
        attention_mask = attention_mask[:max_length]

    # Pad
    pad_len = max_length - len(input_ids)
    if pad_len > 0:
        input_ids += [tokenizer.pad_token_id] * pad_len
        attention_mask += [0] * pad_len

    # Predict
    device = next(model.parameters()).device
    input_tensor = torch.tensor([input_ids], device=device)
    mask_tensor = torch.tensor([attention_mask], device=device)

    with torch.no_grad():
        logits = model(input_ids=input_tensor, attention_mask=mask_tensor).logits
    predictions = torch.argmax(logits, dim=-1)[0]

    # Remove special token predictions
    # Count real tokens used (minus CLS/SEP)
    real_token_count = len(tokens)
    # Truncate real tokens if we had to truncate
    available = min(real_token_count, max_length - 2)
    if available <= 0:
        return {"title": None, "season": None, "episode": None,
                "group": None, "resolution": None, "source": None,
                "special": None}

    pred_labels = predictions[1:1 + available].tolist()
    label_strings = [id2label.get(p, "O") for p in pred_labels]

    # Post-process
    return postprocess(tokens[:available], label_strings)


def main():
    parser = argparse.ArgumentParser(description="Anime filename parser")
    parser.add_argument("filename", nargs="?", type=str, help="Anime filename to parse")
    parser.add_argument("--input-file", type=str, help="File with filenames (one per line)")
    parser.add_argument("--output-file", type=str, help="Output file for results (JSONL)")
    parser.add_argument("--model-dir", type=str, default="./checkpoints/final",
                        help="Path to trained model directory")
    parser.add_argument("--max-length", type=int, default=64,
                        help="Maximum sequence length")
    args = parser.parse_args()

    # Load config
    cfg = Config()

    # Load tokenizer
    print(f"Loading tokenizer from {args.model_dir}...", file=sys.stderr)
    tokenizer = AnimeTokenizer.from_pretrained(args.model_dir)

    # Load model
    print(f"Loading model from {args.model_dir}...", file=sys.stderr)
    model = BertForTokenClassification.from_pretrained(args.model_dir)
    model.eval()

    id2label = cfg.id2label

    # Process filenames
    filenames_to_parse: List[str] = []

    if args.filename:
        filenames_to_parse.append(args.filename)

    if args.input_file:
        with open(args.input_file, 'r', encoding='utf-8') as f:
            filenames_to_parse.extend(line.strip() for line in f if line.strip())

    if not filenames_to_parse:
        # Read from stdin
        filenames_to_parse.extend(sys.stdin.read().strip().splitlines())

    # Parse and output
    results: List[Dict] = []
    for fn in filenames_to_parse:
        if not fn.strip():
            continue
        result = parse_filename(fn, model, tokenizer, id2label, args.max_length)
        result["_input"] = fn
        results.append(result)

        if args.output_file is None:
            print(json.dumps(result, ensure_ascii=False))

    if args.output_file:
        with open(args.output_file, 'w', encoding='utf-8') as f:
            for r in results:
                f.write(json.dumps(r, ensure_ascii=False) + '\n')
        print(f"Results saved to {args.output_file}", file=sys.stderr)


if __name__ == "__main__":
    main()
