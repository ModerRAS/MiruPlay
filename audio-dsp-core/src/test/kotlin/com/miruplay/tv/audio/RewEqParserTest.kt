package com.miruplay.tv.audio

import com.miruplay.tv.model.AudioDspFilterType
import com.miruplay.tv.model.AudioDspChannelTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewEqParserTest {
    @Test
    fun `imports ten point REW filter text with preamp and disabled filters`() {
        val text = buildString {
            appendLine("Preamp: -5.50 dB")
            repeat(10) { index ->
                val frequency = 31 * (index + 1)
                val state = if (index == 4) "OFF" else "ON"
                appendLine("Filter ${index + 1}: $state PK Fc $frequency Hz Gain ${index - 4}.00 dB Q 1.000")
            }
        }

        val result = RewEqParser.parse(text, presetId = "rew-10", presetName = "REW 10 point")

        assertEquals(10, result.importedBandCount)
        assertEquals(-5.5f, result.preset.preampDb)
        assertEquals(AudioDspFilterType.PEAKING, result.preset.rules.single().bands.first().type)
        assertFalse(result.preset.rules.single().bands[4].enabled)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `imports more than twenty filters without truncating`() {
        val text = buildString {
            repeat(40) { index ->
                appendLine("Filter ${index + 1}: ON PK Fc ${20 + index} Hz Gain 0.0 dB Q 1.0")
            }
        }

        val result = RewEqParser.parse(text)

        assertEquals(40, result.importedBandCount)
        assertEquals(40, result.preset.rules.single().bands.size)
    }

    @Test
    fun `imports labeled filter rows from delimited text`() {
        val text = """
            Filter,State,Type,Frequency (Hz),Gain (dB),Q
            1,ON,LS,80,-2.5,0.707
            2,OFF,HP,120,0,0.8
        """.trimIndent()

        val result = RewEqParser.parse(text)

        assertEquals(2, result.importedBandCount)
        assertEquals(AudioDspFilterType.LOW_SHELF, result.preset.rules.single().bands[0].type)
        assertEquals(80f, result.preset.rules.single().bands[0].frequencyHz)
        assertFalse(result.preset.rules.single().bands[1].enabled)
    }

    @Test
    fun `imports REW generic tabular export and skips empty None rows`() {
        val text = listOf(
            "Generic",
            "Number\tEnabled\tControl\tType\tFrequency(Hz)\tGain(dB)\tQ\tBandwidth(Hz)",
            "1\tTrue\tAuto\tPK\t70.00\t-14.7\t10.398\t6.73",
            "2\tTrue\tAuto\tPK\t71.90\t9.0\t6.993\t10.28",
            "3\tTrue\tManual\tLS\t78.30\t5.7\t\t",
            "8\tTrue\tAuto\tPK\t194.50\t-7.2\t49.155\t3.96",
            "18\tTrue\tAuto\tNone\t",
            "Compound_filters",
            "1\tTrue\tAuto\tNone\t",
        ).joinToString("\n")

        val result = RewEqParser.parse(text)
        val bands = result.preset.rules.single().bands

        assertEquals(4, result.importedBandCount)
        assertEquals(AudioDspFilterType.LOW_SHELF, bands[2].type)
        assertEquals(1f, bands[2].q)
        assertEquals(49.155f, bands[3].q)
    }

    @Test
    fun `generic table filter codes are case insensitive`() {
        val text = """
            Number,Enabled,Control,Type,Frequency(Hz),Gain(dB),Q
            1,True,Auto,pk,1000,-3,1.2
        """.trimIndent()

        val result = RewEqParser.parse(text)

        assertEquals(1, result.importedBandCount)
        assertEquals(AudioDspFilterType.PEAKING, result.preset.rules.single().bands.single().type)
    }

    @Test
    fun `preamp clamp is reported as an import warning`() {
        val result = RewEqParser.parse(
            "Preamp: -40 dB\nFilter 1: ON PK Fc 1000 Hz Gain -3 dB Q 1",
        )

        assertEquals(-24f, result.preset.preampDb)
        assertTrue(result.warnings.any { it.contains("preamp", ignoreCase = true) })
    }

    @Test
    fun `keeps supported filters and warns about unsupported filter types`() {
        val result = RewEqParser.parse(
            """
            Filter 1: ON PK Fc 100 Hz Gain 3 dB Q 1
            Filter 2: ON AP Fc 200 Hz Gain 0 dB Q 1
            """.trimIndent(),
        )

        assertEquals(1, result.importedBandCount)
        assertTrue(result.warnings.any { it.contains("AP") })
    }

    @Test
    fun `assigns imported filters to the selected channel target`() {
        val result = RewEqParser.parse(
            "Filter 1: ON PK Fc 100 Hz Gain 3 dB Q 1",
            target = AudioDspChannelTarget.LEFT_SURROUND,
        )

        assertEquals(AudioDspChannelTarget.LEFT_SURROUND, result.preset.rules.single().target)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects input without supported filters`() {
        RewEqParser.parse("Room EQ Wizard measurement export")
    }

    @Test
    fun `rejects oversized text before parsing`() {
        val error = runCatching { RewEqParser.parse("x".repeat(1_000_001)) }.exceptionOrNull()

        assertTrue(error?.message?.contains("too large", ignoreCase = true) == true)
    }

    @Test
    fun `rejects oversized req payload before parsing`() {
        val error = runCatching { RewEqParser.parseReq(ByteArray(1_500_001)) }.exceptionOrNull()

        assertTrue(error?.message?.contains("too large", ignoreCase = true) == true)
    }
}
