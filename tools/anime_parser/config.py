"""
Configuration parameters for the anime filename parser pipeline.
All hyperparameters are centralized here for easy tuning.
"""


from dataclasses import dataclass, field


@dataclass
class Config:
    """Central configuration dataclass for all pipeline parameters."""

    # Data
    synthetic_data_size: int = 100_000
    train_split: float = 0.9
    data_file: str = "data/synthetic.jsonl"

    # Model architecture
    hidden_size: int = 256
    num_hidden_layers: int = 4
    num_attention_heads: int = 8
    intermediate_size: int = 1024
    max_position_embeddings: int = 128
    hidden_dropout_prob: float = 0.1
    attention_probs_dropout_prob: float = 0.1

    # Training hyperparameters
    batch_size: int = 64
    learning_rate: float = 1e-3
    num_epochs: int = 8
    weight_decay: float = 0.01
    warmup_steps: int = 500

    # System
    device: str = "cpu"
    num_workers: int = 0
    save_dir: str = "./checkpoints"
    log_interval: int = 100

    # Sequence
    max_seq_length: int = 64

    # Vocabulary (set dynamically from tokenizer)
    vocab_size: int = 3000  # placeholder, overridden after tokenizer vocab is built

    # Special tokens
    pad_token: str = "[PAD]"
    unk_token: str = "[UNK]"
    cls_token: str = "[CLS]"
    sep_token: str = "[SEP]"

    # BIO label scheme (8 entity types + O)
    label2id: dict = None
    id2label: dict = None

    def __post_init__(self):
        if self.label2id is None:
            self.label2id = {
                "O": 0,
                "B-TITLE": 1, "I-TITLE": 2,
                "B-SEASON": 3, "I-SEASON": 4,
                "B-EPISODE": 5, "I-EPISODE": 6,
                "B-SPECIAL": 7, "I-SPECIAL": 8,
                "B-GROUP": 9, "I-GROUP": 10,
                "B-RESOLUTION": 11, "I-RESOLUTION": 12,
                "B-SOURCE": 13, "I-SOURCE": 14,
            }
        if self.id2label is None:
            self.id2label = {v: k for k, v in self.label2id.items()}

    @property
    def num_labels(self) -> int:
        return len(self.label2id)
