"""
Export the trained anime filename BERT checkpoint to ONNX for Android.

The Android parser pads every filename to a fixed sequence length, so the ONNX
graph is exported with a static [1, max_length] input shape. This keeps mobile
runtime setup simple and predictable.
"""

import argparse
import json
import os
import shutil
import sys
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import torch
from transformers import BertForTokenClassification

from tokenizer import AnimeTokenizer


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")


class TokenClassificationWrapper(torch.nn.Module):
    def __init__(self, model: BertForTokenClassification):
        super().__init__()
        self.model = model

    def forward(self, input_ids: torch.Tensor, attention_mask: torch.Tensor) -> torch.Tensor:
        return self.model(input_ids=input_ids, attention_mask=attention_mask).logits


def encode_sample(tokenizer: AnimeTokenizer, text: str, max_length: int) -> tuple[np.ndarray, np.ndarray]:
    tokens = tokenizer.tokenize(text)
    input_ids = [tokenizer.cls_token_id] + tokenizer.convert_tokens_to_ids(tokens) + [tokenizer.sep_token_id]
    attention_mask = [1] * len(input_ids)

    if len(input_ids) > max_length:
        input_ids = input_ids[:max_length]
        attention_mask = attention_mask[:max_length]

    pad_len = max_length - len(input_ids)
    if pad_len > 0:
        input_ids += [tokenizer.pad_token_id] * pad_len
        attention_mask += [0] * pad_len

    return (
        np.array([input_ids], dtype=np.int64),
        np.array([attention_mask], dtype=np.int64),
    )


def copy_android_assets(model_dir: Path, onnx_path: Path, assets_dir: Path) -> None:
    assets_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(onnx_path, assets_dir / "anime_filename_parser.onnx")
    shutil.copy2(model_dir / "vocab.json", assets_dir / "vocab.json")
    shutil.copy2(model_dir / "config.json", assets_dir / "config.json")


def main() -> None:
    parser = argparse.ArgumentParser(description="Export anime filename parser to ONNX")
    parser.add_argument("--model-dir", default="checkpoints/final", help="HuggingFace checkpoint directory")
    parser.add_argument("--output", default="exports/anime_filename_parser.onnx", help="Output ONNX file")
    parser.add_argument("--max-length", type=int, default=64, help="Fixed sequence length used on Android")
    parser.add_argument(
        "--android-assets-dir",
        help="Optional Android assets directory that receives the ONNX model, vocab, and config",
    )
    parser.add_argument(
        "--sample",
        default="[ANi] 葬送的芙莉莲 S2 - 03 [1080P][WEB-DL]",
        help="Sample filename used for PyTorch/ONNX parity verification",
    )
    args = parser.parse_args()

    model_dir = Path(args.model_dir)
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.with_suffix(output_path.suffix + ".data").unlink(missing_ok=True)

    tokenizer = AnimeTokenizer.from_pretrained(model_dir)
    model = BertForTokenClassification.from_pretrained(model_dir)
    model.eval()

    input_ids_np, attention_mask_np = encode_sample(tokenizer, args.sample, args.max_length)
    input_ids = torch.from_numpy(input_ids_np)
    attention_mask = torch.from_numpy(attention_mask_np)

    wrapper = TokenClassificationWrapper(model).eval()
    with torch.no_grad():
        torch_logits = wrapper(input_ids, attention_mask).detach().cpu().numpy()

    torch.onnx.export(
        wrapper,
        (input_ids, attention_mask),
        output_path,
        input_names=["input_ids", "attention_mask"],
        output_names=["logits"],
        opset_version=18,
        do_constant_folding=True,
        dynamo=True,
        external_data=False,
    )

    onnx_model = onnx.load(output_path)
    onnx.checker.check_model(onnx_model)

    session = ort.InferenceSession(os.fspath(output_path), providers=["CPUExecutionProvider"])
    onnx_logits = session.run(
        ["logits"],
        {
            "input_ids": input_ids_np,
            "attention_mask": attention_mask_np,
        },
    )[0]
    max_diff = float(np.max(np.abs(torch_logits - onnx_logits)))

    metadata = {
        "model_dir": os.fspath(model_dir),
        "output": os.fspath(output_path),
        "max_length": args.max_length,
        "sample": args.sample,
        "logits_shape": list(onnx_logits.shape),
        "max_abs_diff": max_diff,
    }
    metadata_path = output_path.with_suffix(".metadata.json")
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")

    if args.android_assets_dir:
        copy_android_assets(model_dir, output_path, Path(args.android_assets_dir))

    print(json.dumps(metadata, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
