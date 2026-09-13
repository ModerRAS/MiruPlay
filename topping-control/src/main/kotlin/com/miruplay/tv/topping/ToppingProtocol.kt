package com.miruplay.tv.topping

import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspFilterType
import com.miruplay.tv.model.AudioDspPreset

/**
 * Topping DAC USB HID control protocol, ported clean-room from
 * https://github.com/ModerRAS/toppingctl (MIT) — register map hardware-confirmed
 * on a DX5 II (volume/gain/PEQ/preamp) and a D90 III Discrete (PEQ writes).
 *
 * Frame: 22 33 <op> 01 <b4> <reg> <sub> <int32 BE> <crc16 hi lo> 66 77 00
 * CRC is CRC-16/MODBUS over bytes [2..10], big-endian — only power enforces it.
 */
object ToppingProtocol {

    const val VENDOR_ID = 0x152A          // Thesycon (XMOS stack) — PID does NOT imply Topping
    const val PRODUCT_ID = 0x8750         // shared by many Topping models; match on product string

    const val REG_CTRL = 0x71
    const val SUB_POWER = 0x01
    const val SUB_VOLUME = 0x02
    const val SUB_GAIN = 0x17
    const val SUB_COMMIT = 0x34           // vendor name: Heartbeat

    const val PEQ_FIRST = 0x91
    /** 11 band registers exist; 0x9B is inert (hardware-confirmed 2026-08-24) but still written
     *  so a stale band left by the vendor app gets cleared. */
    const val PEQ_REGISTER_COUNT = 11
    const val REG_PREAMP = 0x9C
    /** 2^25 — Q25 fixed-point scale. */
    private const val PREAMP_SCALE = 33554432L

    // Per-band sub-indices: 01-05 left channel, 06-0a right.
    private const val SUB_TYPE = 1
    private const val SUB_FREQ = 2
    private const val SUB_GAIN_SUB = 3
    private const val SUB_Q = 4
    private const val SUB_ON = 5
    private const val CHANNEL_OFFSET = 5

    /** Device filter type codes (PK / LS / HS are the three confirmed types). */
    const val TYPE_PK = 1
    const val TYPE_LS = 4
    const val TYPE_HS = 5

    /** Volume attenuation range, clamped hard — the one irreversible mistake. */
    const val VOLUME_MIN_DB = -99.0
    const val VOLUME_MAX_DB = 0.0
    /** Above this needs an explicit user confirmation (toppingctl --force parity). */
    const val VOLUME_WARN_DB = -10.0

    const val PREAMP_MIN_DB = -40.0
    const val PREAMP_MAX_DB = 10.0

    /** An unused band as the vendor app writes it: 632 Hz factory default, disabled. */
    val DISABLED_BAND = PeqBand(typeCode = TYPE_PK, freqHz = 632, gainDb = 0.0, q = 0.707, on = false)

    data class PeqBand(
        val typeCode: Int,
        val freqHz: Int,
        val gainDb: Double,
        val q: Double,
        val on: Boolean,
    )

    /** Device entry: register map + framing + which controls this model actually has. */
    data class DeviceSpec(
        val key: String,
        val name: String,
        val productId: Int,
        /** USB product-string fragments that identify THIS model (PIDs collide across models). */
        val productMatch: List<String>,
        /** true only after someone verified writes move the FRONT PANEL on this model. */
        val confirmed: Boolean,
        /** D90 III framing: report id 0 + first 15 bytes; DX5 II sends the raw 16-byte frame. */
        val reportIdPrefix: Boolean,
        /** false when volume is knob/remote only (D90 III: not on the HID interface at all). */
        val hidVolume: Boolean,
    )

    val DX5_II = DeviceSpec(
        key = "dx5ii", name = "Topping DX5 II", productId = 0x8750,
        productMatch = listOf("DX5II", "DX52", "DX5"),
        confirmed = true, reportIdPrefix = false, hidVolume = true,
    )
    val D90_III = DeviceSpec(
        key = "d90iii", name = "Topping D90 III Discrete", productId = 0x8750,
        productMatch = listOf("D90III", "D90IIIDISCRETE", "D90"),
        confirmed = false, reportIdPrefix = true, hidVolume = false,
    )
    val DEVICES = listOf(DX5_II, D90_III)

    /** CRC-16/MODBUS: poly 0xA001 reflected, init 0xFFFF, no final XOR. */
    fun crc16Modbus(data: ByteArray): Int {
        var n = 0xFFFF
        for (b in data) {
            n = n xor (b.toInt() and 0xFF)
            repeat(8) {
                n = if (n and 1 != 0) (n ushr 1) xor 0xA001 else n ushr 1
                n = n and 0xFFFF
            }
        }
        return n
    }

    fun frame(reg: Int, sub: Int, value: Long, opcode: Int = 0x20, b4: Int = 0x01, crc: Boolean = false): ByteArray {
        val v = (value and 0xFFFFFFFFL).toInt()
        val f = byteArrayOf(
            0x22, 0x33, opcode.toByte(), 0x01, b4.toByte(), reg.toByte(), sub.toByte(),
            ((v ushr 24) and 0xFF).toByte(), ((v ushr 16) and 0xFF).toByte(),
            ((v ushr 8) and 0xFF).toByte(), (v and 0xFF).toByte(),
            0, 0, 0x66, 0x77, 0x00,
        )
        if (crc) {
            val c = crc16Modbus(f.copyOfRange(2, 11))
            f[11] = ((c ushr 8) and 0xFF).toByte()   // stored HIGH byte first — reverse of Modbus framing
            f[12] = (c and 0xFF).toByte()
        }
        return f
    }

    /** All 10 frames for one band: five parameters x two channels. */
    fun bandFrames(index: Int, band: PeqBand): List<ByteArray> {
        val reg = PEQ_FIRST + index
        val gainTenths = Math.round(band.gainDb * 10)
        val qScaled = Math.round(band.q * 10_000)
        val out = mutableListOf<ByteArray>()
        for (ch in intArrayOf(0, CHANNEL_OFFSET)) {
            out += frame(reg, SUB_TYPE + ch, band.typeCode.toLong())
            out += frame(reg, SUB_FREQ + ch, band.freqHz.toLong())
            out += frame(reg, SUB_GAIN_SUB + ch, gainTenths)
            out += frame(reg, SUB_Q + ch, qScaled)
            out += frame(reg, SUB_ON + ch, if (band.on) 1L else 0L)
        }
        return out
    }

    /** Both channels, matching the vendor app's 9c 01..04 sequence. */
    fun preampFrames(db: Double): List<ByteArray> {
        val v = dbToQ25(db)
        return listOf(
            frame(REG_PREAMP, 0x01, v),
            frame(REG_PREAMP, 0x02, 1),
            frame(REG_PREAMP, 0x03, v),
            frame(REG_PREAMP, 0x04, 1),
        )
    }

    /** Preamp is LINEAR gain in Q25 fixed point: value = 10^(dB/20) · 2^25. */
    fun dbToQ25(db: Double): Long = Math.round(Math.pow(10.0, db / 20.0) * PREAMP_SCALE)

    fun q25ToDb(v: Long): Double = if (v > 0) 20.0 * Math.log10(v.toDouble() / PREAMP_SCALE) else Double.NEGATIVE_INFINITY
    fun volumeFrames(db: Double, stepDb: Double = 0.5): List<ByteArray> {
        val steps = Math.round(-db / stepDb)
        return listOf(frame(REG_CTRL, SUB_VOLUME, steps))
    }

    fun gainFrame(on: Boolean): ByteArray = frame(REG_CTRL, SUB_GAIN, if (on) 1L else 0L)

    /** Power needs a real checksum (b4=0, inside the CRC — why naive rebuilds fail). */
    fun powerFrame(on: Boolean): ByteArray = frame(REG_CTRL, SUB_POWER, if (on) 1L else 0L, b4 = 0x00, crc = true)

    fun commitFrame(): ByteArray = frame(REG_CTRL, SUB_COMMIT, 1)

    /** Bytes as they go on the wire: D90 III needs report id 0 + 15-byte payload (see toppingctl Device._wire). */
    fun wireBytes(spec: DeviceSpec, f: ByteArray): ByteArray =
        if (spec.reportIdPrefix) byteArrayOf(0x00) + f.copyOfRange(0, 15) else f

    // --- preset mapping -----------------------------------------------------

    data class PeqMapping(val bands: List<PeqBand>, val preampDb: Double?, val warnings: List<String>)

    /**
     * Map an in-app [AudioDspPreset] to device PEQ bands. Only PK / LS / HS are
     * confirmed on the hardware; other filter types become disabled bands and
     * are reported — a silently missing filter yields a wrong curve that still
     * sounds plausible.
     */
    fun mapPreset(preset: AudioDspPreset): PeqMapping {
        val warnings = mutableListOf<String>()
        val source = preset.rules.flatMap { rule -> rule.bands.filter { it.enabled } }
        val usable = mutableListOf<PeqBand>()
        for (band in source) {
            val code = when (band.type) {
                AudioDspFilterType.PEAKING -> TYPE_PK
                AudioDspFilterType.LOW_SHELF -> TYPE_LS
                AudioDspFilterType.HIGH_SHELF -> TYPE_HS
                else -> {
                    warnings += "滤波类型 ${band.type.storageValue} @ ${band.frequencyHz.toInt()} Hz 设备不支持，已跳过"
                    null
                }
            } ?: continue
            if (usable.size >= 10) {
                warnings += "预设超过 10 段，多余的滤波被忽略"
                break
            }
            usable += PeqBand(
                typeCode = code,
                freqHz = band.frequencyHz.toInt(),
                gainDb = band.gainDb.toDouble(),
                q = band.q.toDouble(),
                on = true,
            )
        }
        validate(usable).forEach { warnings += it }
        // All 11 registers are written so nothing stale survives underneath.
        val padded = usable + List(PEQ_REGISTER_COUNT - usable.size) { DISABLED_BAND }
        return PeqMapping(padded, preset.preampDb.takeIf { it != 0f }?.toDouble(), warnings)
    }

    /** Reject nonsense before it reaches the DSP. */
    fun validate(bands: List<PeqBand>): List<String> {
        val errs = mutableListOf<String>()
        for ((i, b) in bands.withIndex()) {
            if (b.typeCode !in setOf(TYPE_PK, TYPE_LS, TYPE_HS)) errs += "滤波 ${i + 1}: 不支持的类型代码 ${b.typeCode}"
            if (b.freqHz !in 10..22_000) errs += "滤波 ${i + 1}: 频率 ${b.freqHz} Hz 超出范围"
            if (b.gainDb !in -40.0..40.0) errs += "滤波 ${i + 1}: 增益 ${b.gainDb} dB 超出范围"
            if (b.q !in 0.01..100.0) errs += "滤波 ${i + 1}: Q ${b.q} 超出范围"
        }
        return errs
    }
}
