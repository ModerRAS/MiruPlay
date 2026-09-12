package com.miruplay.tv.audiomeasure

import com.miruplay.tv.measure.MicCalibration
import com.miruplay.tv.repository.MicCalibrationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests only — deliberately NO network calls in tests, so we never
 * stress miniDSP's server from CI. The downloader itself was verified once
 * manually against serial 700-1234 (0°: 616 lines, 90°: 617 lines, 10 Hz–20 kHz).
 */
class UmikCalibrationDownloaderTest {

    private val errorSample = "<p style=\"color:red;\">Unable to locate calibration data. " +
        "Please contact miniDSP support.</p>"

    private val calSample = "\"Sens Factor =-0.0123456dB, SERNO: 7001234\"\r\n" +
        "\"Auto-generated 90-degree calibration file\"\r\n" +
        "10.054\t-4.1234\r\n" +
        "10.179\t-4.2234\r\n" +
        "1000.0\t0.5\r\n"

    @Test
    fun `serial normalization accepts dashed spaced and bare forms`() {
        assertEquals("7001234", UmikCalibrationDownloader.normalizeSerial("700-1234"))
        assertEquals("7001234", UmikCalibrationDownloader.normalizeSerial("700 1234"))
        assertEquals("7001234", UmikCalibrationDownloader.normalizeSerial("7001234"))
        for (bad in listOf("", "718", "718-769", "70012346", "abcd-1234")) {
            var threw = false
            try {
                UmikCalibrationDownloader.normalizeSerial(bad)
            } catch (_: IllegalArgumentException) {
                threw = true
            }
            assertTrue("serial '$bad' should be rejected", threw)
        }
    }

    @Test
    fun `url builder matches the official endpoint shapes`() {
        assertEquals(
            "https://www.minidsp.com/scripts/umikcal/umik.php/7001234.txt",
            UmikCalibrationDownloader.buildUrl("700-1234", MicCalibration.Incidence.ZERO_DEG),
        )
        assertEquals(
            "https://www.minidsp.com/scripts/umikcal/umik90.php/7001234_90deg.txt",
            UmikCalibrationDownloader.buildUrl("7001234", MicCalibration.Incidence.NINETY_DEG),
        )
    }

    @Test
    fun `error body is detected without network`() {
        assertTrue(UmikCalibrationDownloader.isErrorBody(errorSample))
        assertTrue(!UmikCalibrationDownloader.isErrorBody(calSample))
    }

    @Test
    fun `settings are built with validated text and a friendly name`() {
        val settings: MicCalibrationSettings =
            UmikCalibrationDownloader.buildSettings("700-1234", MicCalibration.Incidence.NINETY_DEG, calSample)
        assertEquals("UMIK-1 7001234 90°", settings.name)
        // parse must have been validated during build
        val cal = MicCalibration.parse(settings.data).calibration
        assertEquals(3, cal.frequenciesHz.size)
        var threw = false
        try {
            UmikCalibrationDownloader.buildSettings("700-1234", MicCalibration.Incidence.ZERO_DEG, errorSample)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("error body must not pass validation", threw)
    }
}
