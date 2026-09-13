package com.miruplay.tv.topping

import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspChannelRule
import com.miruplay.tv.model.AudioDspChannelTarget
import com.miruplay.tv.model.AudioDspFilterType
import com.miruplay.tv.model.AudioDspPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Byte-level vectors cross-checked against toppingctl (hardware-confirmed):
 * volume raw 60 = -30.0 dB on the DX5 II front panel; the D90 III capture frame
 * `id=0 22 33 20 01 01 91 02 00 00 00 14 00 00 66 77`; Q25 from the vendor
 * app's own -3.0 dB value 0x016A77C4.
 */
class ToppingProtocolTest {

    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02x".format(it) }

    @Test
    fun `crc16 modbus matches known framing`() {
        // frame(0x91, 0x02, 20) with no CRC: checksum bytes must be 00 00.
        val f = ToppingProtocol.frame(0x91, 0x02, 20)
        assertEquals(16, f.size)
        assertEquals("22 33 20 01 01 91 02 00 00 00 14 00 00 66 77 00", hex(f))
        // CRC-16/MODBUS("123456789") == 0x4B37 is the standard check vector.
        assertEquals(0x4B37, ToppingProtocol.crc16Modbus("123456789".toByteArray()))
    }

    @Test
    fun `d90 iii wire framing is report id zero plus 15 byte payload`() {
        val f = ToppingProtocol.frame(0x91, 0x02, 20)
        val wire = ToppingProtocol.wireBytes(ToppingProtocol.D90_III, f)
        assertEquals("00 22 33 20 01 01 91 02 00 00 00 14 00 00 66 77", hex(wire))
        // DX5 II sends the raw 16-byte frame.
        assertEquals(hex(f), hex(ToppingProtocol.wireBytes(ToppingProtocol.DX5_II, f)))
    }

    @Test
    fun `volume encodes half db steps`() {
        val f = ToppingProtocol.volumeFrames(-30.0).single()
        assertEquals(60, ((f[7].toLong() and 0xFF) shl 24) or ((f[8].toLong() and 0xFF) shl 16) or
            ((f[9].toLong() and 0xFF) shl 8) or (f[10].toLong() and 0xFF))
        assertEquals(25, ToppingProtocol.volumeFrames(-25.0, stepDb = 1.0).single()[10].toInt())
    }

    @Test
    fun `q25 round trip and vendor reference value`() {
        assertEquals(-3.0, ToppingProtocol.q25ToDb(0x016A77C4L), 0.01)
        assertEquals(-6.0, ToppingProtocol.q25ToDb(ToppingProtocol.dbToQ25(-6.0)), 0.01)
        assertEquals(-6.0, ToppingProtocol.q25ToDb(ToppingProtocol.dbToQ25(-6.0)), 1e-6)
    }

    @Test
    fun `band frames write ten frames across two channels`() {
        val frames = ToppingProtocol.bandFrames(0, ToppingProtocol.PeqBand(ToppingProtocol.TYPE_PK, 1000, -12.0, 2.0, on = true))
        assertEquals(10, frames.size)
        // Left gain sub 03 = -120 tenths; right channel sub 08.
        assertEquals(0x03, frames[2][6].toInt())
        assertEquals(0x08, frames[7][6].toInt())
        assertEquals(-120, frames[2][10].toInt())
    }

    @Test
    fun `power frame carries computed crc with b4 zero`() {
        val f = ToppingProtocol.powerFrame(on = false)
        assertEquals(0x00, f[4].toInt())
        val expected = ToppingProtocol.crc16Modbus(f.copyOfRange(2, 11))
        assertEquals(expected ushr 8, f[11].toInt() and 0xFF)
        assertEquals(expected and 0xFF, f[12].toInt() and 0xFF)
    }

    @Test
    fun `preset maps to at most ten bands and reports unsupported types`() {
        val preset = AudioDspPreset(
            id = "p", name = "p", preampDb = -6.5f,
            rules = listOf(
                AudioDspChannelRule(
                    target = AudioDspChannelTarget.ALL,
                    bands = listOf(
                        AudioDspBand(AudioDspFilterType.PEAKING, 1000f, -3f, 1.41f),
                        AudioDspBand(AudioDspFilterType.LOW_SHELF, 105f, 5.5f, 0.7f),
                        AudioDspBand(AudioDspFilterType.NOTCH, 60f, 0f, 8f),
                        AudioDspBand(AudioDspFilterType.HIGH_SHELF, 9000f, 1f, 0.7f, enabled = false),
                    ),
                ),
            ),
        )
        val mapping = ToppingProtocol.mapPreset(preset)
        assertEquals(-6.5, mapping.preampDb!!, 0.01)
        // Enabled bands only: PK + LS written, disabled HS skipped, NOTCH reported.
        assertEquals(2, mapping.bands.count { it.on })
        assertEquals(1, mapping.warnings.count { it.contains("notch") })
        // All 11 registers written: 2 enabled + 9 disabled.
        assertEquals(11, mapping.bands.size)
        assertEquals(9, mapping.bands.count { !it.on })
    }

    @Test
    fun `mapping rejects more than ten usable bands`() {
        val bands = (1..12).map { AudioDspBand(AudioDspFilterType.PEAKING, it * 100f, -1f, 1f) }
        val mapping = ToppingProtocol.mapPreset(
            AudioDspPreset(id = "p", name = "p", rules = listOf(AudioDspChannelRule(target = AudioDspChannelTarget.ALL, bands = bands))),
        )
        assertEquals(10, mapping.bands.count { it.on })
        assertTrue(mapping.warnings.any { it.contains("10 段") })
    }
}
