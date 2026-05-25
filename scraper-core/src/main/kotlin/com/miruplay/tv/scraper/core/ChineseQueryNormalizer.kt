package com.miruplay.tv.scraper.core

import com.ibm.icu.text.Transliterator

fun String.toSimplifiedChineseQuery(): String =
    ChineseQueryNormalizer.toSimplified(this)

private object ChineseQueryNormalizer {
    private val traditionalToSimplified = runCatching {
        Transliterator.getInstance("Traditional-Simplified")
    }.getOrNull()

    fun toSimplified(text: String): String =
        traditionalToSimplified?.transliterate(text) ?: text
}
