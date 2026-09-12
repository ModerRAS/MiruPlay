package com.miruplay.tv.measure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MicCalibrationTest {

    @Test
    fun `parses plain 2-column cal file with comments and sensitivity line`() {
        val text = """
            # UMIK-1 calibration
            Sensitivity = -13.6 dB @ 1 kHz
            20.0    0.10
            1000.0, 0.50
            20000.0; -2.30
        """.trimIndent()
        val parsed = MicCalibration.parse(text)
        assertEquals(2, parsed.columnCount)
        val cal = parsed.calibration
        assertEquals(3, cal.frequenciesHz.size)
        assertEquals(20.0, cal.frequenciesHz[0], 1e-9)
        assertEquals(0.5, cal.gainDb[1], 1e-9)
    }

    @Test
    fun `parses UMIK 3-column file and selects incidence column`() {
        val text = """
            UMIK-1 8100123 calibration data
            Frequency, 0 deg, 90 deg
            20.00,   0.10,  -0.90
            1000.00, 0.50,  -1.50
            20000.0, -2.30, -4.00
        """.trimIndent()
        val zero = MicCalibration.parse(text, MicCalibration.Incidence.ZERO_DEG).calibration
        val ninety = MicCalibration.parse(text, MicCalibration.Incidence.NINETY_DEG).calibration
        assertEquals(3, zero.frequenciesHz.size)
        assertEquals(0.10, zero.gainDb[0], 1e-9)
        assertEquals(-0.90, ninety.gainDb[0], 1e-9)
        assertEquals(-4.00, ninety.gainDb[2], 1e-9)
    }

    @Test
    fun `dominant column layout wins when a stray level line appears`() {
        val text = """
            20.00, 0.10, -0.90
            1000.00, 0.50, -1.50
            -5.00
            20000.0, -2.30, -4.00
        """.trimIndent()
        val parsed = MicCalibration.parse(text)
        assertEquals(3, parsed.calibration.frequenciesHz.size)
    }

    @Test
    fun `correction subtracts interpolated mic response`() {
        val cal = MicCalibration(
            doubleArrayOf(20.0, 2000.0, 20000.0),
            doubleArrayOf(0.0, 2.0, -4.0),
        )
        val freqs = doubleArrayOf(20.0, 1010.0, 20000.0)
        val corrected = cal.correct(doubleArrayOf(1.0, 3.0, -1.0), freqs)
        assertEquals(1.0, corrected[0], 1e-9)
        assertEquals(2.0, corrected[1], 0.01)
        assertEquals(3.0, corrected[2], 1e-9)
    }

    @Test
    fun `invalid files are rejected`() {
        assertThrows { MicCalibration.parse("no numbers here at all") }
        assertThrows { MicCalibration.parse("20.0 0.1") } // single point
        assertThrows {
            MicCalibration.parse("100.0 1.0\n50.0 0.5") // descending frequency
        }
        assertThrows {
            MicCalibration(
                doubleArrayOf(0.0, 1000.0),
                doubleArrayOf(1.0, 1.0),
            )
        }
    }

    @Test
    fun `parses real UMIK-1 90-degree export shape (quoted metadata, tab separated, CRLF)`() {
        val text = listOf(
            "\"Sens Factor =-0.0123456dB, SERNO: 7001234\r",
            "\"Auto-generated 90-degree calibration file\r",
            "10.054\t-4.1234\r",
            "10.179\t-4.2234\r",
            "10.306\t-4.4569\r",
            "1000.0\t0.5\r",
        ).joinToString("")
        val parsed = MicCalibration.parse(text)
        assertEquals(2, parsed.columnCount)
        val cal = parsed.calibration
        assertEquals(4, cal.frequenciesHz.size)
        assertEquals(10.054, cal.coverageLoHz, 1e-9)
        assertEquals(1000.0, cal.coverageHiHz, 1e-9)
        assertTrue(cal.coverageWarning()!!.contains("校准文件仅覆盖"))
        // quoted metadata line must not become a bogus data point
        assertTrue(cal.frequenciesHz.none { it < 10.0 })
    }

    private fun assertThrows(block: () -> Unit) {
        val threw = try {
            block()
            false
        } catch (_: IllegalArgumentException) {
            true
        }
        assertTrue(threw)
    }
}
