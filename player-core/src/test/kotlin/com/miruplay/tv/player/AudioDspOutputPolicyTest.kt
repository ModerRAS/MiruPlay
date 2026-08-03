package com.miruplay.tv.player

import com.miruplay.tv.model.AudioDspConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDspOutputPolicyTest {
    @Test
    fun `disabled dsp retains encoded output policy`() {
        val policy = AudioDspOutputPolicy.forConfig(AudioDspConfig.neutral())

        assertFalse(policy.forcePcm)
        assertTrue(policy.allowOffload)
        assertTrue(policy.allowPassthrough)
        assertTrue(policy.allowTunneling)
    }

    @Test
    fun `enabled dsp forces pcm and disables direct output paths`() {
        val policy = AudioDspOutputPolicy.forConfig(AudioDspConfig(enabled = true))

        assertTrue(policy.forcePcm)
        assertFalse(policy.allowOffload)
        assertFalse(policy.allowPassthrough)
        assertFalse(policy.allowTunneling)
    }
}
