package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressExtensionsTest {

    @Test
    fun `partial progress is resumable but not completed`() {
        val episode = episode(duration = 100_000L)
        val progress = progress(positionMs = 30_000L)

        assertFalse(episode.isCompleted(progress))
        assertTrue(episode.continueEpisodeProgress(progress))
        assertEquals(30_000L, episode.resumePosition(progress))
        assertEquals("看到 00:30", episode.progressLabel(progress))
    }

    @Test
    fun `completion threshold marks watched and clears resume position`() {
        val episode = episode(duration = 100_000L)
        val progress = progress(positionMs = 90_000L)

        assertTrue(episode.isCompleted(progress))
        assertFalse(episode.continueEpisodeProgress(progress))
        assertEquals(0L, episode.resumePosition(progress))
        assertEquals("已看", episode.progressLabel(progress))
    }

    @Test
    fun `unknown duration only completes after a finished playback`() {
        val episode = episode(duration = 0L)
        val partial = progress(positionMs = 30_000L)
        val completed = progress(positionMs = 0L, playCount = 1)

        assertFalse(episode.isCompleted(partial))
        assertTrue(episode.continueEpisodeProgress(partial))
        assertTrue(episode.isCompleted(completed))
        assertEquals("已看", episode.progressLabel(completed))
    }

    @Test
    fun `bangumi done collection is treated as completed`() {
        val episode = episode(duration = 100_000L, bangumiCollectionType = 2)
        val progress = progress(positionMs = 10_000L)

        assertTrue(episode.isCompleted(progress))
        assertEquals(0L, episode.resumePosition(progress))
        assertEquals("已看", episode.progressLabel(progress))
    }

    @Test
    fun `recent playback status helpers share TV wording`() {
        val record = progress(positionMs = 123_456L)

        assertEquals("尚未载入最近播放。", recentPlaybackInitialStatus())
        assertEquals("还没有最近播放记录。", recentPlaybackLoadedStatus(emptyList()))
        assertEquals("已载入 1 条最近播放。", recentPlaybackLoadedStatus(listOf(record)))
        assertEquals("还没有最近播放记录。", recentPlaybackShowingStatus(emptyList()))
        assertEquals("正在显示 1 条最近播放。", recentPlaybackShowingStatus(listOf(record)))
        assertEquals("请先选择一条最近播放记录。", recentPlaybackRequiredStatus())
        assertEquals("未看", playbackProgressRecordLabel(null))
        assertEquals("未看", playbackProgressRecordLabel(progress(positionMs = 0L)))
        assertEquals("看到 02:03", playbackProgressRecordLabel(record))
        assertEquals(record.copy(positionMs = 456_000L), record.retainedSelectionInProgressRecords(listOf(record.copy(positionMs = 456_000L))))
        assertEquals(null, record.retainedSelectionInProgressRecords(listOf(progress(episodeId = "ep2", positionMs = 0L))))
        assertEquals(null, null.retainedSelectionInProgressRecords(listOf(record)))
        assertEquals("123", record.resumeStartSecondsText())
        assertEquals("已载入最近播放：Episode 1。", record.loadedPlaybackStatus("Episode 1"))
    }

    private fun episode(
        duration: Long,
        bangumiCollectionType: Int? = null
    ): Episode = Episode(
        id = "ep1",
        animeId = "anime1",
        episodeNumber = 1,
        filePath = "/anime/ep1.mkv",
        fileName = "ep1.mkv",
        duration = duration,
        bangumiCollectionType = bangumiCollectionType
    )

    private fun progress(
        episodeId: String = "ep1",
        positionMs: Long,
        playCount: Int = 0
    ): ProgressRecord = ProgressRecord(
        episodeId = episodeId,
        positionMs = positionMs,
        lastWatched = 123L,
        playCount = playCount
    )
}
