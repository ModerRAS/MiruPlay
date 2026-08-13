package com.miruplay.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BangumiCommentsPanelTest {
    @Test
    fun `comment images allow public HTTP URLs`() {
        assertEquals(
            "https://lain.bgm.tv/pic/user/l/1.jpg",
            safeBangumiCommentImageUrl("https://lain.bgm.tv/pic/user/l/1.jpg"),
        )
        assertEquals(
            "http://203.0.113.10/image.jpg",
            safeBangumiCommentImageUrl("http://203.0.113.10/image.jpg"),
        )
    }

    @Test
    fun `comment images reject local and non HTTP targets`() {
        listOf(
            "file:///tmp/private",
            "/relative/image.jpg",
            "http://localhost/image.jpg",
            "http://printer/image.jpg",
            "http://device.local/image.jpg",
            "http://127.0.0.1/image.jpg",
            "http://10.0.0.1/image.jpg",
            "http://169.254.1.1/image.jpg",
            "http://172.16.0.1/image.jpg",
            "http://192.168.1.1/image.jpg",
            "http://[::1]/image.jpg",
            "http://[fd00::1]/image.jpg",
            "http://[::ffff:127.0.0.1]/image.jpg",
            "http://[::ffff:10.0.0.1]/image.jpg",
            "http://[::ffff:169.254.1.1]/image.jpg",
            "http://[::ffff:172.16.0.1]/image.jpg",
            "http://[::ffff:192.168.1.1]/image.jpg",
            "http://[::ffff:0.0.0.0]/image.jpg",
        ).forEach { value -> assertNull(value, safeBangumiCommentImageUrl(value)) }
    }
}
