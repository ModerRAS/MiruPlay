package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.detailEpisodePageUnitLabel
import com.miruplay.tv.model.detailEpisodeShelfSubtitle
import com.miruplay.tv.model.detailHeroEmptyTitle
import com.miruplay.tv.model.detailHeroEmptySubtitle
import com.miruplay.tv.model.libraryContinueWatchingSectionTitle
import com.miruplay.tv.model.mediaDetailsPageUnitLabel
import com.miruplay.tv.model.metadataBangumiTokenEmptyMessage
import com.miruplay.tv.model.metadataBangumiTokenSavedMessage
import com.miruplay.tv.model.mediaDetailsEmptyMessage
import com.miruplay.tv.model.mediaDetailsSectionTitle
import com.miruplay.tv.model.pagedListPageSummary
import com.miruplay.tv.model.recentPlaybackClearActionLabel
import com.miruplay.tv.model.recentPlaybackEmptyMessage
import com.miruplay.tv.model.recentPlaybackPageUnitLabel
import com.miruplay.tv.model.recentPlaybackRefreshActionLabel
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.sync.bangumiMetadataCacheId
import com.miruplay.tv.sync.toBangumiLocalEpisode
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
            pagedListPageSummary(6, 6, 14, 6, recentPlaybackPageUnitLabel()),
            recentPlaybackPageSummary(pageStart = 6, visibleCount = 6, itemCount = 14),
        )
        assertEquals(
            pagedListPageSummary(12, 2, 14, 6, recentPlaybackPageUnitLabel()),
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

        assertEquals("选择一部番剧", detailHeroEmptyTitle())
        assertEquals("从媒体库海报墙选择内容后显示详情。", detailHeroEmptySubtitle())
        assertEquals("当前详情没有可播放索引项", detailEpisodeShelfSubtitle(0))
        assertEquals("全 12 话 · 同番选集", detailEpisodeShelfSubtitle(12))
        assertEquals(libraryContinueWatchingSectionTitle(), recents.title)
        assertEquals(recentPlaybackRefreshActionLabel(), recents.refreshAction)
        assertEquals(recentPlaybackClearActionLabel(), recents.clearAction)
        assertEquals(recentPlaybackEmptyMessage(), recents.emptyState)
        assertEquals(mediaDetailsSectionTitle(), mediaDetails.title)
        assertEquals(mediaDetailsEmptyMessage(), mediaDetails.emptyState)
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
    fun `shared Bangumi cache ids and episodes mirror indexed metadata`() {
        val entry = MediaIndexEntry(
            sourceId = 1,
            path = "show/Frieren - S01E02.mkv",
            animeName = "Frieren",
            metadataId = "431767",
            metadataTitle = "葬送的芙莉莲",
            seasonNumber = 1,
            episodeNumber = 2,
            episodeTitle = "旅途",
        )

        val episode = entry.toBangumiLocalEpisode(
            animeId = entry.bangumiMetadataCacheId(),
        )

        assertEquals("431767", entry.bangumiMetadataCacheId())
        assertEquals("show/Frieren - S01E02.mkv", episode.id)
        assertEquals("431767", episode.animeId)
        assertEquals(2, episode.episodeNumber)
        assertEquals("旅途", episode.title)
        assertEquals("Frieren - S01E02.mkv", episode.fileName)
    }

    @Test
    fun `desktop Bangumi token save keeps existing token when input is blank`() {
        val result = desktopBangumiTokenSaveResult(
            input = "   ",
            existingToken = "existing-token",
        )

        assertEquals("existing-token", result.token)
        assertTrue(result.configured)
        assertEquals(metadataBangumiTokenEmptyMessage(), result.status)
    }

    @Test
    fun `desktop Bangumi token save trims and stores non blank input`() {
        val result = desktopBangumiTokenSaveResult(
            input = "  new-token  ",
            existingToken = "existing-token",
        )

        assertEquals("new-token", result.token)
        assertTrue(result.configured)
        assertEquals(metadataBangumiTokenSavedMessage(), result.status)
    }

    @Test
    fun `detail episode navigation moves within all rows`() {
        assertEquals(1, moveDetailEpisodeSelection(currentIndex = 0, itemCount = 3, delta = 1))
        assertEquals(1, moveDetailEpisodeSelection(currentIndex = 2, itemCount = 3, delta = -1))
        assertEquals(null, moveDetailEpisodeSelection(currentIndex = 0, itemCount = 3, delta = -1))
        assertEquals(null, moveDetailEpisodeSelection(currentIndex = 2, itemCount = 3, delta = 1))
        assertEquals(6, moveDetailEpisodeSelection(currentIndex = 5, itemCount = 12, delta = 1))
        assertEquals(5, moveDetailEpisodeSelection(currentIndex = 6, itemCount = 12, delta = -1))
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
        assertEquals(
            DetailEpisodeFocusTarget.Row(6),
            moveDetailEpisodeFocusTarget(currentIndex = 5, itemCount = 12, delta = 1),
        )
        assertEquals(null, moveDetailEpisodeFocusTarget(currentIndex = 0, itemCount = 0, delta = 1))
    }

    @Test
    fun `detail episode page helpers keep every episode row reachable`() {
        assertEquals(0, detailEpisodePageStartForIndex(index = 0, itemCount = 14))
        assertEquals(0, detailEpisodePageStartForIndex(index = 5, itemCount = 14))
        assertEquals(6, detailEpisodePageStartForIndex(index = 6, itemCount = 14))
        assertEquals(12, detailEpisodePageStartForIndex(index = 13, itemCount = 14))
        assertEquals(12, detailEpisodePageStartForIndex(index = 30, itemCount = 14))
        assertEquals(6, detailEpisodeCoercedPageStart(pageStart = 9, itemCount = 14))
        assertEquals(12, detailEpisodeCoercedPageStart(pageStart = 48, itemCount = 14))
        assertEquals(0, detailEpisodeCoercedPageStart(pageStart = -6, itemCount = 14))

        assertEquals(
            pagedListPageSummary(6, 6, 14, 6, detailEpisodePageUnitLabel()),
            detailEpisodePageSummary(pageStart = 6, visibleCount = 6, itemCount = 14),
        )
        assertEquals(
            pagedListPageSummary(12, 2, 14, 6, detailEpisodePageUnitLabel()),
            detailEpisodePageSummary(pageStart = 12, visibleCount = 2, itemCount = 14),
        )
        assertEquals(null, detailEpisodePageSummary(pageStart = 0, visibleCount = 5, itemCount = 5))
    }

    @Test
    fun `detail episode empty state bridges to neighboring panels`() {
        assertEquals(DetailEpisodeFocusTarget.PreviousPanel, detailEpisodeEmptyFocusTarget(Key.DirectionUp))
        assertEquals(DetailEpisodeFocusTarget.NextPanel, detailEpisodeEmptyFocusTarget(Key.DirectionDown))
        assertEquals(null, detailEpisodeEmptyFocusTarget(Key.DirectionLeft))
        assertEquals(null, detailEpisodeEmptyFocusTarget(Key.DirectionRight))
    }

    @Test
    fun `detail episode progress labels mirror shared progress copy`() {
        assertEquals("未看", detailEpisodeProgressLabel(null))
        assertEquals(
            "看到 02:03",
            detailEpisodeProgressLabel(
                ProgressRecord(
                    episodeId = "episode-1",
                    positionMs = 123_456L,
                    lastWatched = 1L,
                ),
            ),
        )
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
            mediaDetailsFocusTarget(currentIndex = 0, rowCount = 7, pageStart = 0, visibleCount = 6, key = Key.DirectionDown),
        )
        assertEquals(
            MediaDetailsFocusTarget.PreviousPanel,
            mediaDetailsFocusTarget(currentIndex = 0, rowCount = 7, pageStart = 0, visibleCount = 6, key = Key.DirectionUp),
        )
        assertEquals(
            MediaDetailsFocusTarget.Row(3),
            mediaDetailsFocusTarget(currentIndex = 0, rowCount = 7, pageStart = 0, visibleCount = 6, key = Key.DirectionRight),
        )
        assertEquals(
            MediaDetailsFocusTarget.Row(2),
            mediaDetailsFocusTarget(currentIndex = 5, rowCount = 7, pageStart = 0, visibleCount = 6, key = Key.DirectionLeft),
        )
        assertEquals(
            MediaDetailsFocusTarget.Row(5),
            mediaDetailsFocusTarget(currentIndex = 2, rowCount = 7, pageStart = 0, visibleCount = 6, key = Key.DirectionRight),
        )
        assertEquals(
            MediaDetailsFocusTarget.Row(6),
            mediaDetailsFocusTarget(currentIndex = 5, rowCount = 7, pageStart = 0, visibleCount = 6, key = Key.DirectionDown),
        )
        assertEquals(null, mediaDetailsFocusTarget(currentIndex = 0, rowCount = 7, pageStart = 0, visibleCount = 6, key = Key.DirectionLeft))
        assertEquals(null, mediaDetailsFocusTarget(currentIndex = 3, rowCount = 7, pageStart = 0, visibleCount = 6, key = Key.DirectionRight))
        assertEquals(null, mediaDetailsFocusTarget(currentIndex = 0, rowCount = 0, pageStart = 0, visibleCount = 0, key = Key.DirectionDown))
    }

    @Test
    fun `media details page helpers keep every detail row reachable`() {
        assertEquals(0, mediaDetailsPageStartForIndex(index = 0, itemCount = 13))
        assertEquals(0, mediaDetailsPageStartForIndex(index = 5, itemCount = 13))
        assertEquals(6, mediaDetailsPageStartForIndex(index = 6, itemCount = 13))
        assertEquals(12, mediaDetailsPageStartForIndex(index = 12, itemCount = 13))
        assertEquals(12, mediaDetailsPageStartForIndex(index = 30, itemCount = 13))
        assertEquals(6, mediaDetailsCoercedPageStart(pageStart = 11, itemCount = 13))
        assertEquals(12, mediaDetailsCoercedPageStart(pageStart = 42, itemCount = 13))
        assertEquals(0, mediaDetailsCoercedPageStart(pageStart = -6, itemCount = 13))
        assertEquals(3, mediaDetailsSplitIndex(pageStart = 0, visibleCount = 6))
        assertEquals(9, mediaDetailsSplitIndex(pageStart = 6, visibleCount = 6))
        assertEquals(13, mediaDetailsSplitIndex(pageStart = 12, visibleCount = 1))

        assertEquals(
            MediaDetailsFocusTarget.Row(7),
            mediaDetailsFocusTarget(currentIndex = 6, rowCount = 13, pageStart = 6, visibleCount = 6, key = Key.DirectionDown),
        )
        assertEquals(
            MediaDetailsFocusTarget.Row(9),
            mediaDetailsFocusTarget(currentIndex = 6, rowCount = 13, pageStart = 6, visibleCount = 6, key = Key.DirectionRight),
        )
        assertEquals(
            MediaDetailsFocusTarget.Row(8),
            mediaDetailsFocusTarget(currentIndex = 11, rowCount = 13, pageStart = 6, visibleCount = 6, key = Key.DirectionLeft),
        )
        assertEquals(
            MediaDetailsFocusTarget.Row(12),
            mediaDetailsFocusTarget(currentIndex = 11, rowCount = 13, pageStart = 6, visibleCount = 6, key = Key.DirectionDown),
        )
        assertEquals(null, mediaDetailsFocusTarget(currentIndex = 12, rowCount = 13, pageStart = 12, visibleCount = 1, key = Key.DirectionRight))
        assertEquals(
            pagedListPageSummary(6, 6, 13, 6, mediaDetailsPageUnitLabel()),
            mediaDetailsPageSummary(pageStart = 6, visibleCount = 6, itemCount = 13),
        )
        assertEquals(
            pagedListPageSummary(12, 1, 13, 6, mediaDetailsPageUnitLabel()),
            mediaDetailsPageSummary(pageStart = 12, visibleCount = 1, itemCount = 13),
        )
        assertEquals(null, mediaDetailsPageSummary(pageStart = 0, visibleCount = 5, itemCount = 5))
    }
}
