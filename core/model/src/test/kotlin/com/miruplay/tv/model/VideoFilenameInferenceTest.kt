package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFilenameInferenceTest {
    @Test
    fun `infer extracts title season and episode from sxe release names`() {
        val metadata = VideoFilenameInference.infer("[Group] Frieren - S01E02 [1080p].mkv", "Downloads")

        assertEquals("Frieren", metadata.title)
        assertEquals(1, metadata.seasonNumber)
        assertEquals(2, metadata.episodeNumber)
    }

    @Test
    fun `infer falls back to useful parent title`() {
        val metadata = VideoFilenameInference.infer("01.mkv", "Classifier Show")

        assertEquals("Classifier Show", metadata.title)
        assertEquals(null, metadata.seasonNumber)
        assertEquals(1, metadata.episodeNumber)
    }

    @Test
    fun `infer ignores generic parent title by default`() {
        val metadata = VideoFilenameInference.infer("01.mkv", "Downloads")

        assertEquals("Unknown", metadata.title)
        assertEquals(1, metadata.episodeNumber)
    }
}
