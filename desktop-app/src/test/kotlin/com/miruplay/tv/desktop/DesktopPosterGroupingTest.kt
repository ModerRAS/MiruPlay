package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import com.miruplay.tv.repository.MediaIndexEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPosterGroupingTest {
    @Test
    fun `poster wall groups indexed episodes by anime title`() {
        val entries = listOf(
            MediaIndexEntry(sourceId = 1, path = "show/Frieren - 02.mkv", animeName = "Frieren", episodeNumber = 2),
            MediaIndexEntry(sourceId = 1, path = "show/Frieren - 01.mkv", animeName = "Frieren", episodeNumber = 1),
            MediaIndexEntry(sourceId = 1, path = "show/Bocchi - 01.mkv", animeName = "Bocchi", episodeNumber = 1),
            MediaIndexEntry(sourceId = 1, path = "show/Frieren", animeName = "Frieren", isDirectory = true),
        )

        val groups = entries.toDesktopPosterGroups()

        assertEquals(listOf("Bocchi", "Frieren"), groups.map { it.title })
        val frieren = groups.single { it.title == "Frieren" }
        assertEquals(2, frieren.entries.size)
        assertTrue(frieren.primaryEntry.path.endsWith("Frieren - 01.mkv"))
        assertEquals("2 episodes", frieren.subtitle)
    }

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
    fun `library media focus moves from poster wall into lower shelves`() {
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
        assertNull(
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
        assertNull(
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
}
