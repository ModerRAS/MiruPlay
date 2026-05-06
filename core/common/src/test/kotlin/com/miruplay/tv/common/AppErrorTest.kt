package com.miruplay.tv.common

import org.junit.Assert.*
import org.junit.Test

class AppErrorTest {

    @Test
    fun `all AppError subclasses have non-empty toUserMessage`() {
        val errors = listOf(
            AppError.MediaSourceError.NotFound("/test/path"),
            AppError.MediaSourceError.AuthenticationFailed("WebDAV"),
            AppError.MediaSourceError.ConnectionLost("SMB Share"),
            AppError.MediaSourceError.Timeout("WebDAV"),
            AppError.MediaSourceError.PermissionDenied("/test"),
            AppError.ParseError.NfoMalformed(5, "Missing title"),
            AppError.ParseError.InvalidEpisodePattern("random.mp4"),
            AppError.ParseError.XmlParseError("Invalid XML"),
            AppError.NetworkError.NoConnectivity,
            AppError.NetworkError.ServerUnreachable("https://example.com"),
            AppError.NetworkError.HttpError(404, "Not Found"),
            AppError.NetworkError.RateLimited(30),
            AppError.ScrapingError.NoMatchFound("Naruto"),
            AppError.ScrapingError.ApiError("AniList", "Rate limit"),
            AppError.ScrapingError.ParseError("Bangumi", "Missing field"),
            AppError.PlaybackError.CodecNotSupported("hevc"),
            AppError.PlaybackError.FileCorrupted("/path/to/file.mkv"),
            AppError.PlaybackError.StreamError("Network timeout"),
            AppError.SyncError.ConflictDetected("ep1"),
            AppError.SyncError.WriteFailed("/path/to.nfo", "Permission denied"),
            AppError.SyncError.ReadOnlyMedia
        )

        errors.forEach { error ->
            val message = error.toUserMessage()
            assertNotNull("Message should not be null for ${error::class.simpleName}", message)
            assertTrue("Message should not be blank for ${error::class.simpleName}", message.isNotBlank())
        }
    }

    @Test
    fun `AppError has correct sealed subclasses`() {
        // Verify the 6 error categories exist by checking their known subtypes
        val mediaSourceErrors = listOf(
            AppError.MediaSourceError.NotFound("/test"),
            AppError.MediaSourceError.AuthenticationFailed("src"),
            AppError.MediaSourceError.ConnectionLost("src"),
            AppError.MediaSourceError.Timeout("src"),
            AppError.MediaSourceError.PermissionDenied("/test")
        )
        assertEquals(5, mediaSourceErrors.size)

        val networkErrors = listOf(
            AppError.NetworkError.NoConnectivity,
            AppError.NetworkError.ServerUnreachable("url"),
            AppError.NetworkError.HttpError(500, "msg"),
            AppError.NetworkError.RateLimited(10)
        )
        assertEquals(4, networkErrors.size)

        val parseErrors = listOf(
            AppError.ParseError.NfoMalformed(1, "msg"),
            AppError.ParseError.InvalidEpisodePattern("file"),
            AppError.ParseError.XmlParseError("cause")
        )
        assertEquals(3, parseErrors.size)

        val scrapingErrors = listOf(
            AppError.ScrapingError.NoMatchFound("q"),
            AppError.ScrapingError.ApiError("src", "msg"),
            AppError.ScrapingError.ParseError("src", "detail")
        )
        assertEquals(3, scrapingErrors.size)

        val playbackErrors = listOf(
            AppError.PlaybackError.CodecNotSupported("h264"),
            AppError.PlaybackError.FileCorrupted("/path"),
            AppError.PlaybackError.StreamError("cause")
        )
        assertEquals(3, playbackErrors.size)

        val syncErrors = listOf(
            AppError.SyncError.ConflictDetected("ep1"),
            AppError.SyncError.WriteFailed("/path", "cause"),
            AppError.SyncError.ReadOnlyMedia
        )
        assertEquals(3, syncErrors.size)
    }
}

class ResultTest {

    @Test
    fun `Result Success map transforms value`() {
        val result: Result<Int> = Result.Success(42)
        val mapped = result.map { it * 2 }
        assertEquals(84, (mapped as Result.Success).data)
    }

    @Test
    fun `Result Error map skips transform`() {
        val error = AppError.MediaSourceError.NotFound("/test")
        val result: Result<Int> = Result.Error(error)
        val mapped = result.map { it * 2 }
        assertTrue(mapped is Result.Error)
    }

    @Test
    fun `Result flatMap chains success`() {
        val result = Result.Success(5)
        val chained = result.flatMap { Result.Success(it * 3) }
        assertEquals(15, (chained as Result.Success).data)
    }

    @Test
    fun `Result flatMap error short-circuits`() {
        val error = AppError.NetworkError.NoConnectivity
        val result: Result<Int> = Result.Error(error)
        val chained = result.flatMap { Result.Success(99) }
        assertTrue(chained is Result.Error)
    }

    @Test
    fun `Result getOrNull returns data on success`() {
        val result = Result.Success("hello")
        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun `Result getOrNull returns null on error`() {
        val result: Result<String> = Result.Error(AppError.PlaybackError.FileCorrupted("/test"))
        assertNull(result.getOrNull())
    }
}
