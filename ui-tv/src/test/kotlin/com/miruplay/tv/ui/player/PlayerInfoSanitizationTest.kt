package com.miruplay.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerInfoSanitizationTest {
    @Test
    fun `remote source shows only decoded file name and scheme`() {
        val source = "https://dav.example/private/Episode%2001.mkv?token=secret"
        val name = sanitizedPlaybackSourceName(source)

        assertEquals("Episode 01.mkv", name)
        assertEquals("HTTPS", playbackSourceSchemeLabel(source))
        assertFalse(name.contains("secret"))
        assertFalse(name.contains("dav.example"))
    }

    @Test
    fun `windows source is identified as a local file`() {
        val source = "D:\\Anime\\Episode 02.mkv"

        assertEquals("Episode 02.mkv", sanitizedPlaybackSourceName(source))
        assertEquals("本地文件", playbackSourceSchemeLabel(source))
    }
}
