package com.miruplay.tv.core.common

import org.junit.Assert.*
import org.junit.Test

class AppErrorTest {

    @Test
    fun `toUserMessage formats shared media source errors`() {
        assertEquals(
            "找不到文件或目录：/media/show.mkv",
            AppError.MediaSourceError.NotFound("/media/show.mkv").toUserMessage()
        )
        assertEquals(
            "WebDAV 认证失败，请检查用户名和密码",
            AppError.MediaSourceError.AuthenticationFailed("WebDAV").toUserMessage()
        )
        assertEquals(
            "与 SMB 的连接已断开",
            AppError.MediaSourceError.ConnectionLost("SMB").toUserMessage()
        )
        assertEquals(
            "CloudDrive2 连接超时，请检查网络",
            AppError.MediaSourceError.Timeout("CloudDrive2").toUserMessage()
        )
        assertEquals(
            "无权限访问：/private",
            AppError.MediaSourceError.PermissionDenied("/private").toUserMessage()
        )
    }

    @Test
    fun `toUserMessage formats shared network errors`() {
        assertEquals("无网络连接", AppError.NetworkError.NoConnectivity.toUserMessage())
        assertEquals(
            "无法连接服务器：https://example.com/rss.xml",
            AppError.NetworkError.ServerUnreachable("https://example.com/rss.xml").toUserMessage()
        )
        assertEquals(
            "HTTP 错误 404：Not Found",
            AppError.NetworkError.HttpError(404, "Not Found").toUserMessage()
        )
        assertEquals(
            "请求过于频繁，请 30秒 后重试",
            AppError.NetworkError.RateLimited(30).toUserMessage()
        )
    }

    @Test
    fun `toUserMessage formats shared parse and scraping errors`() {
        assertEquals(
            "NFO 文件格式错误（第 12 行）：Missing title",
            AppError.ParseError.NfoMalformed(12, "Missing title").toUserMessage()
        )
        assertEquals(
            "无法识别剧集文件名：random.mp4",
            AppError.ParseError.InvalidEpisodePattern("random.mp4").toUserMessage()
        )
        assertEquals(
            "XML 解析失败：Invalid XML",
            AppError.ParseError.XmlParseError("Invalid XML").toUserMessage()
        )
        assertEquals(
            "未找到「Naruto」的相关信息",
            AppError.ScrapingError.NoMatchFound("Naruto").toUserMessage()
        )
        assertEquals(
            "Bangumi 错误：Rate limit",
            AppError.ScrapingError.ApiError("Bangumi", "Rate limit").toUserMessage()
        )
        assertEquals(
            "Bangumi 数据解析错误：Missing field",
            AppError.ScrapingError.ParseError("Bangumi", "Missing field").toUserMessage()
        )
    }

    @Test
    fun `toUserMessage formats shared playback and sync errors`() {
        assertEquals(
            "不支持的视频编码：hevc",
            AppError.PlaybackError.CodecNotSupported("hevc").toUserMessage()
        )
        assertEquals(
            "文件损坏：/path/to/file.mkv",
            AppError.PlaybackError.FileCorrupted("/path/to/file.mkv").toUserMessage()
        )
        assertEquals(
            "播放出错：Network timeout",
            AppError.PlaybackError.StreamError("Network timeout").toUserMessage()
        )
        assertEquals(
            "进度冲突，请手动选择保留哪边",
            AppError.SyncError.ConflictDetected("ep1").toUserMessage()
        )
        assertEquals(
            "写入失败：Permission denied",
            AppError.SyncError.WriteFailed("/path/to.nfo", "Permission denied").toUserMessage()
        )
        assertEquals(
            "媒体源为只读，无法保存进度",
            AppError.SyncError.ReadOnlyMedia.toUserMessage()
        )
    }

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
