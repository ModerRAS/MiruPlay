package com.miruplay.tv.audio

import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspFilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BiquadDesignerTest {
    @Test
    fun `zero gain peaking band is identity`() {
        val coefficients = BiquadDesigner.design(
            AudioDspBand(type = AudioDspFilterType.PEAKING, frequencyHz = 1_000f, gainDb = 0f, q = 1f),
            48_000,
        )

        assertEquals(1.0, coefficients.magnitudeAt(1_000.0, 48_000.0), 1e-9)
    }

    @Test
    fun `six decibel peaking band raises center frequency`() {
        val coefficients = BiquadDesigner.design(
            AudioDspBand(type = AudioDspFilterType.PEAKING, frequencyHz = 1_000f, gainDb = 6f, q = 1f),
            48_000,
        )

        val db = 20.0 * kotlin.math.log10(coefficients.magnitudeAt(1_000.0, 48_000.0))
        assertTrue(abs(db - 6.0) < 0.1)
    }
}
