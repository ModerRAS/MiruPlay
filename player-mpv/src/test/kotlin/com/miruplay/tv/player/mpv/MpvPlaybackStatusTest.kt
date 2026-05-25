package com.miruplay.tv.player.mpv

import org.junit.Assert.assertEquals
import org.junit.Test

class MpvPlaybackStatusTest {
    @Test
    fun `mpv playback statuses share desktop wording`() {
        assertEquals("mpv is idle.", mpvIdleStatus())
        assertEquals("mpv launched: pid 1234", mpvLaunchedStatus(MpvLaunch(command = listOf("mpv"), pid = 1234L)))
        assertEquals("Unable to launch mpv.", mpvLaunchFailedStatus(RuntimeException()))
        assertEquals("mpv failed", mpvLaunchFailedStatus(RuntimeException("mpv failed")))
        assertEquals("No mpv process is active.", mpvNoActiveProcessStatus())
        assertEquals("mpv pause toggled.", mpvPauseToggledStatus())
        assertEquals("mpv resumed.", mpvResumedStatus())
        assertEquals("mpv paused.", mpvPausedStatus())
        assertEquals("mpv seeked back 10s.", mpvSeekBackStatus(seconds = 10))
        assertEquals("mpv seeked forward 30s.", mpvSeekForwardStatus(seconds = 30))
        assertEquals("mpv stopped.", mpvStoppedStatus())
        assertEquals("mpv exited.", mpvExitedStatus())
        assertEquals("mpv playback completed at 02:03.", mpvPlaybackCompletedStatus(positionMs = 123_456L))
        assertEquals("mpv position synced at 02:03.", mpvPositionSyncedStatus(positionMs = 123_456L))
    }
}
