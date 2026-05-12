"""
Custom regex-based tokenizer for anime filenames.

Tokenization pipeline (3 layers):
1. Bracket protection: [...], (...), 【...】, 《...》 are kept as single tokens
2. Format token recognition: S2, 1080P, x265, WEB-DL, etc. are preserved
3. Remainder splitting: separators, Chinese/Japanese char-level, English/number tokens
"""

import re
import json
import os
from typing import Dict, List, Optional, Tuple, Set
from transformers import PreTrainedTokenizer


class AnimeTokenizer(PreTrainedTokenizer):
    """
    Custom regex-based tokenizer for anime filenames.
    Inherits from PreTrainedTokenizer for HuggingFace Trainer compatibility.
    """

    # Required for PreTrainedTokenizer save/load mechanism
    vocab_files_names: Dict[str, str] = {"vocab_file": "vocab.json"}

    # Layer 1: Bracket patterns (kept whole)
    BRACKET_PATTERNS: List[str] = [
        r'\[[^\]]*\]',     # [...]
        r'\([^\)]*\)',     # (...)
        r'【[^】]*】',      # 【...】
        r'《[^》]*》',      # 《...》
    ]

    # Composite format patterns (checked before individual, higher priority)
    COMPOSITE_FORMAT_PATTERNS: List[str] = [
        r'[Ss]\d+[Ee]\d+',   # S01E01
    ]

    # Layer 2: Individual format token patterns
    FORMAT_PATTERNS: List[str] = [
        # Resolution
        r'\d{3,4}[pP]',
        r'\d{3,4}[xX×]\d{3,4}',
        r'\d[Kk]',

        # Codec
        r'[xX]26[45]',
        r'HEVC', r'AVC', r'AV1',
        r'[hH]\.?26[45]',

        # Audio
        r'FLAC', r'AAC', r'MP3', r'DTS', r'Opus',

        # Season
        r'Seasons?\s*\d+',
        r'第[一二三四五六七八九十\d]+季',
        r'\d+[sn][dt]\s+Season',
        r'[Ss]\d+',

        # Episode
        r'[Ee][Pp]?\d+',
        r'#\d+',
        r'第\d+[话話]',
        r'\d+[Vv]\d*',

        # Language
        r'CH[ST]',
        r'简[体體]',
        r'繁[体體]',
        r'JP', r'GB', r'BIG5',
        r'简日双语',

        # Source
        r'WEB[-_]?DL',
        r'BDRip', r'DVDRip', r'TVRip',
        r'Baha', r'Netflix', r'AMZN', r'CR', r'WebRip',

        # Aspect ratio
        r'\d+:\d+',
    ]

    # Layer 3: Separators for splitting
    SEPARATORS: Set[str] = set(' -_|～~.')

    def __init__(self, vocab_file: Optional[str] = None, **kwargs):
        self._vocab: Dict[str, int] = {}
        self._ids_to_tokens: Dict[int, str] = {}

        # Load vocab from file if provided
        if vocab_file is not None and os.path.isfile(vocab_file):
            with open(vocab_file, 'r', encoding='utf-8') as f:
                loaded = json.load(f)
            self._vocab = loaded
            self._ids_to_tokens = {int(v): k for k, v in loaded.items()}

        # Initialize PreTrainedTokenizer with special tokens.
        # Only set defaults for tokens not already provided via kwargs
        # (from_pretrained may pass these through).
        special_kwargs = {}
        for token_name, token_value in [
            ('pad_token', '[PAD]'),
            ('unk_token', '[UNK]'),
            ('cls_token', '[CLS]'),
            ('sep_token', '[SEP]'),
        ]:
            if token_name not in kwargs:
                special_kwargs[token_name] = token_value

        super().__init__(**special_kwargs, **kwargs)

        # Compile regex patterns for efficiency
        self._bracket_re = re.compile('|'.join(self.BRACKET_PATTERNS))
        self._composite_format_re = re.compile('|'.join(self.COMPOSITE_FORMAT_PATTERNS))
        self._format_re = re.compile('|'.join(self.FORMAT_PATTERNS))

    # ---- Properties ----

    @property
    def vocab_size(self) -> int:
        return len(self._vocab)

    # ---- Tokenization (3-layer pipeline) ----

    def tokenize(self, text: str, **kwargs) -> List[str]:
        """
        Tokenize an anime filename into a list of tokens.

        Uses a 3-layer pipeline:
        1. Bracket protection (kept whole)
        2. Format token recognition (composite then individual)
        3. Remainder splitting (separators, char-level for CJK)
        """
        if not text or not text.strip():
            return []

        placeholder_counter = [0]
        placeholders: Dict[int, str] = {}

        def _ph(idx: int) -> str:
            return f'\x00{idx}\x00'

        def _replace_match(m: re.Match) -> str:
            idx = placeholder_counter[0]
            placeholder_counter[0] += 1
            placeholders[idx] = m.group()
            return _ph(idx)

        # Layer 1: Extract bracket content as whole tokens
        processed = self._bracket_re.sub(_replace_match, text)

        # Layer 2a: Composite format patterns (e.g. S01E01 before S01)
        processed = self._composite_format_re.sub(_replace_match, processed)

        # Layer 2b: Individual format tokens
        processed = self._format_re.sub(_replace_match, processed)

        # Layer 3a: Split remainder by separators
        separator_pattern = '|'.join(re.escape(s) for s in sorted(self.SEPARATORS, key=len, reverse=True))
        # Use capturing group to keep separators
        remaining_parts = re.split(f'({separator_pattern})', processed)

        # Layer 3b: Process each part
        result: List[str] = []
        for part in remaining_parts:
            if not part:
                continue

            if part in self.SEPARATORS:
                result.append(part)
            elif '\x00' in part:
                # Extract all placeholder tokens from this part
                # Handles consecutive placeholders like \x001\x00\x002\x00
                ph_pattern = re.compile(r'\x00(\d+)\x00')
                last_end = 0
                for m in ph_pattern.finditer(part):
                    # Add any non-placeholder text before this match
                    if m.start() > last_end:
                        before = part[last_end:m.start()]
                        result.extend(self._split_fragment(before))
                    idx = int(m.group(1))
                    if idx in placeholders:
                        result.append(placeholders[idx])
                    last_end = m.end()
                # Add any remaining text after the last placeholder
                if last_end < len(part):
                    after = part[last_end:]
                    result.extend(self._split_fragment(after))
            else:
                # Split remaining text by character type
                result.extend(self._split_fragment(part))

        return result

    def _split_fragment(self, fragment: str) -> List[str]:
        """
        Split a text fragment by character type:
        - Chinese chars → individual characters
        - Japanese kana → individual characters
        - ASCII letters → whole word
        - Digits → whole number
        - Other → individual characters
        """
        tokens: List[str] = []
        i = 0
        n = len(fragment)

        while i < n:
            ch = fragment[i]

            # Chinese characters (CJK Unified Ideographs + Extension A)
            if '\u4e00' <= ch <= '\u9fff' or '\u3400' <= ch <= '\u4dbf':
                tokens.append(ch)
                i += 1
            # Japanese hiragana
            elif '\u3040' <= ch <= '\u309f':
                tokens.append(ch)
                i += 1
            # Japanese katakana
            elif '\u30a0' <= ch <= '\u30ff':
                tokens.append(ch)
                i += 1
            # ASCII letter sequence (kept whole)
            elif ch.isascii() and ch.isalpha():
                j = i
                while j < n and fragment[j].isascii() and fragment[j].isalpha():
                    j += 1
                tokens.append(fragment[i:j])
                i = j
            # Digit sequence (kept whole)
            elif ch.isdigit():
                j = i
                while j < n and fragment[j].isdigit():
                    j += 1
                tokens.append(fragment[i:j])
                i = j
            else:
                # Other character (punctuation, symbols, etc.)
                tokens.append(ch)
                i += 1

        return tokens

    # ---- Vocabulary Management ----

    def build_vocab(self, tokens_list: List[List[str]]) -> None:
        """
        Build vocabulary from a list of tokenized texts.

        Args:
            tokens_list: List of token lists from tokenize() output.
        """
        freq: Dict[str, int] = {}
        for tokens in tokens_list:
            for token in tokens:
                freq[token] = freq.get(token, 0) + 1

        # Start with special tokens at fixed positions
        vocab: Dict[str, int] = {
            '[PAD]': 0,
            '[UNK]': 1,
            '[CLS]': 2,
            '[SEP]': 3,
        }

        # Add all tokens sorted by frequency descending
        next_id = 4
        for token in sorted(freq, key=lambda t: (-freq[t], t)):
            if token not in vocab:
                vocab[token] = next_id
                next_id += 1

        self._vocab = vocab
        self._ids_to_tokens = {v: k for k, v in vocab.items()}

    # ---- Token-ID Conversion ----

    def _convert_token_to_id(self, token: str) -> int:
        return self._vocab.get(token, self.unk_token_id if self.unk_token_id is not None else 1)

    def _convert_id_to_token(self, index: int) -> str:
        return self._ids_to_tokens.get(index, self.unk_token if self.unk_token else '[UNK]')

    def get_vocab(self) -> Dict[str, int]:
        return dict(self._vocab)

    # ---- Save / Load ----

    def save_vocabulary(self, save_directory: str, filename_prefix: Optional[str] = None) -> Tuple[str]:
        """Save vocabulary to a JSON file. Required by PreTrainedTokenizer."""
        file_path = os.path.join(
            save_directory,
            f"{filename_prefix or ''}vocab.json"
        )
        with open(file_path, 'w', encoding='utf-8') as f:
            json.dump(self._vocab, f, ensure_ascii=False, indent=2)
        return (file_path,)

    # ---- Utility ----

    def __len__(self) -> int:
        return len(self._vocab)

    def __str__(self) -> str:
        return f"AnimeTokenizer(vocab_size={self.vocab_size})"


# Quick test
if __name__ == "__main__":
    tokenizer = AnimeTokenizer()

    test_cases = [
        "[ANi] 葬送的芙莉莲 S2 - 03 [1080P][WEB-DL]",
        "[SubsPlease] Mushoku Tensei - 12 (1080p) [x265][AAC]",
        "【喵萌奶茶屋】★04月新番★[葬送的芙莉莲][01][1080P][HEVC]",
        "Sousou no Frieren S01E01 [BDRip 1920x1080 FLAC]",
        "[VCB-Studio] Girls Band Cry [01][Ma10p_1080p][x265_flac]",
        "86 Eighty Six - 01 [1080P][Baha]",
        "",
        "test",
    ]

    for case in test_cases:
        toks = tokenizer.tokenize(case)
        print(f"Input:  {case}")
        print(f"Tokens: {toks}")
        print()
