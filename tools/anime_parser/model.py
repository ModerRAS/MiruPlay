"""
Tiny BERT model for anime filename token classification.
Uses HuggingFace BertForTokenClassification from scratch (no pretrained weights).
"""

from transformers import BertConfig, BertForTokenClassification
from config import Config


def create_model(config: Config) -> BertForTokenClassification:
    """
    Create a Tiny BERT model for token classification.

    Args:
        config: Config object with model hyperparameters.

    Returns:
        A BertForTokenClassification model initialized from scratch.
    """
    bert_config = BertConfig(
        vocab_size=config.vocab_size,
        hidden_size=config.hidden_size,
        num_hidden_layers=config.num_hidden_layers,
        num_attention_heads=config.num_attention_heads,
        intermediate_size=config.intermediate_size,
        max_position_embeddings=config.max_position_embeddings,
        num_labels=config.num_labels,
        hidden_dropout_prob=config.hidden_dropout_prob,
        attention_probs_dropout_prob=config.attention_probs_dropout_prob,
    )
    model = BertForTokenClassification(bert_config)
    return model


def count_parameters(model) -> int:
    """Count total trainable parameters in a model."""
    return sum(p.numel() for p in model.parameters())


def print_model_summary(model):
    """Print model architecture summary with parameter count."""
    total_params = count_parameters(model)
    trainable_params = sum(p.numel() for p in model.parameters() if p.requires_grad)
    print(f"Total parameters: {total_params:,}")
    print(f"Trainable parameters: {trainable_params:,}")
    print(f"Parameter limit: 5,000,000")
    if total_params < 5_000_000:
        print(f"[OK] Within 5M limit ({(5_000_000 - total_params):,} remaining)")
    else:
        print(f"[FAIL] Exceeds 5M limit by {total_params - 5_000_000:,}")
    return total_params


if __name__ == "__main__":
    cfg = Config()
    # Set a placeholder vocab_size for standalone testing
    cfg.vocab_size = 3000
    model = create_model(cfg)
    print_model_summary(model)
