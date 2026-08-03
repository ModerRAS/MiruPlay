package `is`.xyz.mpv

import org.junit.Assert.assertEquals
import org.junit.Test

class MpvSubtitleLayoutTest {

    @Test
    fun `subtitle layout leaves ass positioning to libass`() {
        assertEquals(
            emptyList<Pair<String, String>>(),
            mpvSubtitleLayoutNormalisationOptions,
        )
    }
}
