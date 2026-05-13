"""
Synthetic training data generator for anime filename parser.

Generates labeled anime filenames using template filling with content pools.
Each sample is a filename tokenized into tokens with BIO labels.

Output format: JSONL (one JSON object per line)
  {"tokens": [...], "labels": [...]}
"""

import json
import os
import random
import re
from typing import Dict, List, Optional, Tuple

from config import Config
from tokenizer import AnimeTokenizer, create_tokenizer


# ═══════════════════════════════════════════════════════════════
# Content Pools
# ═══════════════════════════════════════════════════════════════

# ---- TITLES (200+ mixed CHS/CHT/EN/JP) ----
TITLES: List[str] = [
    # Chinese (100+)
    "葬送的芙莉莲", "葬送的芙莉蓮", "咒术回战", "咒術迴戰",
    "鬼灭之刃", "鬼滅之刃", "间谍过家家", "SPY×FAMILY",
    "葬送のフリーレン", "进击的巨人", "進擊的巨人",
    "钢之炼金术师", "鋼之煉金術師", "新世纪福音战士",
    "新世纪エヴァンゲリオン", "死亡笔记", "DEATH NOTE",
    "命运石之门", "Steins;Gate", "魔法少女小圆",
    "魔法少女まどか☆マギカ", "反叛的鲁路修", "コードギアス",
    "未闻花名", "あの日見た花の名前を僕達はまだ知らない",
    "Clannad", "Angel Beats!", "輕音少女", "K-ON!",
    "紫罗兰永恒花园", "ヴァイオレット・エヴァーガーデン",
    "来自深渊", "メイドインアビス", "无职转生",
    "無職転生", "转生成史莱姆", "転生したらスライムだった件",
    "关于我转生变成史莱姆这档事", "Re:从零开始的异世界生活",
    "Re:ゼロから始める異世界生活", "辉夜大小姐想让我告白",
    "かぐや様は告らせたい", "我的青春恋爱物语果然有问题",
    "やはり俺の青春ラブコメはまちがっている",
    "刀剑神域", "ソードアート・オンライン",
    "OVERLORD", "为美好的世界献上祝福",
    "この素晴らしい世界に祝福を", "实力至上主义的教室",
    "ようこそ実力至上主義の教室へ", "86-不存在的战区",
    "86-エイティシックス-", "孤独摇滚", "ぼっち・ざ・ろっく",
    "Girls Band Cry", "我心里危险的东西",
    "僕の心のヤバイやつ", "药屋少女的呢喃",
    "薬屋のひとりごと", "迷宫饭", "ダンジョン飯",
    "我推的孩子", "【推しの子】", "葬送的芙莉莲 第二季",
    "死神", "BLEACH", "海贼王", "ONE PIECE",
    "火影忍者", "NARUTO", "猎人", "HUNTER×HUNTER",
    "龙珠", "DRAGON BALL", "灌篮高手", "SLAM DUNK",
    "银魂", "GIN TAMA", "Fate/stay night",
    "Fate/Grand Order", "Fate/Zero", "攻壳机动队",
    "攻殻機動隊", "星际牛仔", "カウボーイビバップ",
    "混沌武士", "サムライチャンプルー", "虫师",
    "蟲師", "三月的狮子", "3月のライオン",
    "昭和元禄落语心中", "昭和元禄落語心中",
    "白箱", "SHIROBAKO", "比宇宙更远的地方",
    "宇宙よりも遠い場所", "摇曳露营", "ゆるキャン△",
    "赛马娘", "ウマ娘", "偶像大师",
    "アイドルマスター", "Love Live!", "lovelive!",
    "BanG Dream!", "少女歌剧", " Revue Starlight",
    "奇蛋物语", "ワンダーエッグ・プライオリティ",
    "莉可丽丝", "リコリス・リコイル", "夏日重现",
    "サマータイムレンダ", "边缘行者", "CYBERPUNK EDGERUNNERS",

    # English/Romanized (50+)
    "Sousou no Frieren", "Jujutsu Kaisen", "Kimetsu no Yaiba",
    "Attack on Titan", "Shingeki no Kyojin", "Fullmetal Alchemist",
    "Neon Genesis Evangelion", "Steins Gate",
    "Puella Magi Madoka Magica", "Code Geass",
    "Violet Evergarden", "Made in Abyss", "Mushoku Tensei",
    "That Time I Got Reincarnated as a Slime",
    "Re Zero Starting Life in Another World",
    "Kaguya-sama Love is War", "Sword Art Online",
    "Konosuba God's Blessing on this Wonderful World",
    "Classroom of the Elite", "Solo Leveling",
    "Bocchi the Rock", "Dungeon Meshi", "Delicious in Dungeon",
    "Oshi no Ko", "My Hero Academia", "Demon Slayer",
    "Chainsaw Man", "Hell's Paradise", "Jigokuraku",
    "Vinland Saga", "Ranking of Kings", "Ousama Ranking",
    "Spy x Family", "Cyberpunk Edgerunners",
    "Lycoris Recoil", "Summer Time Rendering",
    "Wonder Egg Priority", "Odd Taxi",
    "Sonny Boy", "Wonder Egg Priority",
    "Super Cub", "Yuru Camp", "Laid-Back Camp",

    # Numbers in title (20+)
    "86 Eighty Six", "3-gatsu no Lion",
    "5-toubun no Hanayome", "5等分の花嫁",
    "7 Seeds", "7-seeds",
    "91 Days", "91Days",
    "100-man no Inochi no Ue ni Ore wa Tatteiru",
    "100万の命の上に俺は立っている",
    "300-en no Otsuki Samurai",
    "5000兆円欲しい！",
    "2.43 清陰高校男子バレー部",
    "22/7", "24 2",
    "8 Girls", "80万再生",

    # With punctuation (20+)
    "K-ON!", "NEW GAME!", "GO! GO! 575",
    "Wake Up, Girls!", "Show By Rock!!",
    "Hello!! KINMOZA", "Hi☆sCoool! セハガール",
    "AKB0048", "C³", "WIXOSS",
    "√Letter", "√3 (ルートスリー)",
    "DOG DAYS'", "DOG DAYS''",
    "RAIL WARS!", "M3～ソノ黒キ鋼～",
    "D.C.III ~Da Capo III~",
    "B-Project", "Fate/Extra",
    "DIABOLIK LOVERS", "B-PROJECT",
]

# ---- GROUPS (50+) ----
GROUPS_EN_BRACKET: List[str] = [
    "[ANi]", "[Baha]", "[VCB-Studio]", "[Lilith-Raws]",
    "[SubsPlease]", "[Erai-raws]", "[DBD-Raws]", "[AI-Raws]",
    "[Ohys-Raws]", "[Moozzi2]", "[NT-Raws]", "[Ember]",
    "[Judas]", "[Leopard-Raws]", "[m.3.3.w]", "[Kagura]",
    "[HorribleSubs]", "[DeadFish]", "[CBM]", "[FFF]",
    "[SSA]", "[C1]", "[WOLF]", "[CKJ]",
    "[Zero-Raws]", "[dHD]", "[UCCUSS]", "[Tk]",
    "[ReinForce]", "[Kuroi-Raws]", "[Kamigami]", "[DIY]",
    "[QTS]", "[XEI]", "[Snow-Raws]", "[Lv.1]",
    "[NAOKI]", "[Hakata]", "[PHZ]", "[Sakurato]",
    "[YYQ]", "[Beatrice]", "[Rally]", "[SweetSub]",
    "[DHR]", "[HR]", "[Hakugetsu]", "[DMG]",
    "[HYSUB]", "[POPGO]", "[SumiSora]", "[KPDM]",
    "[CASO]", "[KTXP]", "[Snow-Raws]", "[philosophy-raws]",
    "[Coalgirls]", "[Elysium]", "[FFF]", "[B-MXT]", "ANK-Raws",
]

GROUPS_CN_BRACKET: List[str] = [
    "【喵萌奶茶屋】", "【桜都字幕组】", "【幻樱字幕组】",
    "【极影字幕社】", "【动漫国字幕组】", "【澄空学园】",
    "【华盟字幕社】", "【千夏字幕组】", "【铃风字幕组】",
    "【白月字幕组】", "【风之圣殿】", "【诸神字幕组】",
    "【雪飘工作室】", "【茉语月译】", "【爱恋字幕社】",
    "【天月动工】", "【星空字幕组】", "【蓝调动漫】",
    "【森罗万像】", "【轻之国度】",
]

GROUPS_NO_BRACKET: List[str] = [
    "ANi", "Baha", "Nekomoe kissaten",
    "SubsPlease", "Erai-raws",
    "VCB-Studio", "Moozzi2",
    "HorribleSubs", "DeadFish",
    "Kamigami", "ReinForce",
    "Lilith-Raws", "Ohys-Raws",
]

GROUPS_PAREN: List[str] = [
    "(喵萌奶茶屋)", "(桜都字幕组)", "(幻樱字幕组)",
    "(极影字幕社)", "(动漫国字幕组)", "(澄空学园)",
    "(VCB-Studio)", "(Erai-raws)",
]

# ---- SEASONS (20+ variations) ----
SEASONS: List[str] = [
    "S1", "S2", "S3", "S4", "S5",
    "S01", "S02", "S03", "S04",
    "Season 1", "Season 2", "Season 3",
    "第一季", "第二季", "第三季", "第四季",
    "1st Season", "2nd Season", "3rd Season",
    "Seasons 1", "Seasons 2",
    "S1Season", "S2Season",
]

# ---- EPISODES (15+ variations) ----
EPISODES: List[str] = [f"{i:02d}" for i in range(1, 100)]  # 01-99
EPISODE_PREFIXES: List[str] = [
    "EP", "Ep", "ep", "E",
]
EPISODE_CN: List[str] = [f"第{i}话" for i in range(1, 100)] + [f"第{i}話" for i in range(1, 100)]
EPISODE_HASH: List[str] = [f"#{i:02d}" for i in range(1, 100)]

# ---- META: RESOLUTION ----
RESOLUTIONS: List[str] = [
    "[1080P]", "[1080p]", "[720P]", "[720p]",
    "[4K]", "[2160P]", "[2160p]",
    "[480P]", "[480p]", "[360P]", "[360p]",
    "1080P", "1080p", "720P", "720p",
    "1920x1080", "1280x720", "3840x2160",
]

# ---- META: SOURCE ----
SOURCES: List[str] = [
    "[WEB-DL]", "[WEBDL]", "[BDRip]", "[BDMV]",
    "[DVD]", "[TVRip]", "[CR]", "[Netflix]",
    "[AMZN]", "[Baha]", "[WebRip]",
    "WEB-DL", "BDRip", "Baha",
]

# ---- META: CODEC ----
CODECS: List[str] = [
    "[x265]", "[x264]", "[HEVC]", "[AVC]", "[AV1]",
    "[H264]", "[H265]", "[h264]", "[h265]",
    "x265", "x264", "HEVC",
]

# ---- META: AUDIO ----
AUDIO: List[str] = [
    "[FLAC]", "[AAC]", "[MP3]", "[DTS]",
    "FLAC", "AAC",
]

# ---- META: LANGUAGE ----
LANGUAGES: List[str] = [
    "[CHT]", "[GB]", "[JP]", "[简日双语]",
    "[CHS]", "[BIG5]",
    "CHT", "GB", "JP",
]

# ---- COMBINED META ----
ALL_METAS: List[str] = RESOLUTIONS + SOURCES + CODECS + AUDIO + LANGUAGES
ALL_METAS_BRACKET: List[str] = [m for m in ALL_METAS if m.startswith("[") or m.startswith("【") or m.startswith("(")]

# ---- SPECIAL ----
SPECIALS: List[str] = [
    "[Movie]", "[OVA]", "[OAD]", "[SP]",
    "[剧场版]", "[特別篇]", "[特别篇]", "[NC]",
    "[OP]", "[ED]", "[PV]", "[CM]",
    "Movie", "OVA", "OAD", "SP",
]

# ---- SEPARATORS ----
SEPARATORS: List[str] = [" - ", " ", "_", " | ", "～", "~", "-", " |"]


# ═══════════════════════════════════════════════════════════════
# Templates
# ═══════════════════════════════════════════════════════════════

TEMPLATES: List[str] = [
    # Standard: GROUP + TITLE + SEASON + SEP + EPISODE + META
    "{group} {title} {season} {sep} {episode} {meta1} {meta2}",
    "{group} {title} {season} {episode} {meta1} {meta2} {meta3}",
    "{group} {title} {episode} {meta1} {meta2}",
    "{group} {title} {season} {sep} {episode} {meta1}",

    # No GROUP
    "{title} {season} {sep} {episode} {meta1} {meta2}",
    "{title} {episode} {meta1} {meta2} {meta3}",

    # GROUP at end
    "{title} {season} {episode} {meta1} {group}",

    # META before title
    "{group} {meta1} {meta2} {title} {season} {episode}",

    # Special type
    "{group} {title} {special} {sep} {episode} {meta1}",
    "{group} {title} {special} {meta1} {meta2}",

    # CN bracket GROUP
    "【{group_cn}】{title} {season} {episode} {meta1} {meta2}",
    "【{group_cn}】{title} {episode} {meta1}",

    # CN decorative
    "【{group_cn}】★新番★{title} {episode} {meta1}",

    # Paren GROUP
    "({group_cn_paren}) {title} {season} {episode} {meta1}",

    # No bracket GROUP
    "{group_no_bracket} {title} {season} {sep} {episode} {meta1}",

    # OVA/Movie
    "{group} {title} {special} {meta1} {meta2}",

    # Season with composite episode
    "{group} {title} {season} {sep} {episode} {meta1} {meta2} {meta3} {meta4}",

    # Minimal
    "{title} {episode}",

    # Title first, meta after
    "{title} {sep} {episode} [{meta_bracket}] [{meta_bracket}]",
]


# ═══════════════════════════════════════════════════════════════
# Label mapping
# ═══════════════════════════════════════════════════════════════

LABEL_MAP: Dict[str, str] = {
    "title": "TITLE",
    "season": "SEASON",
    "episode": "EPISODE",
    "group": "GROUP",
    "special": "SPECIAL",
    "resolution": "RESOLUTION",
    "source": "SOURCE",
    "codec": "SOURCE",      # CODEC merged into SOURCE
    "audio": "SOURCE",
    "language": "SOURCE",
    "sep": "O",
    "decoration": "O",
    "noise": "O",
}

# Additional meta tokens to categorize
META_RESOLUTION_TOKENS: List[str] = [
    "1080P", "1080p", "720P", "720p", "4K", "2160P", "2160p",
    "480P", "480p", "360P", "360p",
    "1920x1080", "1280x720", "3840x2160",
]

META_SOURCE_TOKENS: List[str] = [
    "WEB-DL", "WEBDL", "BDRip", "BDMV", "DVD", "TVRip",
    "CR", "Netflix", "AMZN", "Baha", "WebRip",
]

META_CODEC_TOKENS: List[str] = [
    "x265", "x264", "HEVC", "AVC", "AV1", "H264", "H265", "h264", "h265",
]

META_AUDIO_TOKENS: List[str] = [
    "FLAC", "AAC", "MP3", "DTS",
]

META_LANG_TOKENS: List[str] = [
    "CHT", "GB", "JP", "CHS", "BIG5", "简日双语",
]


def categorize_meta_token(token: str) -> str:
    """Determine the entity type for a meta token (resolution/source/etc)."""
    # Strip brackets for matching
    clean = token.strip("[]()【】")
    if clean in META_RESOLUTION_TOKENS:
        return "RESOLUTION"
    if clean in META_SOURCE_TOKENS:
        return "SOURCE"
    if clean in META_CODEC_TOKENS:
        return "SOURCE"  # merged
    if clean in META_AUDIO_TOKENS:
        return "SOURCE"  # merged
    if clean in META_LANG_TOKENS:
        return "SOURCE"  # merged
    return "SOURCE"  # default meta type


def assign_bio(tokens: List[str], token_category: List[str]) -> List[str]:
    """
    Assign BIO labels to tokens based on their categories.

    Handles multi-token entities (TITLE, GROUP) that may span across
    separator tokens (spaces, etc.). For example, "Attack on Titan"
    should have B-TITLE for "Attack", I-TITLE for "on", I-TITLE for "Titan"
    even though there are O-labeled spaces between them.

    Args:
        tokens: List of token strings
        token_category: Category for each token (title, season, episode, etc.)

    Returns:
        List of BIO label strings (B-TITLE, I-TITLE, O, etc.)
    """
    labels: List[str] = []
    active_entity: Optional[str] = None  # tracks the current entity across O tokens

    for token, cat in zip(tokens, token_category):
        entity = LABEL_MAP.get(cat, "O")

        if entity == "O":
            labels.append("O")
            # Don't reset active_entity — allows multi-word entities
            # to span across separator tokens (spaces, punctuation)
        elif entity in ("SEASON", "EPISODE", "SPECIAL", "RESOLUTION", "SOURCE"):
            # Single-token or always-B entities
            labels.append(f"B-{entity}")
            active_entity = None
        else:
            # Multi-token entities (TITLE, GROUP)
            if entity == active_entity:
                labels.append(f"I-{entity}")
            else:
                labels.append(f"B-{entity}")
                active_entity = entity

    return labels


# ═══════════════════════════════════════════════════════════════
# Sample Generation
# ═══════════════════════════════════════════════════════════════

def pick_random(pool: list):
    """Pick a random item from a list."""
    return random.choice(pool)


# ---- Category tracking markers ----
# Using Unicode Private Use Area characters that NEVER appear in anime filenames.
# These are single characters that the tokenizer treats as "Other" → single-char tokens.
# They cannot be merged into bracket content, making them robust markers.
_CAT_PUA_BASE = '\uE100'  # Start of PUA region for category markers
_CAT_MARKER_END_CHAR = '\uE000'  # End marker character
_CAT_INDEX: Dict[str, int] = {
    "title": 0, "season": 1, "episode": 2, "special": 3,
    "group": 4, "resolution": 5, "source": 6, "sep": 7, "decoration": 8,
}
_CAT_FROM_INDEX: Dict[int, str] = {v: k for k, v in _CAT_INDEX.items()}
# Pre-compute marker characters
_CAT_MARKER_CHARS: Dict[str, str] = {
    cat: chr(ord(_CAT_PUA_BASE) + idx)
    for cat, idx in _CAT_INDEX.items()
}


def _cat_marker(category: str) -> str:
    """Get a category start marker character."""
    return _CAT_MARKER_CHARS.get(category, _CAT_MARKER_CHARS["title"])


# Regex to detect bracket-wrapped placeholders: 【{placeholder}】, ({placeholder}), etc.
_BRACKET_WRAP_RE = re.compile(r'([\[（【《\(])\{(\w+)\}([\]）】》\)])')


def generate_template_filled(template: str) -> Tuple[str, Dict[str, str]]:
    """
    Fill a template with random content from pools.

    Returns:
        (filled_string, category_map) where each placeholder's value
        is wrapped with category marker characters for tracking.

    For bracket-wrapped placeholders (e.g., 【{group_cn}】), markers
    are placed OUTSIDE the brackets to prevent marker-bracket merging.
    """
    fields: Dict[str, str] = {}
    marker_placeholders: List[str] = []

    for placeholder in ["group", "group_cn", "group_cn_paren", "group_no_bracket",
                        "title", "season", "episode", "special",
                        "meta1", "meta2", "meta3", "meta4",
                        "sep", "meta_bracket", "decoration"]:
        if "{" + placeholder + "}" not in template:
            continue

        if placeholder == "title":
            val = pick_random(TITLES)
            cat = "title"
        elif placeholder == "season":
            val = pick_random(SEASONS)
            cat = "season"
        elif placeholder == "episode":
            choice = random.random()
            if choice < 0.6:
                val = pick_random(EPISODES)
            elif choice < 0.8:
                prefix = pick_random(EPISODE_PREFIXES)
                val = prefix + pick_random(EPISODES)
            else:
                val = pick_random(EPISODE_CN)
            cat = "episode"
        elif placeholder == "group":
            val = pick_random(GROUPS_EN_BRACKET)
            cat = "group"
        elif placeholder == "group_cn":
            val = pick_random(GROUPS_CN_BRACKET)
            cat = "group"
        elif placeholder == "group_cn_paren":
            val = pick_random(GROUPS_PAREN)
            cat = "group"
        elif placeholder == "group_no_bracket":
            val = pick_random(GROUPS_NO_BRACKET)
            cat = "group"
        elif placeholder == "special":
            val = pick_random(SPECIALS)
            cat = "special"
        elif placeholder.startswith("meta"):
            meta_type = random.random()
            if meta_type < 0.3:
                val = pick_random(RESOLUTIONS)
                cat = "resolution"
            elif meta_type < 0.5:
                val = pick_random(SOURCES)
                cat = "source"
            elif meta_type < 0.65:
                val = pick_random(CODECS)
                cat = "source"
            elif meta_type < 0.8:
                val = pick_random(AUDIO)
                cat = "source"
            else:
                val = pick_random(LANGUAGES)
                cat = "source"
        elif placeholder == "sep":
            val = pick_random(SEPARATORS)
            cat = "sep"
        elif placeholder == "meta_bracket":
            val = pick_random(ALL_METAS_BRACKET)
            clean = val.strip("[]()【】")
            if clean in META_RESOLUTION_TOKENS:
                cat = "resolution"
            elif clean in META_SOURCE_TOKENS:
                cat = "source"
            elif clean in META_CODEC_TOKENS:
                cat = "source"
            elif clean in META_AUDIO_TOKENS:
                cat = "source"
            elif clean in META_LANG_TOKENS:
                cat = "source"
            else:
                cat = "source"
        elif placeholder == "decoration":
            decos = ["★04月新番★", "★07月新番★", "★10月新番★", "★01月新番★",
                     "★2024★", "★2025★", "★2026★",
                     "[完]", "[合集]", "【完结】"]
            val = pick_random(decos)
            cat = "decoration"
        else:
            val = placeholder
            cat = "O"

        fields[placeholder] = cat
        placeholder_slot = "{" + placeholder + "}"

        # Check if placeholder is wrapped in template brackets: 【{x}】, ({x}), etc.
        # If so, place markers OUTSIDE the brackets to prevent merging.
        bracket_match = _BRACKET_WRAP_RE.search(template)
        if bracket_match and bracket_match.group(2) == placeholder:
            open_bracket = bracket_match.group(1)
            close_bracket = bracket_match.group(3)
            replacement = f"{_cat_marker(cat)}{open_bracket}{val}{close_bracket}{_CAT_MARKER_END_CHAR}"
            template = template.replace(
                f"{open_bracket}{placeholder_slot}{close_bracket}",
                replacement,
                1
            )
        else:
            # Normal non-wrapped placeholder
            template = template.replace(
                placeholder_slot,
                f"{_cat_marker(cat)}{val}{_CAT_MARKER_END_CHAR}",
                1
            )

    return template, fields


def generate_sample(tokenizer: AnimeTokenizer, templates: List[str]) -> Dict:
    """
    Generate one labeled training sample.

    Placeholder values are wrapped with category marker tokens
    (e.g., [__title__]value[__/__]) so that assign_token_categories
    can track which token belongs to which category.

    Returns:
        {"tokens": [...], "labels": [...]} where labels are in BIO format.
    """
    template = pick_random(templates)
    filled_text, category_map = generate_template_filled(template)

    # Add noise: random decoration
    if random.random() < 0.05:
        deco = pick_random(["★04月新番★", "★07月新番★", "★10月新番★", "★01月新番★",
                           "[完]", "【完结】", "★2024★", "★2025★"])
        if random.random() < 0.5:
            filled_text = _cat_marker("decoration") + deco + _CAT_MARKER_END_CHAR + filled_text
        else:
            filled_text = filled_text + _cat_marker("decoration") + deco + _CAT_MARKER_END_CHAR

    # Tokenize
    tokens = tokenizer.tokenize(filled_text)
    if not tokens:
        return generate_sample(tokenizer, templates)  # retry on empty

    # Assign categories using marker tokens (also filters out markers)
    filtered_tokens, token_categories = assign_token_categories(tokens, filled_text, category_map)

    # Retry if all tokens were filtered out (shouldn't happen, but safety)
    if not filtered_tokens:
        return generate_sample(tokenizer, templates)

    # Generate BIO labels
    labels = assign_bio(filtered_tokens, token_categories)

    assert len(filtered_tokens) == len(labels), f"Token/label mismatch: {len(filtered_tokens)} vs {len(labels)}"

    return {
        "tokens": filtered_tokens,
        "labels": labels,
    }


def assign_token_categories(
    tokens: List[str],
    filled_text: str,
    category_map: Dict[str, str]
) -> Tuple[List[str], List[str]]:
    """
    Assign categories to tokens using embedded Unicode PUA marker chars.

    Category markers are PUA Unicode chars (\uE100-\uE108) that the tokenizer
    outputs as single-character tokens. They bracket each placeholder's content
    and cannot be merged into bracket content.

    Returns:
        (filtered_tokens, categories) with marker chars removed.
    """
    filtered_tokens: List[str] = []
    categories: List[str] = []
    current_category: Optional[str] = None
    markers_encountered = 0

    for token in tokens:
        # Check for end marker
        if len(token) == 1 and token == _CAT_MARKER_END_CHAR:
            current_category = None
            markers_encountered += 1
            continue

        # Check for category start marker (PUA characters)
        if len(token) == 1 and _CAT_PUA_BASE <= token <= chr(ord(_CAT_PUA_BASE) + 8):
            idx = ord(token) - ord(_CAT_PUA_BASE)
            current_category = _CAT_FROM_INDEX.get(idx, None)
            markers_encountered += 1
            continue

        filtered_tokens.append(token)
        if current_category is not None:
            categories.append(current_category)
        else:
            categories.append(_heuristic_category(token))

    # If no markers were found, use pure heuristics as fallback
    if markers_encountered == 0:
        categories = [_heuristic_category(t) for t in filtered_tokens]

    return filtered_tokens, categories


def _heuristic_category(token: str) -> str:
    """
    Fallback heuristic category assignment for tokens not covered by markers.

    This is used only when a token appears outside the marker system
    (e.g., for the first call before markers are added to the template).
    Kept conservative to avoid mislabeling.
    """
    if token in SEPARATORS or token in " -_|～~.":
        return "sep"

    if token.startswith("[") or token.startswith("(") or token.startswith("【"):
        clean = token.strip("[]()【】")
        # Check group
        if any(g.strip("[]()【】") == clean for g in GROUPS_EN_BRACKET + GROUPS_CN_BRACKET + GROUPS_PAREN):
            return "group"
        # Check special
        if any(s.strip("[]()【】") == clean or s == clean for s in SPECIALS):
            return "special"
        # Otherwise meta
        cat = categorize_meta_token(token)
        return cat.lower()

    # Season — only if exact known patterns
    if re.match(r'^[Ss]\d+$', token) or token.startswith("Season") or "季" in token:
        return "season"

    # Episode — only if strong patterns
    if re.match(r'^[Ee][Pp]?\d{1,3}$', token):   # E01, EP01
        return "episode"
    if re.match(r'^#\d{1,3}$', token):            # #01
        return "episode"
    if re.match(r'^第\d+[话話]$', token):          # 第7话
        return "episode"
    if re.match(r'^\d{1,2}[Vv]\d*$', token):      # 01v2
        return "episode"

    # Meta tokens (without brackets)
    if token in ALL_METAS:
        return "source"
    clean = token.strip("[]()【】")
    if clean in META_RESOLUTION_TOKENS + META_SOURCE_TOKENS + META_CODEC_TOKENS + META_AUDIO_TOKENS + META_LANG_TOKENS:
        return "source"

    # Default: title
    return "title"



# ═══════════════════════════════════════════════════════════════
# Main script
# ═══════════════════════════════════════════════════════════════

def generate_dataset(num_samples: int, tokenizer: AnimeTokenizer, output_path: str):
    """
    Generate a synthetic dataset and save to JSONL.

    Args:
        num_samples: Number of samples to generate
        tokenizer: AnimeTokenizer instance
        output_path: Path to output JSONL file
    """
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    all_token_lists: List[List[str]] = []
    with open(output_path, 'w', encoding='utf-8') as f:
        for i in range(num_samples):
            sample = generate_sample(tokenizer, TEMPLATES)
            f.write(json.dumps(sample, ensure_ascii=False) + '\n')
            all_token_lists.append(sample["tokens"])

            if (i + 1) % 10000 == 0:
                print(f"Generated {i + 1}/{num_samples} samples...")

    print(f"Total samples generated: {num_samples}")
    return all_token_lists


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Generate synthetic anime filename dataset")
    parser.add_argument("--num-samples", type=int, default=100_000,
                        help="Number of samples to generate (default: 100000)")
    parser.add_argument("--output", type=str, default="data/synthetic.jsonl",
                        help="Output path (default: data/synthetic.jsonl)")
    parser.add_argument("--tokenizer", choices=["regex", "char"], default="regex",
                        help="Tokenizer variant used to generate the JSONL data")
    parser.add_argument("--vocab-output", type=str, default=None,
                        help="Vocab path (default: output directory vocab.json or vocab.char.json)")
    parser.add_argument("--seed", type=int, default=42,
                        help="Random seed (default: 42)")
    args = parser.parse_args()

    random.seed(args.seed)

    print(f"Generating {args.num_samples} synthetic samples...")
    print(f"Output: {args.output}")

    tokenizer = create_tokenizer(args.tokenizer)

    token_lists = generate_dataset(args.num_samples, tokenizer, args.output)

    # Build tokenizer vocabulary from generated data
    tokenizer.build_vocab(token_lists)

    # Save tokenizer vocab alongside data
    vocab_path = args.vocab_output or os.path.join(
        os.path.dirname(args.output),
        "vocab.json" if args.tokenizer == "regex" else "vocab.char.json",
    )
    vocab_dir = os.path.dirname(vocab_path) or "."
    os.makedirs(vocab_dir, exist_ok=True)
    with open(vocab_path, "w", encoding="utf-8") as f:
        json.dump(tokenizer.get_vocab(), f, ensure_ascii=False, indent=2)
    print(f"Tokenizer vocab saved to {vocab_path}")
    print(f"Vocab size: {tokenizer.vocab_size}")
