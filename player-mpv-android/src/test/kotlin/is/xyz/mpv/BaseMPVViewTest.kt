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

    @Test
    fun `resume seek is skipped when start position is zero`() {
        assertFalse(shouldApplyPendingStartSeek(startPositionMs = null))
        assertFalse(shouldApplyPendingStartSeek(startPositionMs = 0L))
    }

    @Test
    fun `resume seek is deferred until file load when start position is positive`() {
        assertTrue(shouldApplyPendingStartSeek(startPositionMs = 1L))
        assertTrue(shouldApplyPendingStartSeek(startPositionMs = 30_000L))
    }
}
