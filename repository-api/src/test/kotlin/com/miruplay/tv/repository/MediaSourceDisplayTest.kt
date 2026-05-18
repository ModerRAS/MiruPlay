package com.miruplay.tv.repository

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ProgressRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSourceDisplayTest {
    @Test
    fun `displayLabel joins source name and type`() {
        assertEquals(
            "Library · LOCAL",
            MediaSourceInfo(
                name = "Library",
                type = MediaSourceType.LOCAL,
                connectionInfo = mapOf("path" to "D:/Anime"),
            ).displayLabel(),
        )
    }

    @Test
    fun `upsertById replaces matching id or appends missing id`() {
        val local = MediaSourceInfo(id = 1L, name = "Local", type = MediaSourceType.LOCAL)
        val updatedLocal = local.copy(name = "Updated")
        val smb = MediaSourceInfo(id = 2L, name = "SMB", type = MediaSourceType.SMB)

        assertEquals(listOf(updatedLocal), listOf(local).upsertById(updatedLocal))
        assertEquals(listOf(local, smb), listOf(local).upsertById(smb))
    }

    @Test
    fun `mediaDisplayName uses the last path segment`() {
        assertEquals(
            "Episode 01.mkv",
            ProgressRecord(
                episodeId = "smb://nas/anime/Show/Episode 01.mkv",
                positionMs = 1_000L,
                lastWatched = 0L,
            ).mediaDisplayName(),
        )
        assertEquals(
            "opaque-id",
            ProgressRecord(
                episodeId = "opaque-id",
                positionMs = 1_000L,
                lastWatched = 0L,
            ).mediaDisplayName(),
        )
    }
}
