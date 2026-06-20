package `is`.xyz.mpv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseMPVViewTest {
    @Test
    fun `loadfile waits until surface is attached`() {
        assertFalse(shouldLoadMpvFileImmediately(surfaceAttached = false))
    }

    @Test
    fun `loadfile can run immediately after surface attach`() {
        assertTrue(shouldLoadMpvFileImmediately(surfaceAttached = true))
    }
}
