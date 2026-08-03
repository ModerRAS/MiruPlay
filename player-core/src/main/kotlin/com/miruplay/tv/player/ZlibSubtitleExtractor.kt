package com.miruplay.tv.player

import androidx.media3.common.Format
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

private const val MAX_INFLATED_SUBTITLE_BYTES = 1024 * 1024

/**
 * Media3 handles Matroska header stripping but not zlib-compressed subtitle samples.
 * This factory keeps the stock extractors and inflates text samples before parsing.
 */
@UnstableApi
internal class ZlibSubtitleExtractorsFactory(
    private val delegate: DefaultExtractorsFactory = DefaultExtractorsFactory()
        .setSubtitleParserFactory(zlibSubtitleParserFactory())
        .experimentalSetTextTrackTranscodingEnabled(true),
) : ExtractorsFactory {

    override fun createExtractors(): Array<Extractor> = delegate.createExtractors()

    override fun experimentalSetTextTrackTranscodingEnabled(enabled: Boolean): ExtractorsFactory {
        delegate.experimentalSetTextTrackTranscodingEnabled(enabled)
        return this
    }

    override fun setSubtitleParserFactory(factory: SubtitleParser.Factory): ExtractorsFactory {
        delegate.setSubtitleParserFactory(ZlibSubtitleParserFactory(factory))
        return this
    }
}

@UnstableApi
internal fun zlibSubtitleParserFactory(
    delegate: SubtitleParser.Factory = DefaultSubtitleParserFactory(),
): SubtitleParser.Factory = ZlibSubtitleParserFactory(delegate)

@UnstableApi
private class ZlibSubtitleParserFactory(
    private val delegate: SubtitleParser.Factory,
) : SubtitleParser.Factory {

    override fun supportsFormat(format: Format): Boolean = delegate.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format): Int =
        delegate.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser =
        ZlibSubtitleParser(delegate.create(format))
}

@UnstableApi
private class ZlibSubtitleParser(
    private val delegate: SubtitleParser,
) : SubtitleParser {
    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<androidx.media3.extractor.text.CuesWithTiming>,
    ) {
        val sample = data.copyOfRange(offset, offset + length)
        val inflated = inflateSubtitleSampleIfNeeded(sample)
        delegate.parse(inflated, 0, inflated.size, outputOptions, output)
    }

    override fun getCueReplacementBehavior(): Int = delegate.getCueReplacementBehavior()

    override fun reset() {
        delegate.reset()
    }
}

internal fun inflateSubtitleSampleIfNeeded(input: ByteArray): ByteArray {
    if (input.size < 2) return input

    for (start in 0..input.lastIndex - 1) {
        if (!isZlibHeader(input, start)) continue
        val inflated = inflateFrom(input, start)
        if (inflated != null) {
            val candidate = input.copyOfRange(0, start) + inflated
            if (looksLikeSubtitleText(candidate)) return candidate
        }

        val escaped = inflateEscapedFrom(input, start) ?: continue
        val escapedCandidate = input.copyOfRange(0, start) + escaped
        if (looksLikeSubtitleText(escapedCandidate)) return escapedCandidate
    }
    return input
}

internal fun findZlibHeader(input: ByteArray): Int {
    if (input.size < 2) return -1
    return (0 until input.size - 1).firstOrNull { isZlibHeader(input, it) } ?: -1
}

private fun isZlibHeader(input: ByteArray, index: Int): Boolean {
    val compressionMethod = input[index].toInt() and 0x0F
    val flags = input[index + 1].toInt() and 0xFF
    val header = ((input[index].toInt() and 0xFF) shl 8) or flags
    return compressionMethod == 8 && header % 31 == 0
}

private fun inflateFrom(input: ByteArray, start: Int): ByteArray? {
    return inflateWith(input, start)
}

private fun inflateEscapedFrom(input: ByteArray, start: Int): ByteArray? {
    if (start + 1 >= input.size || input[start].toInt() and 0xFF != 0x78) return null
    val flagIndex = ZLIB_FLAG_VALUES.indexOfFirst { it == input[start + 1].toInt() and 0xFF }
    if (flagIndex < 0) return null
    val restored = input.copyOf()
    val sentinel = ESCAPE_SENTINELS[flagIndex].toByte()
    for (index in start + 2 until restored.size) {
        if (restored[index] == sentinel) restored[index] = 0
    }
    return inflateWith(restored, start)
}

private fun inflateWith(input: ByteArray, start: Int): ByteArray? {
    val inflater = Inflater()
    return try {
        inflater.setInput(input, start, input.size - start)
        val output = ByteArrayOutputStream(maxOf(32, (input.size - start) * 2))
        val buffer = ByteArray(8 * 1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count > 0) {
                output.write(buffer, 0, count)
                if (output.size() > MAX_INFLATED_SUBTITLE_BYTES) return null
            } else if (inflater.needsDictionary() || inflater.needsInput()) {
                return null
            } else {
                return null
            }
        }
        output.toByteArray()
    } catch (_: DataFormatException) {
        null
    } finally {
        inflater.end()
    }
}

private fun looksLikeSubtitleText(bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    val text = bytes.toString(Charsets.UTF_8)
    if (text.indexOf('\uFFFD') >= 0) return false
    return text.contains("Dialogue:") ||
        text.contains("--> ") ||
        text.contains("WEBVTT") ||
        text.contains("<tt")
}
