package com.miruplay.tv.sync.rss

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import java.io.File

object TorrentMagnetParser {
    fun parse(file: File): Result<String> =
        try {
            parse(file.readBytes())
        } catch (e: Exception) {
            Result.failure(AppError.SyncError.WriteFailed(file.absolutePath, "torrent 解析失败: ${e.message ?: "unknown"}"))
        }

    internal fun parse(bytes: ByteArray): Result<String> =
        try {
            val root = BencodeParser(bytes).parse()
            val rootDict = root as? BValue.Dict
                ?: throw IllegalArgumentException("Not a valid torrent file")
            val info = rootDict.value("info")
                ?: throw IllegalArgumentException("No 'info' dictionary found in torrent")
            val infoDict = info as? BValue.Dict
                ?: throw IllegalArgumentException("'info' is not a dictionary")

            val infoHash = RssTextEncoding.sha1Hex(bytes.copyOfRange(info.start, info.end))
            val name = (infoDict.value("name.utf-8") ?: infoDict.value("name"))
                ?.asString()
                ?.takeIf { it.isNotBlank() }
            val trackers = rootDict.trackers()

            val query = buildList {
                add("xt=urn:btih:$infoHash")
                if (name != null) add("dn=${RssTextEncoding.queryValue(name)}")
                trackers.forEach { tracker -> add("tr=${RssTextEncoding.queryValue(tracker)}") }
            }.joinToString("&")
            Result.success("magnet:?$query")
        } catch (e: Exception) {
            Result.failure(AppError.SyncError.WriteFailed("torrent", "torrent 解析失败: ${e.message ?: "unknown"}"))
        }

    private fun BValue.Dict.trackers(): List<String> {
        val values = linkedSetOf<String>()
        value("announce")?.asString()?.takeIf { it.isNotBlank() }?.let(values::add)
        value("announce-list")?.collectStrings(values)
        return values.toList()
    }

    private fun BValue.collectStrings(target: MutableSet<String>) {
        when (this) {
            is BValue.Bytes -> bytes.toString(Charsets.UTF_8).takeIf { it.isNotBlank() }?.let(target::add)
            is BValue.ListValue -> values.forEach { it.collectStrings(target) }
            is BValue.Dict -> entries.forEach { it.second.collectStrings(target) }
            is BValue.Integer -> Unit
        }
    }

    private fun BValue.asString(): String? =
        (this as? BValue.Bytes)?.bytes?.toString(Charsets.UTF_8)

    private sealed class BValue(open val start: Int, open val end: Int) {
        data class Bytes(val bytes: ByteArray, override val start: Int, override val end: Int) : BValue(start, end)
        data class Integer(val value: Long, override val start: Int, override val end: Int) : BValue(start, end)
        data class ListValue(val values: List<BValue>, override val start: Int, override val end: Int) : BValue(start, end)
        data class Dict(
            val entries: List<Pair<String, BValue>>,
            override val start: Int,
            override val end: Int
        ) : BValue(start, end) {
            fun value(key: String): BValue? = entries.firstOrNull { it.first == key }?.second
        }
    }

    private class BencodeParser(private val data: ByteArray) {
        private var index = 0

        fun parse(): BValue {
            val value = parseValue()
            if (index != data.size) {
                throw IllegalArgumentException("Trailing data at byte $index")
            }
            return value
        }

        private fun parseValue(): BValue {
            if (index >= data.size) throw IllegalArgumentException("Unexpected end of torrent data")
            return when (data[index].toInt().toChar()) {
                'i' -> parseInteger()
                'l' -> parseList()
                'd' -> parseDict()
                in '0'..'9' -> parseBytes()
                else -> throw IllegalArgumentException("Unknown bencode type at byte $index")
            }
        }

        private fun parseInteger(): BValue.Integer {
            val start = index++
            val valueStart = index
            while (index < data.size && data[index].toInt().toChar() != 'e') index++
            if (index >= data.size) throw IllegalArgumentException("Unterminated integer")
            val value = data.decodeToString(valueStart, index).toLong()
            index++
            return BValue.Integer(value, start, index)
        }

        private fun parseList(): BValue.ListValue {
            val start = index++
            val values = mutableListOf<BValue>()
            while (index < data.size && data[index].toInt().toChar() != 'e') {
                values += parseValue()
            }
            if (index >= data.size) throw IllegalArgumentException("Unterminated list")
            index++
            return BValue.ListValue(values, start, index)
        }

        private fun parseDict(): BValue.Dict {
            val start = index++
            val entries = mutableListOf<Pair<String, BValue>>()
            while (index < data.size && data[index].toInt().toChar() != 'e') {
                val key = parseBytes().bytes.toString(Charsets.UTF_8)
                entries += key to parseValue()
            }
            if (index >= data.size) throw IllegalArgumentException("Unterminated dictionary")
            index++
            return BValue.Dict(entries, start, index)
        }

        private fun parseBytes(): BValue.Bytes {
            val start = index
            var length = 0
            var hasDigit = false
            while (index < data.size && data[index].toInt().toChar().isDigit()) {
                hasDigit = true
                length = length * 10 + (data[index].toInt() - '0'.code)
                index++
            }
            if (!hasDigit || index >= data.size || data[index].toInt().toChar() != ':') {
                throw IllegalArgumentException("Invalid byte string at byte $start")
            }
            index++
            val bytesStart = index
            val bytesEnd = bytesStart + length
            if (bytesEnd > data.size) throw IllegalArgumentException("Byte string extends past end of data")
            index = bytesEnd
            return BValue.Bytes(data.copyOfRange(bytesStart, bytesEnd), start, index)
        }
    }
}
