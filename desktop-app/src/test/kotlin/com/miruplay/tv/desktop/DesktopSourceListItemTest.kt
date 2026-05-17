package com.miruplay.tv.desktop

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopSourceListItemTest {
    @Test
    fun `source list item shows type name and location`() {
        val item = DesktopSourceListItem(
            MediaSourceInfo(
                id = 4L,
                name = "NAS",
                type = MediaSourceType.SMB,
                connectionInfo = mapOf("url" to "smb://nas/anime"),
            )
        )

        assertEquals("SMB: NAS  smb://nas/anime", item.toString())
    }
}
