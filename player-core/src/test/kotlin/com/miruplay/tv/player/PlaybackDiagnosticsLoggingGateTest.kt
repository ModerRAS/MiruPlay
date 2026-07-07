package com.miruplay.tv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackDiagnosticsLoggingGateTest {
    @Test
    fun `http media loads are logged`() {
        assertTrue(
            shouldLogPlaybackLoad(
                loadEventInfo = LoadEventInfo(
                    1L,
                    DataSpec(Uri.parse("https://example.com/video.mkv")),
                    0L,
                ),
                mediaLoadData = MediaLoadData(C.DATA_TYPE_MEDIA),
            ),
        )
    }

    @Test
    fun `file media loads are ignored`() {
        assertFalse(
            shouldLogPlaybackLoad(
                loadEventInfo = LoadEventInfo(
                    1L,
                    DataSpec(Uri.parse("file:///storage/emulated/0/video.mkv")),
                    0L,
                ),
                mediaLoadData = MediaLoadData(C.DATA_TYPE_MEDIA),
            ),
        )
    }

    @Test
    fun `http non media loads are ignored`() {
        assertFalse(
            shouldLogPlaybackLoad(
                loadEventInfo = LoadEventInfo(
                    1L,
                    DataSpec(Uri.parse("https://example.com/video.mkv")),
                    0L,
                ),
                mediaLoadData = MediaLoadData(C.DATA_TYPE_MANIFEST),
            ),
        )
    }

    @Test
    fun `track type labels stay readable`() {
        assertEquals("video", mediaTrackTypeLabel(C.TRACK_TYPE_VIDEO))
        assertEquals("audio", mediaTrackTypeLabel(C.TRACK_TYPE_AUDIO))
        assertEquals("text", mediaTrackTypeLabel(C.TRACK_TYPE_TEXT))
        assertEquals("123", mediaTrackTypeLabel(123))
    }
}
