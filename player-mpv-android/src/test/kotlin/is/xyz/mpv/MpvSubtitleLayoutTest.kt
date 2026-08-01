package `is`.xyz.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvSubtitleLayoutTest {

    @Test
    fun `subtitle layout normalisation forces ass override so bilingual lines stack`() {
        val override = mpvSubtitleLayoutNormalisationOptions.firstOrNull { it.first == "sub-ass-override" }

        assertNotNull("sub-ass-override must be configured to fix bilingual overlap", override)
        assertEquals("force", override!!.second)
    }

    @Test
    fun `normalisation options are non-empty`() {
        assertTrue(mpvSubtitleLayoutNormalisationOptions.isNotEmpty())
    }
}
