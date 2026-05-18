package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaPathConventionsTest {
    @Test
    fun `fileName and stem handle local and remote separators`() {
        assertEquals("Episode 01.mkv", MediaPathConventions.fileName("""D:\Anime\Show\Episode 01.mkv"""))
        assertEquals("Episode 01", MediaPathConventions.stem("/mnt/anime/Show/Episode 01.mkv"))
        assertEquals("Episode 01.mkv", MediaPathConventions.fileName("smb://nas/share/Show/Episode 01.mkv"))
    }

    @Test
    fun `sibling and child paths preserve separator style`() {
        assertEquals(
            """D:\Anime\Show\Episode 01.nfo""",
            MediaPathConventions.siblingWithExtension("""D:\Anime\Show\Episode 01.mkv""", "nfo"),
        )
        assertEquals(
            "/mnt/anime/Show/tvshow.nfo",
            MediaPathConventions.childPath("/mnt/anime/Show", "tvshow.nfo"),
        )
        assertEquals(
            "smb://nas/share/Show/tvshow.nfo",
            MediaPathConventions.childPath("smb://nas/share/Show", "tvshow.nfo"),
        )
    }

    @Test
    fun `remoteParent handles webdav roots and smb shares`() {
        assertNull(MediaPathConventions.remoteParent(""))
        assertNull(MediaPathConventions.remoteParent("/"))
        assertEquals("", MediaPathConventions.remoteParent("/Anime"))
        assertEquals("/Anime", MediaPathConventions.remoteParent("/Anime/Season 1"))
        assertNull(MediaPathConventions.remoteParent("smb://nas/share"))
        assertEquals(
            "smb://nas/share/Anime",
            MediaPathConventions.remoteParent("smb://nas/share/Anime/Season 1"),
        )
    }

    @Test
    fun `remote path encoding is per segment and uses percent spaces`() {
        assertEquals(
            "%E5%AD%A4%E7%8B%AC%E6%91%87%E6%BB%9A/Season%2001/Episode%2001.mkv",
            MediaPathConventions.encodeRemotePath("/孤独摇滚/Season 01/Episode 01.mkv"),
        )
        assertEquals("a b", MediaPathConventions.decodePath("a%20b"))
    }

    @Test
    fun `joinRemoteUrl appends encoded path to base url`() {
        assertEquals(
            "https://dav.example/anime/%E5%AD%A4%E7%8B%AC%E6%91%87%E6%BB%9A/Season%2001/Episode%2001.mkv",
            MediaPathConventions.joinRemoteUrl(
                "https://dav.example/anime/",
                "/孤独摇滚/Season 01/Episode 01.mkv",
            ),
        )
        assertEquals(
            "https://dav.example/anime/Episode%2001.mkv",
            MediaPathConventions.joinRemoteUrl("https://dav.example/anime", "Episode 01.mkv"),
        )
        assertEquals(
            "https://dav.example/anime/Episode%2001.mkv",
            MediaPathConventions.joinRemoteUrl(
                "https://dav.example/anime",
                "https://dav.example/anime/Episode%2001.mkv",
            ),
        )
        assertEquals("/Episode 01.mkv", MediaPathConventions.joinRemoteUrl("", "/Episode 01.mkv"))
    }
}
