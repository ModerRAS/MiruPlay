package com.miruplay.tv.webcontrol

import java.net.URLDecoder

object HttpRequestEncoding {
    fun queryParameter(
        rawQuery: String?,
        parsedParameters: Map<String, List<String>>,
        name: String,
    ): String =
        rawQuery
            ?.split('&')
            .orEmpty()
            .firstNotNullOfOrNull { pair ->
                val rawName = pair.substringBefore('=')
                if (decodeQueryComponent(rawName) == name) {
                    decodeQueryComponent(pair.substringAfter('=', ""))
                } else {
                    null
                }
            }
            ?: parsedParameters[name]?.firstOrNull().orEmpty()

    fun decodeSegment(segment: String): String =
        URLDecoder.decode(segment, Charsets.UTF_8.name())

    fun utf8BodyCandidates(body: String): List<String> {
        val repaired = runCatching {
            body.toByteArray(Charsets.ISO_8859_1).toString(Charsets.UTF_8)
        }.getOrNull()
        val candidates = if (repaired != null && looksLikeUtf8Mojibake(body, repaired)) {
            listOf(repaired, body)
        } else {
            listOf(body, repaired)
        }
        return candidates
            .filterNotNull()
            .distinct()
    }

    private fun decodeQueryComponent(value: String): String =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)

    private fun looksLikeUtf8Mojibake(value: String, repaired: String): Boolean {
        if (!repaired.containsCjk()) return false
        return value.indexOf('\uFFFD') >= 0 ||
            value.any { it in mojibakeMarkerChars }
    }

    private fun String.containsCjk(): Boolean =
        any { char ->
            val block = Character.UnicodeBlock.of(char)
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
        }

    private val mojibakeMarkerChars = setOf(
        'Ã', 'Â', 'ä', 'å', 'æ', 'ç', 'è', 'é', 'ê', 'ë', 'ì', 'í', 'î', 'ï',
    )
}
