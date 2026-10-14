package com.miruplay.tv.player

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

private const val MAX_CANDIDATE_BYTES = 256 * 1024
private const val MAX_CANDIDATE_TEXT_BYTES = 1024 * 1024
// Valid second bytes for a 0x78 zlib header with FDICT=0 (FCHECK-adjusted). Only four
// exist, each mapping to a sentinel used to stand in for NUL. A stream already holding all
// four sentinels (seen once: LV999 S01E02 @56.41s CN wipe) cannot be protected by this
// equal-length scheme and is left for the malformed-sample path (documented, ~1% residual).
internal val ZLIB_FLAG_VALUES = intArrayOf(0x01, 0x5E, 0x9C, 0xDA)
internal val ESCAPE_SENTINELS = intArrayOf(0xFF, 0xFE, 0xFD, 0xFC)

/** Protects subtitle zlib streams from Media3's null-terminated subtitle handling. */
internal class ZlibSubtitleByteEscaper {
    private val output = ByteArrayOutputStream()
    private var pendingHeaderByte = -1
    private var inflater: Inflater? = null
    private var candidateBytes = ByteArrayOutputStream()
    private var candidateText = ByteArrayOutputStream()

    fun process(input: ByteArray, endOfInput: Boolean = false): ByteArray =
        process(input, 0, input.size, endOfInput)

    fun process(
        input: ByteArray,
        offset: Int,
        length: Int,
        endOfInput: Boolean = false,
    ): ByteArray {
        require(offset >= 0 && length >= 0 && offset <= input.size - length)
        for (index in offset until offset + length) {
            processByte(input[index].toInt() and 0xFF)
        }
        if (endOfInput) finish()
        return output.toByteArray().also { output.reset() }
    }

    fun reset() {
        pendingHeaderByte = -1
        resetCandidate()
        output.reset()
    }

    private fun processByte(value: Int) {
        val activeInflater = inflater
        if (activeInflater != null) {
            candidateBytes.write(value)
            try {
                val oneByte = byteArrayOf(value.toByte())
                activeInflater.setInput(oneByte)
                val buffer = ByteArray(256)
                while (!activeInflater.needsInput() && !activeInflater.finished()) {
                    val count = activeInflater.inflate(buffer)
                    if (count == 0) break
                    candidateText.write(buffer, 0, count)
                }
                when {
                    activeInflater.finished() -> completeCandidate()
                    activeInflater.needsDictionary() ||
                        candidateBytes.size() > MAX_CANDIDATE_BYTES ||
                        candidateText.size() > MAX_CANDIDATE_TEXT_BYTES ->
                        rejectCandidate()
                }
            } catch (_: DataFormatException) {
                rejectCandidate()
            }
            return
        }

        if (pendingHeaderByte >= 0) {
            val first = pendingHeaderByte
            pendingHeaderByte = -1
            if (isZlibHeader(first, value)) {
                startCandidate(first, value)
            } else {
                output.write(first)
                processByte(value)
            }
        } else if (value == 0x78) {
            pendingHeaderByte = value
        } else {
            output.write(value)
        }
    }

    private fun startCandidate(first: Int, second: Int) {
        inflater = Inflater()
        candidateBytes = ByteArrayOutputStream()
        candidateText = ByteArrayOutputStream()
        processCandidateByte(first)
        processCandidateByte(second)
    }

    private fun processCandidateByte(value: Int) {
        val activeInflater = checkNotNull(inflater)
        candidateBytes.write(value)
        try {
            activeInflater.setInput(byteArrayOf(value.toByte()))
            val buffer = ByteArray(256)
            while (!activeInflater.needsInput() && !activeInflater.finished()) {
                val count = activeInflater.inflate(buffer)
                if (count == 0) break
                candidateText.write(buffer, 0, count)
            }
            if (activeInflater.finished()) completeCandidate()
        } catch (_: DataFormatException) {
            rejectCandidate()
        }
    }

    private fun completeCandidate() {
        val bytes = candidateBytes.toByteArray()
        val text = candidateText.toByteArray().toString(Charsets.UTF_8)
        val sentinelIndex = ESCAPE_SENTINELS.indexOfFirst { sentinel ->
            bytes.none { (it.toInt() and 0xFF) == sentinel }
        }
        if (looksLikeSubtitlePayload(text) && sentinelIndex >= 0) {
            bytes[1] = ZLIB_FLAG_VALUES[sentinelIndex].toByte()
            bytes.indices.forEach { index ->
                if (bytes[index].toInt() == 0) bytes[index] = ESCAPE_SENTINELS[sentinelIndex].toByte()
            }
            output.write(bytes)
        } else {
            output.write(bytes)
        }
        resetCandidate()
    }

    private fun rejectCandidate() {
        output.write(candidateBytes.toByteArray())
        resetCandidate()
    }

    private fun finish() {
        if (pendingHeaderByte >= 0) output.write(pendingHeaderByte)
        pendingHeaderByte = -1
        if (inflater != null) rejectCandidate()
    }

    private fun resetCandidate() {
        inflater?.end()
        inflater = null
        candidateBytes.reset()
        candidateText.reset()
    }

    private companion object {
        fun isZlibHeader(first: Int, second: Int): Boolean {
            val header = (first shl 8) or second
            return (first and 0x0F) == 8 && header % 31 == 0
        }

        fun looksLikeSubtitlePayload(text: String): Boolean {
            if (text.contains('\uFFFD')) return false
            val firstLine = text.lineSequence().firstOrNull() ?: return false
            // Head columns (ReadOrder, Layer, Style, ...) are stable under the split
            // limit; the tail varies because karaoke/styled bodies carry commas inside
            // override tags (\pos, \clip, \fad) and Text. Requiring exactly 9 fields
            // silently skipped every styled line (e.g. LV999 OP wipes), leaving their
            // NUL bytes for Media3 to truncate. ponytail: still head-anchored, tail free.
            val fields = firstLine.split(',', limit = 10)
            return fields.size >= 9 &&
                fields[0].toIntOrNull() != null &&
                fields[1].toIntOrNull() != null &&
                fields[2].isNotBlank()
        }
    }
}

internal fun protectSubtitleZlibBytes(input: ByteArray): ByteArray =
    ZlibSubtitleByteEscaper().process(input, endOfInput = true)
