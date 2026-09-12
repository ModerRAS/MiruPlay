package com.miruplay.tv.measure

/**
 * Microphone calibration (UMIK-1 / REW-style .cal file): the microphone's own
 * magnitude response in dB. The measured room response is room × mic, so the
 * mic curve must be SUBTRACTED before smoothing / target / fitting.
 *
 * Accepted text formats:
 *  - miniDSP UMIK-1 3-column lines: `frequency  0° dB  90° dB` — [Incidence]
 *    picks the column (0° = on-axis default, like REW's calibration selector)
 *  - plain 2-column lines: `frequency  dB`
 * Comment lines (# or //), blanks, and a `Sensitivity …` line are skipped;
 * the calibration's absolute level does not matter because the pipeline
 * re-centers the target level anyway.
 *
 * Whitespace, commas and semicolons are all treated as separators.
 */
class MicCalibration(val frequenciesHz: DoubleArray, val gainDb: DoubleArray) {

    init {
        require(frequenciesHz.size >= 2) { "校准文件至少需要 2 个有效数据点" }
        require(frequenciesHz.size == gainDb.size) { "校准数据列数不匹配" }
        require(frequenciesHz[0] > 0.0) { "校准频率必须为正" }
        for (i in 1 until frequenciesHz.size) {
            require(frequenciesHz[i] > frequenciesHz[i - 1]) { "校准频率必须严格递增" }
        }
    }

    /**
     * Subtract the mic's own response from a measured response (linear
     * interpolation of the calibration curve onto [freqs]; clamped at the ends).
     */
    fun correct(responseDb: DoubleArray, freqs: DoubleArray): DoubleArray = DoubleArray(freqs.size) { i ->
        responseDb[i] - interpolate(freqs[i])
    }

    /** Lowest frequency the calibration file actually covers. */
    val coverageLoHz: Double get() = frequenciesHz.first()

    /** Highest frequency the calibration file covers; above this no correction is applied (flat). */
    val coverageHiHz: Double get() = frequenciesHz.last()

    /** True when the file does not extend into the measurement band (truncated export). */
    fun coverageWarning(): String? {
        val top = coverageHiHz
        return if (top < 5_000.0) {
            "校准文件仅覆盖 %.0f–%.0f Hz，高频段（5 kHz 以上）没有被校准；看起来文件不完整，建议重新下载完整校准".format(coverageLoHz, top)
        } else {
            null
        }
    }

    private fun interpolate(f: Double): Double {
        val last = frequenciesHz.size - 1
        if (f <= frequenciesHz[0]) return gainDb[0]
        if (f >= frequenciesHz[last]) return gainDb[last]
        var lo = 0
        var hi = last
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (frequenciesHz[mid] <= f) lo = mid else hi = mid
        }
        val f0 = frequenciesHz[lo]
        val f1 = frequenciesHz[hi]
        val frac = ((f - f0) / (f1 - f0)).coerceIn(0.0, 1.0)
        return gainDb[lo] * (1.0 - frac) + gainDb[hi] * frac
    }

    enum class Incidence { ZERO_DEG, NINETY_DEG }

    data class Parsed(val calibration: MicCalibration, val columnCount: Int)

    companion object {
        const val MAX_TEXT_CHARS = 200_000
        const val MAX_POINTS = 8_192

        private val NUMBER = Regex("""[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?""")

        /**
         * Parse a calibration file. Lines are grouped by numeric column count
         * (2-column and 3-column files must not be mixed); the dominant layout
         * wins so a stray sensitivity/level line doesn't corrupt the table.
         */
        fun parse(text: String, incidence: Incidence = Incidence.ZERO_DEG): Parsed {
            require(text.length <= MAX_TEXT_CHARS) { "校准文件过大" }
            data class Row(val columns: Int, val freq: Double, val gain: Double)
            val rows = mutableListOf<Row>()
            for (rawLine in text.lineSequence()) {
                val line = rawLine.trim().removeSurrounding("\uFEFF")
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue
                // UMIK metadata lines: "Sens Factor =-0.0123456dB, SERNO: 7001234",
                // "Sensitivity …", "Auto-generated 90-degree calibration file"
                if (line.startsWith('"') || line.startsWith("Sens", ignoreCase = true)) continue
                val numbers = NUMBER.findAll(line).map { it.value.toDouble() }.toList()
                val valid = numbers.size in 2..3 &&
                    numbers[0] in 1e-3..1e6 &&
                    numbers.drop(1).all { kotlin.math.abs(it) < 100.0 }
                if (valid) {
                    val gain = when {
                        numbers.size == 3 -> when (incidence) {
                            Incidence.ZERO_DEG -> numbers[1]
                            Incidence.NINETY_DEG -> numbers[2]
                        }
                        else -> numbers[1]
                    }
                    rows += Row(numbers.size, numbers[0], gain)
                    if (rows.size >= MAX_POINTS) break
                }
            }
            require(rows.size >= 2) { "校准文件没有足够的有效数据点（需要频率 + dB 两列，UMIK 为 频率/0°/90° 三列）" }
            // Keep only the dominant column layout.
            val dominant = rows.groupingBy { it.columns }.eachCount().maxByOrNull { it.value }!!.key
            val picked = rows.filter { it.columns == dominant }
            val calibration = MicCalibration(
                frequenciesHz = picked.map { it.freq }.toDoubleArray(),
                gainDb = picked.map { it.gain }.toDoubleArray(),
            )
            return Parsed(calibration, dominant)
        }
    }
}
