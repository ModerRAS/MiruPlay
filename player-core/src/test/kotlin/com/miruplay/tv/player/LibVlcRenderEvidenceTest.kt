package com.miruplay.tv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.libvlc.interfaces.IMedia

class LibVlcRenderEvidenceTest {
    @Test
    fun `render evidence reports displayed frames when libvlc has presented pictures`() {
        val evidence = resolveLibVlcRenderEvidenceForTest(
            IMedia.Stats(
                0,
                0f,
                0,
                0f,
                0,
                0,
                18,
                0,
                12,
                1,
                0,
                0,
                0,
                0,
                0f,
            )
        )

        assertNotNull(evidence)
        assertTrue(evidence!!.hasDisplayedFrames)
        assertTrue(evidence.displayedPictures == 12)
        assertTrue(evidence.lostPictures == 1)
    }

    @Test
    fun `render evidence stays negative when decoder has not displayed any picture yet`() {
        val evidence = resolveLibVlcRenderEvidenceForTest(
            IMedia.Stats(
                0,
                0f,
                0,
                0f,
                0,
                0,
                18,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0f,
            )
        )

        assertNotNull(evidence)
        assertFalse(evidence!!.hasDisplayedFrames)
        assertTrue(evidence.decodedVideo == 18)
        assertTrue(evidence.displayedPictures == 0)
    }
}
