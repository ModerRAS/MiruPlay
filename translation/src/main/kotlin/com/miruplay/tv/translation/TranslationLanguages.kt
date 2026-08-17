package com.miruplay.tv.translation

data class TargetLanguage(
    val code: String,
    val displayName: String,
)

val SUPPORTED_TARGET_LANGUAGES: List<TargetLanguage> = listOf(
    TargetLanguage("zh-Hans", "中文"),
    TargetLanguage("en", "英语"),
    TargetLanguage("zh-Hant", "繁體中文"),
    TargetLanguage("ja", "日语"),
    TargetLanguage("ko", "韩语"),
    TargetLanguage("fr", "法语"),
    TargetLanguage("de", "德语"),
    TargetLanguage("es", "西班牙语"),
    TargetLanguage("ru", "俄语"),
    TargetLanguage("pt", "葡萄牙语"),
)

/** Google 翻译语言代码映射：zh-Hans → zh-CN，zh-Hant → zh-TW，其余原样。 */
fun googleLanguageCode(code: String): String =
    when (code) {
        "zh-Hans" -> "zh-CN"
        "zh-Hant" -> "zh-TW"
        else -> code
    }

/** Bing edge 端点直接接受 zh-Hans / zh-Hant。 */
fun bingLanguageCode(code: String): String = code

/** DeepSeek 使用自然语言目标指令（system prompt 只写翻译指令，避免被拒）。 */
fun deepSeekLanguageInstruction(code: String): String =
    when (code) {
        "zh-Hans" -> "简体中文"
        "zh-Hant" -> "繁體中文"
        "en" -> "英语"
        "ja" -> "日语"
        "ko" -> "韩语"
        "fr" -> "法语"
        "de" -> "德语"
        "es" -> "西班牙语"
        "ru" -> "俄语"
        "pt" -> "葡萄牙语"
        else -> code
    }
