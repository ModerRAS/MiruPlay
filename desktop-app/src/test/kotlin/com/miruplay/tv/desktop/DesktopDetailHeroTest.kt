package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.repository.MediaIndexEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopDetailHeroTest {
    @Test
    fun `detail hero prefers metadata title and includes episode context`() {
        val source = MediaSourceInfoConventions.local(
            name = "Anime Library",
            rootPath = "D:/Anime",
        )
        val entry = MediaIndexEntry(
            sourceId = 7,
            path = "D:/Anime/Frieren/Frieren - S01E02.mkv",
            animeName = "Fixture Frieren",
            metadataTitle = "Frieren",
            seasonNumber = 1,
            episodeNumber = 2,
            episodeTitle = "The Journey",
        )

        assertEquals("Frieren", entry.detailTitle())
        val subtitle = entry.detailSubtitle(source)
        assertTrue(subtitle.contains("Anime Library"))
        assertTrue(subtitle.contains("S1"))
        assertTrue(subtitle.contains("EP2"))
        assertTrue(subtitle.contains("The Journey"))
    }

    @Test
    fun `detail hero action navigation moves within primary actions`() {
        assertEquals(
            DesktopDetailHeroAction.BackToLibrary,
            moveDesktopDetailHeroAction(DesktopDetailHeroAction.Play, 1),
        )
        assertEquals(
            DesktopDetailHeroAction.Play,
            moveDesktopDetailHeroAction(DesktopDetailHeroAction.BackToLibrary, -1),
        )
        assertEquals(null, moveDesktopDetailHeroAction(DesktopDetailHeroAction.Play, -1))
        assertEquals(null, moveDesktopDetailHeroAction(DesktopDetailHeroAction.BackToLibrary, 1))
    }

    @Test
    fun `recent playback navigation moves within all records`() {
        assertEquals(1, moveRecentPlaybackSelection(currentIndex = 0, itemCount = 3, delta = 1))
        assertEquals(1, moveRecentPlaybackSelection(currentIndex = 2, itemCount = 3, delta = -1))
        assertEquals(null, moveRecentPlaybackSelection(currentIndex = 0, itemCount = 3, delta = -1))
        assertEquals(null, moveRecentPlaybackSelection(currentIndex = 2, itemCount = 3, delta = 1))
        assertEquals(6, moveRecentPlaybackSelection(currentIndex = 5, itemCount = 9, delta = 1))
        assertEquals(5, moveRecentPlaybackSelection(currentIndex = 6, itemCount = 9, delta = -1))
        assertEquals(null, moveRecentPlaybackSelection(currentIndex = 0, itemCount = 0, delta = 1))
    }

    @Test
    fun `recent playback focus moves through actions and neighboring panels`() {
        assertEquals(
            RecentPlaybackFocusTarget.Action(RecentPlaybackAction.Refresh),
            moveRecentPlaybackFocusTarget(currentIndex = 0, itemCount = 3, delta = -1),
        )
        assertEquals(
            RecentPlaybackFocusTarget.Row(1),
            moveRecentPlaybackFocusTarget(currentIndex = 0, itemCount = 3, delta = 1),
        )
        assertEquals(
            RecentPlaybackFocusTarget.NextPanel,
            moveRecentPlaybackFocusTarget(currentIndex = 2, itemCount = 3, delta = 1),
        )
        assertEquals(
            RecentPlaybackFocusTarget.Row(6),
            moveRecentPlaybackFocusTarget(currentIndex = 5, itemCount = 9, delta = 1),
        )
        assertEquals(null, moveRecentPlaybackFocusTarget(currentIndex = 0, itemCount = 0, delta = 1))
        assertEquals(RecentPlaybackAction.Clear, moveRecentPlaybackAction(RecentPlaybackAction.Refresh, 1))
        assertEquals(RecentPlaybackAction.Refresh, moveRecentPlaybackAction(RecentPlaybackAction.Clear, -1))
        assertEquals(null, moveRecentPlaybackAction(RecentPlaybackAction.Refresh, -1))
        assertEquals(null, moveRecentPlaybackAction(RecentPlaybackAction.Clear, 1))
        assertEquals(
            RecentPlaybackFocusTarget.PreviousPanel,
            recentPlaybackActionVerticalFocusTarget(direction = -1, hasRecords = true),
        )
        assertEquals(
            RecentPlaybackFocusTarget.Row(0),
            recentPlaybackActionVerticalFocusTarget(direction = 1, hasRecords = true),
        )
        assertEquals(
            RecentPlaybackFocusTarget.EmptyState,
            recentPlaybackActionVerticalFocusTarget(direction = 1, hasRecords = false),
        )
        assertEquals(
            RecentPlaybackFocusTarget.Action(RecentPlaybackAction.Refresh),
            recentPlaybackEmptyFocusTarget(Key.DirectionUp),
        )
        assertEquals(
            RecentPlaybackFocusTarget.NextPanel,
            recentPlaybackEmptyFocusTarget(Key.DirectionDown),
        )
        assertEquals(null, recentPlaybackEmptyFocusTarget(Key.DirectionLeft))
    }

    @Test
    fun `recent playback page helpers keep every recent record reachable`() {
        assertEquals(0, recentPlaybackPageStartForIndex(index = 0, itemCount = 14))
        assertEquals(0, recentPlaybackPageStartForIndex(index = 5, itemCount = 14))
        assertEquals(6, recentPlaybackPageStartForIndex(index = 6, itemCount = 14))
        assertEquals(12, recentPlaybackPageStartForIndex(index = 13, itemCount = 14))
        assertEquals(12, recentPlaybackPageStartForIndex(index = 30, itemCount = 14))
        assertEquals(6, recentPlaybackCoercedPageStart(pageStart = 8, itemCount = 14))
        assertEquals(12, recentPlaybackCoercedPageStart(pageStart = 48, itemCount = 14))
        assertEquals(0, recentPlaybackCoercedPageStart(pageStart = -6, itemCount = 14))

        assertEquals(
            "显示 7-12 / 14 条记录，按上/下继续翻页。",
            recentPlaybackPageSummary(pageStart = 6, visibleCount = 6, itemCount = 14),
        )
        assertEquals(
            "显示 13-14 / 14 条记录，按上/下继续翻页。",
            recentPlaybackPageSummary(pageStart = 12, visibleCount = 2, itemCount = 14),
        )
        assertEquals(null, recentPlaybackPageSummary(pageStart = 0, visibleCount = 5, itemCount = 5))
    }

    @Test
    fun `detail hero down key falls back to bangumi when recents are absent`() {
        assertEquals(
            DesktopDetailDownTarget.EpisodeList,
            detailHeroDownTarget(hasRelatedEpisodes = true, hasRecentPlayback = false),
        )
        assertEquals(
            DesktopDetailDownTarget.RecentPlayback,
            detailHeroDownTarget(hasRelatedEpisodes = false, hasRecentPlayback = true),
        )
        assertEquals(
            DesktopDetailDownTarget.BangumiMetadata,
            detailHeroDownTarget(hasRelatedEpisodes = false, hasRecentPlayback = false),
        )
    }

    @Test
    fun `detail hero stat labels mirror TV detail pills from indexed data`() {
        val entry = MediaIndexEntry(
            sourceId = 1,
            path = "show/Frieren - S01E02.mkv",
            animeName = "Frieren",
            seasonNumber = 1,
            episodeNumber = 2,
            metadataSource = "Bangumi",
        )

        assertEquals(
            listOf("全 3 话", "第 1 季", "Bangumi"),
            detailHeroStatLabels(entry, episodeCount = 3),
        )
        assertEquals(emptyList<String>(), detailHeroStatLabels(null, episodeCount = 3))
        assertEquals(listOf("第 1 季", "Bangumi"), detailHeroStatLabels(entry, episodeCount = 0))
    }

    @Test
    fun `detail panel chrome uses TV-facing Chinese labels`() {
        val recents = desktopRecentPlaybackLabels()
        val mediaDetails = desktopMediaDetailsLabels()

        assertEquals("从媒体库海报墙选择内容后显示详情。", desktopDetailHeroEmptySubtitle())
        assertEquals("当前详情没有可播放索引项", detailEpisodeShelfSubtitle(0))
        assertEquals("全 12 话 · 同番选集", detailEpisodeShelfSubtitle(12))
        assertEquals("继续观看", recents.title)
        assertEquals("刷新", recents.refreshAction)
        assertEquals("清除条目", recents.clearAction)
        assertEquals("开始播放后会在这里显示最近记录。", recents.emptyState)
        assertEquals("媒体详情", mediaDetails.title)
        assertEquals("选择媒体后会在这里显示详细信息。", mediaDetails.emptyState)
    }

    @Test
    fun `detail episodes group selected anime and sort by season episode`() {
        val selected = MediaIndexEntry(
            sourceId = 1,
            path = "show/Frieren - S01E02.mkv",
            animeName = "Frieren",
            seasonNumber = 1,
            episodeNumber = 2,
        )
        val entries = listOf(
            selected,
            MediaIndexEntry(sourceId = 1, path = "show/Frieren - S01E01.mkv", animeName = "Frieren", seasonNumber = 1, episodeNumber = 1),
            MediaIndexEntry(sourceId = 1, path = "show/Frieren - S02E01.mkv", animeName = "Frieren", seasonNumber = 2, episodeNumber = 1),
            MediaIndexEntry(sourceId = 1, path = "show/Bocchi - S01E01.mkv", animeName = "Bocchi", seasonNumber = 1, episodeNumber = 1),
            MediaIndexEntry(sourceId = 2, path = "other/Frieren - S01E03.mkv", animeName = "Frieren", seasonNumber = 1, episodeNumber = 3),
        )

        val episodes = detailEpisodesForSelection(entries, selected)

        assertEquals(
            listOf("show/Frieren - S01E01.mkv", "show/Frieren - S01E02.mkv", "show/Frieren - S02E01.mkv"),
            episodes.map { it.path },
        )
        assertEquals(listOf(1, 2), detailEpisodeSeasons(episodes))
        assertEquals(1, detailActiveEpisodeSeason(episodes, selected, requestedSeason = null))
        assertEquals(listOf("show/Frieren - S02E01.mkv"), detailEpisodesForSeason(episodes, 2).map { it.path })
    }

    @Test
    fun `detail episode navigation moves within visible rows`() {
        assertEquals(1, moveDetailEpisodeSelection(currentIndex = 0, itemCount = 3, delta = 1))
        assertEquals(1, moveDetailEpisodeSelection(currentIndex = 2, itemCount = 3, delta = -1))
        assertEquals(null, moveDetailEpisodeSelection(currentIndex = 0, itemCount = 3, delta = -1))
        assertEquals(null, moveDetailEpisodeSelection(currentIndex = 2, itemCount = 3, delta = 1))
        assertEquals(null, moveDetailEpisodeSelection(currentIndex = 0, itemCount = 0, delta = 1))
    }

    @Test
    fun `detail episode focus exits to neighboring panels at row boundaries`() {
        assertEquals(
            DetailEpisodeFocusTarget.PreviousPanel,
            moveDetailEpisodeFocusTarget(currentIndex = 0, itemCount = 3, delta = -1),
        )
        assertEquals(
            DetailEpisodeFocusTarget.Row(1),
            moveDetailEpisodeFocusTarget(currentIndex = 0, itemCount = 3, delta = 1),
        )
        assertEquals(
            DetailEpisodeFocusTarget.NextPanel,
            moveDetailEpisodeFocusTarget(currentIndex = 2, itemCount = 3, delta = 1),
        )
        assertEquals(null, moveDetailEpisodeFocusTarget(currentIndex = 0, itemCount = 0, delta = 1))
    }

    @Test
    fun `detail episode empty state bridges to neighboring panels`() {
        assertEquals(DetailEpisodeFocusTarget.PreviousPanel, detailEpisodeEmptyFocusTarget(Key.DirectionUp))
        assertEquals(DetailEpisodeFocusTarget.NextPanel, detailEpisodeEmptyFocusTarget(Key.DirectionDown))
        assertEquals(null, detailEpisodeEmptyFocusTarget(Key.DirectionLeft))
        assertEquals(null, detailEpisodeEmptyFocusTarget(Key.DirectionRight))
    }

    @Test
    fun `detail episode focus bridges through season selector when multiple seasons exist`() {
        assertEquals(
            DetailEpisodeFocusTarget.Season(1),
            moveDetailEpisodeFocusTarget(
                currentIndex = 0,
                itemCount = 3,
                delta = -1,
                seasonCount = 3,
                activeSeasonIndex = 1,
            ),
        )
        assertEquals(
            DetailEpisodeFocusTarget.Season(0),
            detailEpisodeSeasonFocusTarget(
                currentIndex = 1,
                seasonCount = 3,
                episodeCount = 3,
                selectedEpisodeIndex = 2,
                key = Key.DirectionLeft,
            ),
        )
        assertEquals(
            DetailEpisodeFocusTarget.Season(2),
            detailEpisodeSeasonFocusTarget(
                currentIndex = 1,
                seasonCount = 3,
                episodeCount = 3,
                selectedEpisodeIndex = 2,
                key = Key.DirectionRight,
            ),
        )
        assertEquals(
            DetailEpisodeFocusTarget.Row(2),
            detailEpisodeSeasonFocusTarget(
                currentIndex = 1,
                seasonCount = 3,
                episodeCount = 3,
                selectedEpisodeIndex = 2,
                key = Key.DirectionDown,
            ),
        )
        assertEquals(
            DetailEpisodeFocusTarget.PreviousPanel,
            detailEpisodeSeasonFocusTarget(
                currentIndex = 1,
                seasonCount = 3,
                episodeCount = 3,
                selectedEpisodeIndex = 2,
                key = Key.DirectionUp,
            ),
        )
        assertEquals(
            DetailEpisodeFocusTarget.NextPanel,
            detailEpisodeSeasonFocusTarget(
                currentIndex = 1,
                seasonCount = 3,
                episodeCount = 0,
                selectedEpisodeIndex = 2,
                key = Key.DirectionDown,
            ),
        )
        assertEquals(
            DetailEpisodeFocusTarget.PreviousPanel,
            moveDetailEpisodeFocusTarget(
                currentIndex = 0,
                itemCount = 3,
                delta = -1,
                seasonCount = 1,
                activeSeasonIndex = 0,
            ),
        )
        assertEquals(
            null,
            detailEpisodeSeasonFocusTarget(
                currentIndex = 0,
                seasonCount = 3,
                episodeCount = 3,
                selectedEpisodeIndex = 0,
                key = Key.DirectionLeft,
            ),
        )
        assertEquals(
            null,
            detailEpisodeSeasonFocusTarget(
                currentIndex = 2,
                seasonCount = 3,
                episodeCount = 3,
                selectedEpisodeIndex = 0,
                key = Key.DirectionRight,
            ),
        )
    }

    @Test
    fun `media details focus moves through two column rows and exits upward`() {
        assertEquals(MediaDetailsFocusTarget.Row(0), mediaDetailsInitialFocusTarget(hasRows = true))
        assertEquals(MediaDetailsFocusTarget.EmptyState, mediaDetailsInitialFocusTarget(hasRows = false))
        assertEquals(MediaDetailsFocusTarget.PreviousPanel, mediaDetailsEmptyFocusTarget(Key.DirectionUp))
        assertEquals(null, mediaDetailsEmptyFocusTarget(Key.DirectionDown))
        assertEquals(
            MediaDetailsFocusTarget.Row(1),
            mediaDetailsFocusTarget(currentIndex = 0, rowCount = 7, splitIndex = 4, key = Key.DirectionDown),
        )
        assertEquals(
            MediaDetailsFocusTarget.PreviousPanel,
            mediaDetailsFocusTarget(currentIndex = 0, rowCount = 7, splitIndex = 4, key = Key.DirectionUp),
        )
        assertEquals(
            MediaDetailsFocusTarget.Row(4),
            mediaDetailsFocusTarget(currentIndex = 0, rowCount = 7, splitIndex = 4, key = Key.DirectionRight),
        )
        assertEquals(
            MediaDetailsFocusTarget.Row(2),
            mediaDetailsFocusTarget(currentIndex = 6, rowCount = 7, splitIndex = 4, key = Key.DirectionLeft),
        )
        assertEquals(
            MediaDetailsFocusTarget.Row(6),
            mediaDetailsFocusTarget(currentIndex = 3, rowCount = 7, splitIndex = 4, key = Key.DirectionRight),
        )
        assertEquals(null, mediaDetailsFocusTarget(currentIndex = 6, rowCount = 7, splitIndex = 4, key = Key.DirectionDown))
        assertEquals(null, mediaDetailsFocusTarget(currentIndex = 0, rowCount = 7, splitIndex = 4, key = Key.DirectionLeft))
        assertEquals(null, mediaDetailsFocusTarget(currentIndex = 4, rowCount = 7, splitIndex = 4, key = Key.DirectionRight))
        assertEquals(null, mediaDetailsFocusTarget(currentIndex = 0, rowCount = 0, splitIndex = 0, key = Key.DirectionDown))
    }
}
