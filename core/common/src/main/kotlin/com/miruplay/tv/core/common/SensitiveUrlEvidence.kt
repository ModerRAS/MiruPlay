package com.miruplay.tv.core.common

import java.net.URI
import java.security.MessageDigest

data class SensitiveUrlEvidence(
    val redacted: String,
    val scheme: String,
    val host: String,
    val sha256: String,
)

fun sensitiveUrlEvidence(
    value: String,
    schemeRedactions: Map<String, String> = emptyMap(),
): SensitiveUrlEvidence {
    val trimmed = value.trim()
    val uri = runCatching { URI(trimmed) }.getOrNull()
    val scheme = uri.normalizedScheme(trimmed)
    return SensitiveUrlEvidence(
        redacted = redactSensitiveUrl(trimmed, schemeRedactions = schemeRedactions),
        scheme = scheme,
        host = uri.redactedHost(),
        sha256 = sha256Hex(trimmed),
    )
}

fun redactSensitiveUrl(
    value: String,
    schemeRedactions: Map<String, String> = emptyMap(),
): String {
    val trimmed = value.trim()
    val uri = runCatching { URI(trimmed) }.getOrNull()
    val scheme = uri.normalizedScheme(trimmed).ifBlank { null }
    return when (scheme) {
        "http",
        "https",
        -> {
            val authority = uri.redactedAuthority()
            if (authority.isBlank()) "$scheme://<redacted>/..." else "$scheme://$authority/..."
        }
        null -> "<redacted>"
        else -> schemeRedactions[scheme] ?: "$scheme:<redacted>"
    }
}

fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.trim().toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun URI?.normalizedScheme(raw: String): String =
    this?.scheme?.lowercase()
        ?: raw.substringBefore(':', missingDelimiterValue = "").lowercase()

private fun URI?.redactedAuthority(): String =
    this?.rawAuthority
        ?.substringAfterLast("@")
        ?.takeIf { it.isNotBlank() }
        ?: this?.host?.let { host ->
            if (this.port >= 0) "$host:${this.port}" else host
        }.orEmpty()

private fun URI?.redactedHost(): String =
    this?.host
        ?: this?.rawAuthority
            ?.substringAfterLast("@")
            ?.trim('[', ']')
            ?.substringBefore(":")
            .orEmpty()
