package com.miruplay.tv.scanner

import com.miruplay.tv.model.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScanStatusTest {
    @Test
    fun `status accumulates progress and completes source`() {
        val status = LibraryScanStatus()

        assertTrue(status.tryStart(currentPath = "WebDAV", canCancel = false))
        val started = status.state.value as LibraryScanState.Scanning
        assertEquals("WebDAV", started.currentPath)
        assertFalse(started.canCancel)

        val progress = status.reportProgress(
            currentPath = "Show A",
            filesScanned = 5,
            newEpisodes = 2,
        )
        assertEquals("Show A", progress.currentPath)
        assertEquals(5, progress.filesScanned)
        assertEquals(2, progress.newEpisodes)
        assertFalse(progress.canCancel)

        val result = ScanResult(
            animeName = "Show A",
            episodesFound = 7,
            newEpisodes = 3,
        )
        val completed = status.completeSource(result)
        assertEquals("Show A", completed.currentPath)
        assertEquals(7, completed.filesScanned)
        assertEquals(3, completed.newEpisodes)
        assertEquals(1, completed.contentVersion)
        assertFalse(completed.canCancel)

        status.finish(listOf(result))
        assertEquals(LibraryScanState.Finished(listOf(result)), status.state.value)
    }

    @Test
    fun `tryStart rejects concurrent scan`() {
        val status = LibraryScanStatus()

        assertTrue(status.tryStart("Library"))
        assertFalse(status.tryStart("WebDAV"))
    }
}
