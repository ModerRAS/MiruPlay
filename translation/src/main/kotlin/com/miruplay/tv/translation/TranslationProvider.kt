package com.miruplay.tv.translation

enum class TranslationProvider(val id: String) {
    GOOGLE("google"),
    BING("bing"),
    DEEPSEEK("deepseek");

    companion object {
        fun fromId(id: String): TranslationProvider? = entries.firstOrNull { it.id == id }
    }
}
