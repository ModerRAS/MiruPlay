package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFileConventionsTest {
    @Test
    fun `mimeTypeForName maps shared desktop media types`() {
        assertEquals("video/x-matroska", MediaFileConventions.mimeTypeForName("Episode 01.mkv"))
        assertEquals("video/x-m4v", MediaFileConventions.mimeTypeForName("Episode 01.m4v"))
        assertEquals("application/x-subrip", MediaFileConventions.mimeTypeForName("Episode 01.srt"))
        assertEquals("image/webp", MediaFileConventions.mimeTypeForName("cover.webp"))
        assertNull(MediaFileConventions.mimeTypeForName("folder"))
    }

    @Test
    fun `hasExtension ignores path separators and remote fragments`() {
        assertTrue(MediaFileConventions.hasExtension("/Show/tvshow.nfo?download=1", "nfo"))
        assertTrue(MediaFileConventions.hasExtension("""D:\Show\Episode 01.MKV#fragment""", ".mkv"))
        assertTrue(MediaFileConventions.hasExtension("/Show/Episode #OVA?.mkv", "mkv"))
        assertFalse(MediaFileConventions.hasExtension("/Show/Episode 01.mkv", "nfo"))
    }

    @Test
    fun `isHiddenName recognizes ignored media source entries`() {
        assertTrue(MediaFileConventions.isHiddenName("Thumbs.db"))
        assertTrue(MediaFileConventions.isHiddenName("@eaDir/"))
        assertTrue(MediaFileConventions.isHiddenName("\$RECYCLE.BIN"))
        assertFalse(MediaFileConventions.isHiddenName("Season 01"))
    }

    @Test
    fun `sortEntries keeps directories first then names`() {
        val sorted = MediaFileConventions.sortEntries(
            listOf(
                FileEntry("b.mkv", "/b.mkv", isDirectory = false),
                FileEntry("Season 02", "/Season 02", isDirectory = true),
                FileEntry("a.mkv", "/a.mkv", isDirectory = false),
                FileEntry("Season 01", "/Season 01", isDirectory = true),
            )
        )

        assertEquals(listOf("Season 01", "Season 02", "a.mkv", "b.mkv"), sorted.map { it.name })
    }

    @Test
    fun `fileEntryComparator supports platform-specific entry types`() {
        data class PlatformEntry(val title: String, val folder: Boolean)

        val sorted = listOf(
            PlatformEntry("b.mkv", folder = false),
            PlatformEntry("Season 02", folder = true),
            PlatformEntry("a.mkv", folder = false),
            PlatformEntry("Season 01", folder = true),
        ).sortedWith(
            MediaFileConventions.fileEntryComparator(
                isDirectory = PlatformEntry::folder,
                name = PlatformEntry::title,
            )
        )

        assertEquals(listOf("Season 01", "Season 02", "a.mkv", "b.mkv"), sorted.map { it.title })
    }

    @Test
    fun `metadataFor preserves file entry fields`() {
        val metadata = MediaFileConventions.metadataFor(
            FileEntry(
                name = "Episode 01.mkv",
                path = "/Anime/Episode 01.mkv",
                isDirectory = false,
                size = 1234L,
                lastModified = 1000L,
                mimeType = "video/x-matroska",
            )
        )

        assertEquals("Episode 01.mkv", metadata.name)
        assertEquals("/Anime/Episode 01.mkv", metadata.path)
        assertEquals(false, metadata.isDirectory)
        assertEquals(1234L, metadata.size)
        assertEquals(1000L, metadata.lastModified)
        assertEquals("video/x-matroska", metadata.mimeType)
    }
}
