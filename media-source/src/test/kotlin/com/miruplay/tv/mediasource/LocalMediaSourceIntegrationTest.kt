package com.miruplay.tv.mediasource

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class LocalMediaSourceIntegrationTest {
    
    private lateinit var tempDir: File
    private lateinit var mediaSource: LocalMediaSource
    
    @Before
    fun setup() {
        tempDir = createTempDir("miruplay-test")
        // Create test files
        File(tempDir, "test.mkv").writeText("test video content")
        File(tempDir, "subs.srt").writeText("1\n00:00:01,000 --> 00:00:02,000\nHello")
        File(tempDir, ".DS_Store").writeText("hidden")
        File(tempDir, "Subfolder").mkdir()
        File(File(tempDir, "Subfolder"), "ep01.mkv").writeText("episode")
        
        val info = MediaSourceInfo(
            name = "test",
            type = MediaSourceType.LOCAL,
            connectionInfo = mapOf("path" to tempDir.absolutePath)
        )
        mediaSource = LocalMediaSource(info)
    }
    
    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }
    
    @Test
    fun `listFiles returns correct entries excluding hidden files`() = runBlocking {
        val result = mediaSource.listFiles(tempDir.absolutePath)
        assertTrue("listFiles should succeed", result.isSuccess())
        val files = result.getOrNull()
        assertNotNull("Files should not be null", files)
        assertTrue("Should have multiple files (excluding .DS_Store)", files!!.size >= 3)
        assertFalse("Should not include .DS_Store", files.any { it.name == ".DS_Store" })
        assertTrue("Should include test.mkv", files.any { it.name == "test.mkv" })
        assertTrue("Should include Subfolder", files.any { it.name == "Subfolder" })
    }
    
    @Test
    fun `openStream reads file content correctly`() = runBlocking {
        val result = mediaSource.openStream(File(tempDir, "test.mkv").absolutePath)
        assertTrue("openStream should succeed", result.isSuccess())
        val stream = result.getOrNull()
        assertNotNull("Stream should not be null", stream)
        val content = stream!!.bufferedReader().readText()
        assertEquals("Content should match", "test video content", content)
        stream.close()
    }
    
    @Test
    fun `openStream returns NotFound for missing file`() = runBlocking {
        val result = mediaSource.openStream("/nonexistent/path.mkv")
        assertTrue("Should return error", result.isSuccess() == false)
    }
    
    @Test
    fun `testConnection returns true for valid path`() = runBlocking {
        val result = mediaSource.testConnection()
        assertTrue("testConnection should succeed", result.isSuccess())
        assertEquals("Should return true", true, result.getOrNull())
    }
    
    @Test
    fun `listFiles with non-existent path returns error`() = runBlocking {
        val result = mediaSource.listFiles("/nonexistent/directory")
        assertTrue("Should return error for non-existent path", result.isSuccess() == false)
    }
    
    @Test
    fun `files sorted with directories first`() = runBlocking {
        val result = mediaSource.listFiles(tempDir.absolutePath)
        assertTrue("Should succeed", result.isSuccess())
        val files = result.getOrNull() ?: return@runBlocking
        val firstDir = files.first { it.isDirectory }
        val firstFile = files.first { !it.isDirectory }
        assertTrue("Directories should come before files", 
            files.indexOf(firstDir) < files.indexOf(firstFile))
    }
}
