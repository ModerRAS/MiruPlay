package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.repository.MediaIndexEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopPosterGroupingTest {
    @Test
    fun `poster wall uses six poster columns like Android TV library`() {
        val groups = (1..13).map { index ->
            DesktopPosterGroup(
                title = "Show $index",
                entries = listOf(MediaIndexEntry(sourceId = 1, path = "show-$index.mkv")),
            )
        }

        val rows = groups.toPosterWallRows()

        assertEquals(listOf(6, 6, 1), rows.map { it.size })
    }

    @Test
    fun `poster wall down navigation lands on nearest poster in short next row`() {
        val groups = (1..8).map { index ->
            DesktopPosterGroup(
                title = "Show $index",
                entries = listOf(MediaIndexEntry(sourceId = 1, path = "show-$index.mkv")),
            )
        }

        assertEquals("Show 7", groups.posterNavigationTarget(currentIndex = 0, key = Key.DirectionDown)?.title)
        assertEquals("Show 8", groups.posterNavigationTarget(currentIndex = 1, key = Key.DirectionDown)?.title)
        assertEquals("Show 8", groups.posterNavigationTarget(currentIndex = 4, key = Key.DirectionDown)?.title)
        assertEquals("Show 8", groups.posterNavigationTarget(currentIndex = 5, key = Key.DirectionDown)?.title)
        assertNull(groups.posterNavigationTarget(currentIndex = 6, key = Key.DirectionDown))
    }

    @Test
    fun `poster wall navigation also accepts shared direction intents`() {
        val groups = (1..8).map { index ->
            DesktopPosterGroup(
                title = "Show $index",
                entries = listOf(MediaIndexEntry(sourceId = 1, path = "show-$index.mkv")),
            )
        }

        assertEquals(
            "Show 7",
            groups.posterNavigationTarget(
                currentIndex = 0,
                intent = MiruPlayInputIntent.DirectionDown,
            )?.title,
        )
        assertEquals(
            "Show 8",
            groups.posterNavigationTarget(
                currentIndex = 6,
                intent = MiruPlayInputIntent.DirectionRight,
            )?.title,
        )
        assertNull(
            groups.posterNavigationTarget(
                currentIndex = 6,
                intent = MiruPlayInputIntent.DirectionLeft,
            ),
        )
        assertNull(
            groups.posterNavigationTarget(
                currentIndex = 0,
                intent = MiruPlayInputIntent.Activate,
            ),
        )
    }

    @Test
    fun `poster wall horizontal navigation stays inside visual rows`() {
        val groups = (1..8).map { index ->
            DesktopPosterGroup(
                title = "Show $index",
                entries = listOf(MediaIndexEntry(sourceId = 1, path = "show-$index.mkv")),
            )
        }

        assertNull(groups.posterNavigationTarget(currentIndex = 5, key = Key.DirectionRight))
        assertNull(groups.posterNavigationTarget(currentIndex = 6, key = Key.DirectionLeft))
        assertEquals("Show 8", groups.posterNavigationTarget(currentIndex = 6, key = Key.DirectionRight)?.title)
        assertNull(groups.posterNavigationTarget(currentIndex = 7, key = Key.DirectionRight))
    }

    @Test
    fun `featured row orders highest heat before poster wall`() {
        val groups = listOf(
            DesktopPosterGroup(
                title = "Small",
                entries = listOf(MediaIndexEntry(sourceId = 1, path = "Small - S01E01.mkv", episodeNumber = 1)),
            ),
            DesktopPosterGroup(
                title = "Long Running",
                entries = listOf(
                    MediaIndexEntry(sourceId = 1, path = "Long Running - S01E01.mkv", episodeNumber = 1),
                    MediaIndexEntry(sourceId = 1, path = "Long Running - S01E02.mkv", episodeNumber = 2),
                    MediaIndexEntry(sourceId = 1, path = "Long Running - S01E03.mkv", episodeNumber = 3),
                ),
            ),
            DesktopPosterGroup(
                title = "Medium",
                entries = listOf(
                    MediaIndexEntry(sourceId = 1, path = "Medium - S01E01.mkv", episodeNumber = 1),
                    MediaIndexEntry(sourceId = 1, path = "Medium - S01E02.mkv", episodeNumber = 2),
                ),
            ),
        )

        val featured = groups.toFeaturedPosterGroups()

        assertEquals(listOf("Long Running", "Medium"), featured.map { it.title })
    }

    @Test
    fun `recently added shelf orders newest groups before older groups`() {
        val groups = listOf(
            DesktopPosterGroup(
                title = "Older",
                entries = listOf(MediaIndexEntry(sourceId = 1, path = "Older - 01.mkv", lastModified = 100)),
            ),
            DesktopPosterGroup(
                title = "Newest",
                entries = listOf(MediaIndexEntry(sourceId = 1, path = "Newest - 01.mkv", lastModified = 300)),
            ),
            DesktopPosterGroup(
                title = "Middle",
                entries = listOf(MediaIndexEntry(sourceId = 1, path = "Middle - 01.mkv", lastModified = 200)),
            ),
        )

        val recentlyAdded = groups.toRecentlyAddedPosterGroups()

        assertEquals(listOf("Newest", "Middle", "Older"), recentlyAdded.map { it.title })
    }

    @Test
    fun `horizontal poster shelves move left and right without wrapping rows`() {
        val groups = (1..4).map { index ->
            DesktopPosterGroup(
                title = "Show $index",
                entries = listOf(MediaIndexEntry(sourceId = 1, path = "show-$index.mkv")),
            )
        }

        assertEquals("Show 2", groups.posterShelfNavigationTarget(currentIndex = 0, key = Key.DirectionRight)?.title)
        assertEquals("Show 3", groups.posterShelfNavigationTarget(currentIndex = 3, key = Key.DirectionLeft)?.title)
        assertNull(groups.posterShelfNavigationTarget(currentIndex = 0, key = Key.DirectionLeft))
        assertNull(groups.posterShelfNavigationTarget(currentIndex = 3, key = Key.DirectionRight))
        assertNull(groups.posterShelfNavigationTarget(currentIndex = 1, key = Key.DirectionDown))
    }

    @Test
    fun `horizontal poster shelves also accept shared direction intents`() {
        val groups = (1..4).map { index ->
            DesktopPosterGroup(
                title = "Show $index",
                entries = listOf(MediaIndexEntry(sourceId = 1, path = "show-$index.mkv")),
            )
        }

        assertEquals(
            "Show 2",
            groups.posterShelfNavigationTarget(
                currentIndex = 0,
                intent = MiruPlayInputIntent.DirectionRight,
            )?.title,
        )
        assertEquals(
            "Show 3",
            groups.posterShelfNavigationTarget(
                currentIndex = 3,
                intent = MiruPlayInputIntent.DirectionLeft,
            )?.title,
        )
        assertNull(
            groups.posterShelfNavigationTarget(
                currentIndex = 0,
                intent = MiruPlayInputIntent.DirectionLeft,
            ),
        )
        assertNull(
            groups.posterShelfNavigationTarget(
                currentIndex = 1,
                intent = MiruPlayInputIntent.DirectionDown,
            ),
        )
    }

    @Test
    fun `library media focus moves from poster wall into lower shelves`() {
        assertEquals(
            LibraryMediaFocusTarget.PreviousPanel,
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.PosterWall(0),
                key = Key.DirectionUp,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
        assertEquals(
            LibraryMediaFocusTarget.Featured(0),
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.PosterWall(6),
                key = Key.DirectionDown,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
        assertEquals(
            LibraryMediaFocusTarget.Featured(1),
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.PosterWall(7),
                key = Key.DirectionDown,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
        assertEquals(
            LibraryMediaFocusTarget.RecentlyAdded(1),
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.PosterWall(7),
                key = Key.DirectionDown,
                posterCount = 8,
                featuredCount = 0,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
        assertEquals(
            LibraryMediaFocusTarget.SearchBar,
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.PosterWall(7),
                key = Key.DirectionDown,
                posterCount = 8,
                featuredCount = 0,
                recentlyAddedCount = 0,
                columns = 6,
            ),
        )
    }

    @Test
    fun `library media focus also accepts shared direction intents`() {
        assertEquals(
            LibraryMediaFocusTarget.PreviousPanel,
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.PosterWall(0),
                intent = MiruPlayInputIntent.DirectionUp,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
        assertEquals(
            LibraryMediaFocusTarget.Featured(1),
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.PosterWall(7),
                intent = MiruPlayInputIntent.DirectionDown,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
        assertEquals(
            LibraryMediaFocusTarget.Featured(1),
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.Featured(0),
                intent = MiruPlayInputIntent.DirectionRight,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
        assertEquals(
            LibraryMediaFocusTarget.SearchBar,
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.RecentlyAdded(3),
                intent = MiruPlayInputIntent.DirectionDown,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
        assertNull(
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.Featured(0),
                intent = MiruPlayInputIntent.Activate,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
    }

    @Test
    fun `library media focus moves between featured and recently added shelves`() {
        assertEquals(
            LibraryMediaFocusTarget.PosterWall(1),
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.Featured(1),
                key = Key.DirectionUp,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
        assertEquals(
            LibraryMediaFocusTarget.RecentlyAdded(1),
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.Featured(1),
                key = Key.DirectionDown,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
        assertEquals(
            LibraryMediaFocusTarget.Featured(1),
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.Featured(0),
                key = Key.DirectionRight,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
        assertEquals(
            LibraryMediaFocusTarget.RecentlyAdded(2),
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.RecentlyAdded(3),
                key = Key.DirectionLeft,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
        assertEquals(
            LibraryMediaFocusTarget.Featured(1),
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.RecentlyAdded(3),
                key = Key.DirectionUp,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
        assertEquals(
            LibraryMediaFocusTarget.PosterWall(3),
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.RecentlyAdded(3),
                key = Key.DirectionUp,
                posterCount = 8,
                featuredCount = 0,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
        assertEquals(
            LibraryMediaFocusTarget.SearchBar,
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.RecentlyAdded(3),
                key = Key.DirectionDown,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
    }

    @Test
    fun `library media focus exits to search bar after poster shelves`() {
        assertEquals(
            LibraryMediaFocusTarget.SearchBar,
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.Featured(1),
                key = Key.DirectionDown,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 0,
                columns = 6,
            ),
        )
        assertEquals(
            LibraryMediaFocusTarget.SearchBar,
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.RecentlyAdded(3),
                key = Key.DirectionDown,
                posterCount = 8,
                featuredCount = 2,
                recentlyAddedCount = 4,
                columns = 6,
            ),
        )
        assertEquals(
            LibraryMediaFocusTarget.SearchBar,
            libraryMediaFocusTarget(
                current = LibraryMediaFocusTarget.PosterWall(7),
                key = Key.DirectionDown,
                posterCount = 8,
                featuredCount = 0,
                recentlyAddedCount = 0,
                columns = 6,
            ),
        )
    }

    @Test
    fun `library search bar focus moves between media and source panels`() {
        assertEquals(
            LibrarySearchFocusTarget.Action,
            librarySearchFocusTarget(LibrarySearchFocusTarget.Field, Key.DirectionRight),
        )
        assertEquals(
            LibrarySearchFocusTarget.Field,
            librarySearchFocusTarget(LibrarySearchFocusTarget.Action, Key.DirectionLeft),
        )
        assertEquals(
            LibrarySearchFocusTarget.PreviousPanel,
            librarySearchFocusTarget(LibrarySearchFocusTarget.Field, Key.DirectionUp),
        )
        assertEquals(
            LibrarySearchFocusTarget.NextPanel,
            librarySearchFocusTarget(LibrarySearchFocusTarget.Action, Key.DirectionDown),
        )
        assertNull(librarySearchFocusTarget(LibrarySearchFocusTarget.Field, Key.DirectionLeft))
        assertNull(librarySearchFocusTarget(LibrarySearchFocusTarget.Action, Key.DirectionRight))
    }

    @Test
    fun `library search bar focus also accepts shared direction intents`() {
        assertEquals(
            LibrarySearchFocusTarget.Action,
            librarySearchFocusTarget(
                LibrarySearchFocusTarget.Field,
                MiruPlayInputIntent.DirectionRight,
            ),
        )
        assertEquals(
            LibrarySearchFocusTarget.Field,
            librarySearchFocusTarget(
                LibrarySearchFocusTarget.Action,
                MiruPlayInputIntent.DirectionLeft,
            ),
        )
        assertEquals(
            LibrarySearchFocusTarget.PreviousPanel,
            librarySearchFocusTarget(
                LibrarySearchFocusTarget.Field,
                MiruPlayInputIntent.DirectionUp,
            ),
        )
        assertEquals(
            LibrarySearchFocusTarget.NextPanel,
            librarySearchFocusTarget(
                LibrarySearchFocusTarget.Action,
                MiruPlayInputIntent.DirectionDown,
            ),
        )
        assertNull(
            librarySearchFocusTarget(
                LibrarySearchFocusTarget.Field,
                MiruPlayInputIntent.Activate,
            ),
        )
    }
}
