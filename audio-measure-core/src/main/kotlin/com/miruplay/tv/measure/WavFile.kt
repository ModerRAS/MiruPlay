package com.miruplay.tv.measure

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Minimal RIFF/WAVE 16-bit PCM mono reader/writer for offline measurement
 * import (Phase 2: capture on a laptop, deconvolve on the TV).
 *
 * RIFF is LITTLE-ENDIAN; do not use DataOutputStream/DataInputStream (big
 * endian) here — the original implementation byte-swapped every field, which
 * made exports unreadable by standard tools and imports of standard WAVs
 * (REW, recorders) pure garbage, invisible to the self-round-trip test.
 */
object WavFile {

    fun write16bitMono(path: String, samples: DoubleArray, sampleRateHz: Int) {
        BufferedOutputStream(FileOutputStream(path)).use { out ->
            val dataSize = samples.size * 2
            out.write("RIFF".toByteArray())
            out.write(u32Le(36 + dataSize))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(u32Le(16))
            out.write(u16Le(1))                // PCM
            out.write(u16Le(1))                // mono
            out.write(u32Le(sampleRateHz))
            out.write(u32Le(sampleRateHz * 2)) // byte rate
            out.write(u16Le(2))                // block align
            out.write(u16Le(16))               // bits per sample
            out.write("data".toByteArray())
            out.write(u32Le(dataSize))
            for (v in samples) {
                val clamped = max(-1.0, min(1.0, v))
                val s = (clamped * 32767.0).roundToInt()
                out.write(s and 0xFF)
                out.write((s shr 8) and 0xFF)
            }
        }
    }

    private fun u32Le(v: Int): ByteArray =
        byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte())

    private fun u16Le(v: Int): ByteArray = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    /** Returns samples in [-1, 1] and the sample rate. Throws on malformed/unsupported input. */
    fun read16bitMono(path: String): Pair<DoubleArray, Int> =
        DataInputStream(BufferedInputStream(FileInputStream(path))).use { read16bitMono(it) }

    fun read16bitMono(input: java.io.InputStream): Pair<DoubleArray, Int> {
        DataInputStream(BufferedInputStream(input)).use { input ->
            val header = ByteArray(12)
            input.readFully(header)
            val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(bb.get() == 'R'.code.toByte() && bb.get() == 'I'.code.toByte() &&
                bb.get() == 'F'.code.toByte() && bb.get() == 'F'.code.toByte()) { "not a RIFF file" }
            bb.int // riff size
            require(bb.get() == 'W'.code.toByte() && bb.get() == 'A'.code.toByte() &&
                bb.get() == 'V'.code.toByte() && bb.get() == 'E'.code.toByte()) { "not a WAVE file" }

            var formatCode = -1
            var channels = -1
            var sampleRate = -1
            var samples: DoubleArray? = null

            while (samples == null) {
                val chunkHeader = ByteArray(8)
                try {
                    input.readFully(chunkHeader)
                } catch (_: EOFException) {
                    throw IllegalArgumentException("data chunk not found")
                }
                val ch = ByteBuffer.wrap(chunkHeader).order(ByteOrder.LITTLE_ENDIAN)
                val id = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                ch.position(4)
                val size = ch.int
                when (id) {
                    "fmt " -> {
                        val fmt = ByteArray(min(size, 1024))
                        input.readFully(fmt)
                        val fb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        formatCode = fb.short.toInt()
                        channels = fb.short.toInt()
                        sampleRate = fb.int
                        fb.int  // byte rate
                        fb.short // block align
                        val bits = fb.short.toInt()
                        require(formatCode == 1 && bits == 16) { "only 16-bit PCM supported, got fmt=$formatCode bits=$bits" }
                        require(size >= 16) { "unexpected fmt chunk size $size" }
                        repeat(size - fmt.size) { input.readByte() }
                    }
                    "data" -> {
                        require(channels > 0) { "fmt chunk missing" }
                        val raw = ByteArray(size)
                        if (size > 0) input.readFully(raw)
                        val db = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
                        val frameCount = size / 2 / channels
                        val out = DoubleArray(frameCount)
                        for (i in 0 until frameCount) {
                            var acc = 0.0
                            for (c in 0 until channels) {
                                acc += db.short / 32768.0
                            }
                            out[i] = acc / channels
                        }
                        // data chunks are word-aligned
                        if (size % 2 != 0) input.readByte()
                        samples = out
                    }
                    else -> {
                        var skipped = 0
                        while (skipped < size) {
                            val n = input.skipBytes(min(size - skipped, 1 shl 16))
                            if (n == 0) break
                            skipped += n
                        }
                    }
                }
            }
            return samples!! to sampleRate
        }
    }

    /** Parse a full WAV file held in memory (Web upload / SAF import). */
    fun read16bitMono(bytes: ByteArray): Pair<DoubleArray, Int> =
        read16bitMono(java.io.ByteArrayInputStream(bytes))
}
