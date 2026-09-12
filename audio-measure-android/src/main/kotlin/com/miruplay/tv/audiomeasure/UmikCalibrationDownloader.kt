package com.miruplay.tv.audiomeasure

import com.miruplay.tv.measure.MicCalibration
import com.miruplay.tv.repository.MicCalibrationSettings

/**
 * Downloads miniDSP UMIK-1 calibration files by 7-digit serial number.
 * Direct GET needs no token/cookie — the product page's serial form only
 * exists to render the links:
 *  - 0° (unique factory cal):  /scripts/umikcal/umik.php/<sn>.txt
 *  - 90° (auto-generated):     /scripts/umikcal/umik90.php/<sn>_90deg.txt
 * Unknown serials return an HTML error body ("Unable to locate calibration
 * data"), which [isErrorBody] turns into a user-facing failure.
 */
object UmikCalibrationDownloader {

    private const val BASE_URL = "https://www.minidsp.com/scripts/umikcal"

    /** Accepts "700-1234", "700 1234" or "7001234"; digits only, exactly 7. */
    fun normalizeSerial(input: String): String {
        val digits = input.filter { it.isDigit() }
        require(digits.length == 7) { "序列号需要恰好 7 位数字（如 700-1234）" }
        return digits
    }

    fun buildUrl(serial: String, incidence: MicCalibration.Incidence): String {
        val sn = normalizeSerial(serial)
        return when (incidence) {
            MicCalibration.Incidence.ZERO_DEG -> "$BASE_URL/umik.php/$sn.txt"
            MicCalibration.Incidence.NINETY_DEG -> "$BASE_URL/umik90.php/${sn}_90deg.txt"
        }
    }

    fun isErrorBody(text: String): Boolean =
        text.contains("Unable to locate calibration data", ignoreCase = true)

    fun buildSettings(serial: String, incidence: MicCalibration.Incidence, text: String): MicCalibrationSettings {
        val sn = normalizeSerial(serial)
        val label = when (incidence) {
            MicCalibration.Incidence.ZERO_DEG -> "0°"
            MicCalibration.Incidence.NINETY_DEG -> "90°"
        }
        MicCalibration.parse(text) // validate before saving
        return MicCalibrationSettings(
            name = "UMIK-1 $sn $label",
            data = text,
        )
    }
}
