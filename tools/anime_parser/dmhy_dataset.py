"""
Export weakly-labeled anime filename samples from a DMHY crawler SQLite DB.

The crawler database is append-only while it runs, so this script snapshots a
high-water mark (`files.id <= last_file_id`) and writes that value to a manifest.
Future exports can pass `--min-id last_file_id + 1` to label only newly crawled
rows.
"""

import argparse
import json
import os
import random
import re
import sqlite3
from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, List, Optional, Sequence

from data_generator import assign_bio, categorize_meta_token
from tokenizer import AnimeTokenizer


VIDEO_EXTENSIONS = {
    ".mkv", ".mp4", ".avi", ".mov", ".wmv", ".flv", ".rmvb",
    ".ts", ".m2ts", ".webm", ".mpg", ".mpeg", ".m4v",
}

NOISE_BRACKETS = {
    "mp4", "mkv", "avi", "webm", "mov", "wmv", "flv", "rmvb", "ts", "m2ts",
    "raw", "raws", "rip", "10bit", "8bit", "hi10p", "ma10p", "ass", "assx2",
    "tc", "sc", "gb", "big5", "cht", "chs", "jpn", "jp", "jap", "eng",
    "繁中", "简中", "繁日", "简日", "日语", "日文", "外挂", "内封", "字幕",
}

SPECIAL_RE = re.compile(r"^(?:ova|oad|sp|movie|the\s*movie|op|ed|pv|cm|ncop|nced|剧场版|劇場版|特别篇|特別篇)$", re.I)
EPISODE_RE = re.compile(r"^(?:[Ee][Pp]?|#)?(\d{1,4})(?:v\d+)?$", re.I)
SEASON_RE = re.compile(r"^(?:[Ss](\d{1,2})|Season\s*(\d{1,2})|第([一二三四五六七八九十\d]+)[季期])$", re.I)
SXE_RE = re.compile(r"^([Ss]\d{1,2})([Ee]\d{1,4})(?:v\d+)?$")
DATE_RE = re.compile(r"^(?:19|20)\d{2}[.\-_年]?(?:0?[1-9]|1[0-2])?[.\-_月]?(?:0?[1-9]|[12]\d|3[01])?日?$")
HASH_RE = re.compile(r"^[A-Fa-f0-9]{8,}$")
DIMENSION_RE = re.compile(r"^\d{3,4}[xX×]\d{3,4}$")
RESOLUTION_RE = re.compile(r"^(?:\d{3,4}[pP]|\d[Kk]|\d{3,4}[xX×]\d{3,4})$")
SOURCE_RE = re.compile(
    r"^(?:WEB[-_ ]?DL|WEB[-_ ]?Rip|BDRip|BluRay|BDMV|DVDRip|DVD|TVRip|HDTV|"
    r"Netflix|NF|AMZN|Baha|CR|ABEMA|DSNP|U[-_ ]?NEXT|Hulu|AT[-_ ]?X|"
    r"x26[45]|h\.?26[45]|HEVC|AVC|AV1|AAC\d*(?:\.\d+)?|AAC|FLAC|MP3|DTS|Opus|"
    r"CHS|CHT|BIG5|GB|JPN?|简[体體]?|繁[体體]?|简日双语|繁日双语|内封|外挂|MSubs?)$",
    re.I,
)
GROUP_HINT_RE = re.compile(
    r"(?:字幕|字幕组|字幕組|sub|subs|raws?|fansub|studio|house|team|project|"
    r"loli|ani|baha|vcb|airota|kiss|dmhy|mabors|lilith|ohys|erai|subsplease)",
    re.I,
)
TRAILING_DECORATION_RE = re.compile(
    r"(?:新番|月番|合集|合輯|全集|完结|完結|检索|檢索|招募|字幕|内封|內封|"
    r"年齡|年龄|限制|版本|版|"
    r"简中|繁中|GB|BIG5|CHS|CHT|JPN?|MP4|MKV|HEVC|AVC|AAC|FLAC|WEB-DL|1080[Pp]|720[Pp])"
)


@dataclass
class ExportStats:
    scanned_rows: int = 0
    video_rows: int = 0
    duplicate_basenames: int = 0
    labeled_samples: int = 0
    skipped_no_episode: int = 0
    skipped_no_title: int = 0
    skipped_too_short: int = 0
    skipped_too_long: int = 0


def normalize_path_basename(filename: str) -> str:
    return re.split(r"[\\/]", filename)[-1].strip()


def strip_video_extension(basename: str) -> tuple[str, str]:
    stem, ext = os.path.splitext(basename)
    return stem.strip(), ext.lower()


def clean_bracket(token: str) -> str:
    return token.strip().strip("[]()【】《》（）").strip()


def cn_number_to_int(text: str) -> Optional[int]:
    if text.isdigit():
        return int(text)
    values = {"一": 1, "二": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9}
    if text == "十":
        return 10
    if text.startswith("十") and len(text) == 2:
        return 10 + values.get(text[1], 0)
    if text.endswith("十") and len(text) == 2:
        return values.get(text[0], 0) * 10
    if "十" in text and len(text) == 3:
        return values.get(text[0], 0) * 10 + values.get(text[2], 0)
    return values.get(text)


def season_number(token: str) -> Optional[int]:
    clean = clean_bracket(token)
    match = SEASON_RE.match(clean)
    if not match:
        return None
    value = next((g for g in match.groups() if g), None)
    if value is None:
        return None
    return cn_number_to_int(value)


def episode_number(token: str) -> Optional[int]:
    clean = clean_bracket(token)
    if season_number(clean) is not None:
        return None
    if DIMENSION_RE.match(clean) or DATE_RE.match(clean) or HASH_RE.match(clean):
        return None
    if re.match(r"^第\d{1,4}[话話集]$", clean):
        return int(re.search(r"\d+", clean).group())
    match = EPISODE_RE.match(clean)
    if not match:
        return None
    number = int(match.group(1))
    if number == 0 or number > 2000:
        return None
    return number


def is_resolution(token: str) -> bool:
    return bool(RESOLUTION_RE.match(clean_bracket(token)))


def is_source(token: str) -> bool:
    clean = clean_bracket(token)
    if not clean:
        return False
    if categorize_meta_token(token) in {"RESOLUTION", "SOURCE"} and (
        is_resolution(clean) or SOURCE_RE.match(clean)
    ):
        return True
    return bool(SOURCE_RE.match(clean))


def is_special(token: str) -> bool:
    return bool(SPECIAL_RE.match(clean_bracket(token)))


def is_noise_bracket(token: str) -> bool:
    clean = clean_bracket(token)
    if not clean:
        return True
    normalized = re.sub(r"[\s._-]+", "", clean).lower()
    if normalized in NOISE_BRACKETS:
        return True
    if DATE_RE.match(clean) or HASH_RE.match(clean):
        return True
    return False


def is_group_bracket(token: str, index: int, tokens: Sequence[str]) -> bool:
    if not (token.startswith("[") or token.startswith("(") or token.startswith("【") or token.startswith("《")):
        return False
    clean = clean_bracket(token)
    if not clean or is_noise_bracket(token):
        return False
    if is_resolution(clean) or is_source(clean) or is_special(clean) or episode_number(clean) is not None:
        return False
    first_content_index = next((i for i, t in enumerate(tokens) if t not in {" ", "-", "_", "|", "~", "～", "."}), 0)
    if index == first_content_index:
        return True
    if index <= first_content_index + 2 and GROUP_HINT_RE.search(clean):
        return True
    return False


def is_title_token(token: str) -> bool:
    if not token.strip():
        return False
    if token in {" ", "-", "_", "|", "~", "～", "."}:
        return False
    clean = clean_bracket(token)
    if not clean:
        return False
    if is_resolution(clean) or is_source(clean) or is_special(clean):
        return False
    if season_number(clean) is not None or episode_number(clean) is not None:
        return False
    if DATE_RE.match(clean) or HASH_RE.match(clean):
        return False
    if (token.startswith("[") or token.startswith("(") or token.startswith("【") or token.startswith("《")) and TRAILING_DECORATION_RE.search(clean):
        return False
    return True


def trim_title_span(tokens: Sequence[str], start: int, end: int) -> tuple[int, int]:
    while start < end and not is_title_token(tokens[start]):
        start += 1
    while end > start and not is_title_token(tokens[end - 1]):
        end -= 1
    while start < end and TRAILING_DECORATION_RE.search(clean_bracket(tokens[end - 1])):
        end -= 1
        while end > start and tokens[end - 1] in {" ", "-", "_", "|", "~", "～", "."}:
            end -= 1
    return start, end


def find_episode_index(tokens: Sequence[str]) -> Optional[int]:
    candidates: list[tuple[int, int]] = []
    for idx, token in enumerate(tokens):
        number = episode_number(token)
        if number is None:
            continue
        score = 0
        clean = clean_bracket(token)
        if re.match(r"^(?:[Ee][Pp]?|#|第)", clean, re.I):
            score += 4
        if token.startswith("[") or token.startswith("(") or token.startswith("【"):
            score += 3
        if idx > 0 and tokens[idx - 1] in {"-", "_", "|"}:
            score += 2
        if idx >= len(tokens) // 2:
            score += 1
        if 1 <= number <= 200:
            score += 1
        candidates.append((score, idx))
    if not candidates:
        return None
    return max(candidates, key=lambda item: (item[0], item[1]))[1]


def label_bracket_contents(token: str, category: str, tokenizer: AnimeTokenizer) -> tuple[List[str], List[str]]:
    inner = clean_bracket(token)
    if not inner:
        return [token], [category]
    open_char = token[0] if token[0] in "[【(《" else ""
    close_char = token[-1] if token[-1] in "]】)》" else ""
    inner_tokens = tokenizer.tokenize(inner)
    tokens: List[str] = []
    cats: List[str] = []
    if open_char:
        tokens.append(open_char)
        cats.append("sep")
    tokens.extend(inner_tokens)
    cats.extend([category] * len(inner_tokens))
    if close_char:
        tokens.append(close_char)
        cats.append("sep")
    return tokens, cats


def expand_tokens_and_categories(
    tokens: Sequence[str],
    categories: Sequence[str],
    tokenizer: AnimeTokenizer,
) -> tuple[List[str], List[str]]:
    expanded_tokens: List[str] = []
    expanded_categories: List[str] = []
    for token, category in zip(tokens, categories):
        clean = clean_bracket(token)
        if category == "season":
            match = SXE_RE.match(clean)
            if match:
                expanded_tokens.extend([match.group(1), match.group(2)])
                expanded_categories.extend(["season", "episode"])
                continue
        if category in {"group", "title"} and (
            token.startswith("[") or token.startswith("(") or token.startswith("【") or token.startswith("《")
        ):
            split_tokens, split_categories = label_bracket_contents(token, category, tokenizer)
            expanded_tokens.extend(split_tokens)
            expanded_categories.extend(split_categories)
            continue
        expanded_tokens.append(token)
        expanded_categories.append(category)
    return expanded_tokens, expanded_categories


def weak_label_filename(filename: str, tokenizer: AnimeTokenizer) -> Optional[dict]:
    tokens = tokenizer.tokenize(filename)
    if not tokens:
        return None

    categories = ["sep" if token in {" ", "-", "_", "|", "~", "～", "."} else "title" for token in tokens]

    for idx, token in enumerate(tokens):
        if is_group_bracket(token, idx, tokens):
            categories[idx] = "group"

    for idx, token in enumerate(tokens):
        if categories[idx] == "group":
            continue
        if is_resolution(token):
            categories[idx] = "resolution"
        elif is_source(token):
            categories[idx] = "source"
        elif is_special(token):
            categories[idx] = "special"
        elif season_number(token) is not None:
            categories[idx] = "season"
        elif is_noise_bracket(token):
            categories[idx] = "sep"

    episode_idx = find_episode_index(tokens)
    if episode_idx is None:
        return None
    categories[episode_idx] = "episode"

    # S01E07 is tokenized as S01 + E07 after tokenizer changes. If an older
    # token slips through, expand_tokens_and_categories will split it.
    clean_episode = clean_bracket(tokens[episode_idx])
    sxe_match = SXE_RE.match(clean_episode)
    if sxe_match:
        categories[episode_idx] = "season"
    elif not any(cat == "season" for cat in categories[:episode_idx]):
        for idx in range(episode_idx - 1, -1, -1):
            if categories[idx] == "sep":
                continue
            clean = clean_bracket(tokens[idx])
            if re.fullmatch(r"[0-9]+", clean) and 1 <= int(clean) <= 20 and not (
                tokens[idx].startswith("[") or tokens[idx].startswith("(") or tokens[idx].startswith("【")
            ):
                categories[idx] = "season"
            break

    title_end = episode_idx
    while title_end > 0 and categories[title_end - 1] in {"season", "sep"}:
        title_end -= 1
    title_start = 0
    while title_start < title_end and categories[title_start] in {"group", "sep", "source", "resolution", "special"}:
        title_start += 1
    title_start, title_end = trim_title_span(tokens, title_start, title_end)
    if title_start >= title_end:
        return None

    for idx, token in enumerate(tokens):
        if title_start <= idx < title_end:
            if categories[idx] not in {"group", "season", "episode", "resolution", "source", "special"}:
                categories[idx] = "title"
        elif categories[idx] == "title":
            categories[idx] = "sep"

    if not any(cat == "title" for cat in categories) or not any(cat == "episode" for cat in categories):
        return None

    labels = assign_bio(tokens, categories)
    if len(tokens) != len(labels):
        return None
    return {"tokens": tokens, "labels": labels}


def iter_db_rows(db_path: Path, min_id: int, max_id: int) -> Iterable[tuple[int, str]]:
    uri = f"file:{db_path}?mode=ro"
    conn = sqlite3.connect(uri, uri=True, timeout=30)
    conn.execute("PRAGMA query_only=ON")
    try:
        query = "SELECT id, filename FROM files WHERE id >= ? AND id <= ? ORDER BY id"
        yield from conn.execute(query, (min_id, max_id))
    finally:
        conn.close()


def export_dataset(args: argparse.Namespace) -> None:
    db_path = Path(args.db)
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True, timeout=30)
    conn.execute("PRAGMA query_only=ON")
    try:
        db_max_id = conn.execute("SELECT MAX(id) FROM files").fetchone()[0] or 0
        max_id = min(args.max_id if args.max_id is not None else db_max_id, db_max_id)
    finally:
        conn.close()

    base_vocab = None
    if args.base_vocab:
        base_tokenizer = AnimeTokenizer(vocab_file=args.base_vocab)
        base_vocab = base_tokenizer.get_vocab()
    tokenizer = AnimeTokenizer()
    stats = ExportStats()
    seen_basenames: set[str] = set()
    token_lists: List[List[str]] = []
    label_counter: Counter[str] = Counter()
    examples: List[dict] = []

    with output_path.open("w", encoding="utf-8") as out:
        for file_id, raw_filename in iter_db_rows(db_path, args.min_id, max_id):
            stats.scanned_rows += 1
            basename = normalize_path_basename(raw_filename)
            stem, ext = strip_video_extension(basename)
            if ext not in VIDEO_EXTENSIONS:
                continue
            stats.video_rows += 1
            if stem in seen_basenames:
                stats.duplicate_basenames += 1
                continue
            seen_basenames.add(stem)
            if len(stem) < args.min_chars:
                stats.skipped_too_short += 1
                continue
            if len(stem) > args.max_chars:
                stats.skipped_too_long += 1
                continue

            sample = weak_label_filename(stem, tokenizer)
            if sample is None:
                # Most failures are no confident episode or no title; keep the
                # manifest aggregate conservative instead of over-classifying.
                stats.skipped_no_episode += 1
                continue

            labels = sample["labels"]
            if not any(label.endswith("TITLE") for label in labels):
                stats.skipped_no_title += 1
                continue
            if not any(label.endswith("EPISODE") for label in labels):
                stats.skipped_no_episode += 1
                continue

            record = {
                "file_id": file_id,
                "filename": stem,
                "tokens": sample["tokens"],
                "labels": labels,
            }
            out.write(json.dumps(record, ensure_ascii=False) + "\n")
            stats.labeled_samples += 1
            token_lists.append(sample["tokens"])
            label_counter.update(labels)
            if len(examples) < args.example_count:
                examples.append(record)
            if args.limit and stats.labeled_samples >= args.limit:
                break

    tokenizer.build_vocab(token_lists, max_size=args.max_vocab_size, base_vocab=base_vocab)
    tokenizer.save_vocabulary(output_path.parent)

    manifest = {
        "created_at": datetime.now(timezone.utc).isoformat(),
        "source_db": str(db_path),
        "output": str(output_path),
        "min_file_id": args.min_id,
        "last_file_id": max_id,
        "db_max_file_id_at_export_start": db_max_id,
        "limit": args.limit,
        "stats": stats.__dict__,
        "label_counts": dict(label_counter),
        "vocab_size": tokenizer.vocab_size,
        "notes": [
            "Rows are a snapshot of files.id <= last_file_id.",
            "Future incremental export can use --min-id last_file_id+1.",
            "Weak labels target GROUP, TITLE, SEASON, and EPISODE; media tags are boundary labels/noise.",
        ],
        "examples": examples,
    }
    manifest_path = output_path.with_suffix(".manifest.json")
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

    print(json.dumps({k: v for k, v in manifest.items() if k != "examples"}, ensure_ascii=False, indent=2))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export weakly-labeled DMHY filename dataset")
    parser.add_argument("--db", default=r"D:\WorkSpace\Python\dmhy-parser\dmhy_anime.db", help="DMHY SQLite database")
    parser.add_argument("--output", default="data/dmhy_weak.jsonl", help="Output JSONL path")
    parser.add_argument("--min-id", type=int, default=1, help="Minimum files.id to export")
    parser.add_argument("--max-id", type=int, default=None, help="Maximum files.id to export; defaults to current DB max")
    parser.add_argument("--limit", type=int, default=None, help="Maximum labeled samples to write")
    parser.add_argument("--min-chars", type=int, default=4, help="Minimum stem length")
    parser.add_argument("--max-chars", type=int, default=180, help="Maximum stem length")
    parser.add_argument("--example-count", type=int, default=20, help="Examples to include in manifest")
    parser.add_argument("--base-vocab", default=None, help="Optional vocab whose IDs should be preserved")
    parser.add_argument("--max-vocab-size", type=int, default=3000, help="Maximum vocab size including special tokens")
    parser.add_argument("--seed", type=int, default=42, help="Random seed")
    return parser.parse_args()


if __name__ == "__main__":
    parsed_args = parse_args()
    random.seed(parsed_args.seed)
    export_dataset(parsed_args)
