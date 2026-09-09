package com.miruplay.tv.measure

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Minimal RIFF/WAVE 16-bit PCM mono reader/writer for offline measurement
 * import (Phase 2: capture on a laptop, deconvolve on the TV).
 */
object WavFile {

    fun write16bitMono(path: String, samples: DoubleArray, sampleRateHz: Int) {
        DataOutputStream(BufferedOutputStream(FileOutputStream(path))).use { out ->
            val dataSize = samples.size * 2
            out.writeInt(0x52494646)               // "RIFF"
            out.writeInt(36 + dataSize)
            out.writeInt(0x57415645)               // "WAVE"
            out.writeInt(0x666D7420)               // "fmt "
            out.writeInt(16)                       // fmt chunk size
            out.writeShort(1)                      // PCM
            out.writeShort(1)                      // mono
            out.writeInt(sampleRateHz)
            out.writeInt(sampleRateHz * 2)         // byte rate
            out.writeShort(2)                      // block align
            out.writeShort(16)                     // bits per sample
            out.writeInt(0x64617461)               // "data"
            out.writeInt(dataSize)
            for (v in samples) {
                val clamped = max(-1.0, min(1.0, v))
                out.writeShort((clamped * 32767.0).roundToInt())
            }
        }
    }

    /** Returns samples in [-1, 1] and the sample rate. Throws on malformed/unsupported input. */
    fun read16bitMono(path: String): Pair<DoubleArray, Int> {
        DataInputStream(BufferedInputStream(FileInputStream(path))).use { input ->
            val riff = input.readInt()
            require(riff == 0x52494646) { "not a RIFF file" }
            input.readInt() // riff size
            val wave = input.readInt()
            require(wave == 0x57415645) { "not a WAVE file" }

            var formatCode = -1
            var channels = -1
            var sampleRate = -1
            var samples: DoubleArray? = null

            while (samples == null) {
                val id = try {
                    input.readInt()
                } catch (_: EOFException) {
                    throw IllegalArgumentException("data chunk not found")
                }
                val size = input.readInt()
                when (id) {
                    0x666D7420 -> { // "fmt "
                        formatCode = input.readShort().toInt()
                        channels = input.readShort().toInt()
                        sampleRate = input.readInt()
                        input.readInt()  // byte rate
                        input.readShort() // block align
                        val bits = input.readShort().toInt()
                        require(formatCode == 1 && bits == 16) { "only 16-bit PCM supported, got fmt=$formatCode bits=$bits" }
                        require(size >= 16) { "unexpected fmt chunk size $size" }
                        repeat(size - 16) { input.readByte() }
                    }
                    0x64617461 -> { // "data"
                        require(channels > 0) { "fmt chunk missing" }
                        val frameCount = size / 2 / channels
                        val out = DoubleArray(frameCount)
                        for (i in 0 until frameCount) {
                            var acc = 0.0
                            for (c in 0 until channels) {
                                acc += input.readShort() / 32768.0
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
}
