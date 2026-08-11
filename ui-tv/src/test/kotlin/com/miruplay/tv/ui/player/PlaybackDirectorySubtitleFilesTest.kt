package com.miruplay.tv.ui.player

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSource
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfoConventions
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackDirectorySubtitleFilesTest {
    @Test
    fun `parent directory supports regular and document tree paths`() {
        assertEquals("/Show", playbackParentDirectoryPath("/Show/Episode 01.mkv"))
        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3AAnime/document/primary%3AAnime%2FShow",
            playbackParentDirectoryPath(
                "content://com.android.externalstorage.documents/tree/primary%3AAnime/document/" +
                    "primary%3AAnime%2FShow%2FEpisode%2001.mkv",
            ),
        )
    }

    @Test
    fun `directory listing returns file paths and closes source`() = runBlocking {
        val info = MediaSourceInfoConventions.webDav("https://dav.example/anime", "DAV").copy(id = 7L)
        val mediaSource = mockk<MediaSource>()
        val factory = mockk<MediaSourceFactory>()
        every { factory.create(info) } returns Result.success(mediaSource)
        coEvery { mediaSource.listFiles("/Show") } returns Result.success(
            listOf(
                FileEntry("Episode 01.mkv", "/Show/Episode 01.mkv", isDirectory = false),
                FileEntry("Episode 01.zh-CN.ass", "/Show/Episode 01.zh-CN.ass", isDirectory = false),
                FileEntry("Subs", "/Show/Subs", isDirectory = true),
            ),
        )
        coEvery { mediaSource.close() } just Runs

        assertEquals(
            listOf("/Show/Episode 01.mkv", "/Show/Episode 01.zh-CN.ass"),
            listPlaybackSiblingPaths(factory, info, "/Show/Episode 01.mkv"),
        )
        coVerify(exactly = 1) { mediaSource.close() }
    }
}
