package com.miruplay.tv.webcontrol

import com.miruplay.tv.sync.rss.CloudDriveDirectoryBrowserState
import com.miruplay.tv.sync.rss.CloudDriveDirectoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class WebControlCloudDriveDirectoriesTest {
    @Test
    fun `directory browser state maps to WebUI dto`() {
        val dto = CloudDriveDirectoryBrowserState(
            path = "/CloudRoot/Inbox",
            displayPath = "CloudRoot / Inbox",
            parentPath = "/CloudRoot",
            entries = listOf(
                CloudDriveDirectoryEntry(
                    name = "Season 01",
                    path = "/CloudRoot/Inbox/Season 01",
                ),
                CloudDriveDirectoryEntry(
                    name = "Season 02",
                    path = "/CloudRoot/Inbox/Season 02",
                ),
            ),
        ).toWebControlDirectoryDto()

        assertEquals("/CloudRoot/Inbox", dto.path)
        assertEquals("CloudRoot / Inbox", dto.displayPath)
        assertEquals("/CloudRoot", dto.parentPath)
        assertEquals(2, dto.entries.size)
        assertEquals("Season 01", dto.entries[0].name)
        assertEquals("/CloudRoot/Inbox/Season 01", dto.entries[0].path)
        assertEquals(true, dto.entries[0].canRead)
        assertEquals("Season 02", dto.entries[1].name)
        assertEquals("/CloudRoot/Inbox/Season 02", dto.entries[1].path)
        assertEquals(true, dto.entries[1].canRead)
    }
}
