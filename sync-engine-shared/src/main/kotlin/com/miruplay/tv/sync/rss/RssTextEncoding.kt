package com.miruplay.tv.sync.rss

import java.net.URLEncoder
import java.security.MessageDigest

object RssTextEncoding {
    fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .toHex()

    fun sha1Hex(value: String): String =
        sha1Hex(value.toByteArray())

    fun queryValue(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
