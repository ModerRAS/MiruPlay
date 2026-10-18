package com.miruplay.tv.core.common

sealed class AppError {
    abstract fun toUiText(): UiText

    fun toUserMessage(): String = toUiText().fallback

    sealed class MediaSourceError : AppError() {
        data class NotFound(val path: String) : MediaSourceError() {
            override fun toUiText(): UiText = UiText("error_media_not_found", listOf(path), "找不到文件或目录：$path")
        }
        data class AuthenticationFailed(val source: String) : MediaSourceError() {
            override fun toUiText(): UiText = UiText("error_media_auth", listOf(source), "$source 认证失败，请检查用户名和密码")
        }
        data class ConnectionLost(val source: String) : MediaSourceError() {
            override fun toUiText(): UiText = UiText("error_media_conn_lost", listOf(source), "与 $source 的连接已断开")
        }
        data class Timeout(val source: String) : MediaSourceError() {
            override fun toUiText(): UiText = UiText("error_media_timeout", listOf(source), "$source 连接超时，请检查网络")
        }
        data class PermissionDenied(val path: String) : MediaSourceError() {
            override fun toUiText(): UiText = UiText("error_media_permission", listOf(path), "无权限访问：$path")
        }
    }

    sealed class ParseError : AppError() {
        data class NfoMalformed(val line: Int, val message: String) : ParseError() {
            override fun toUiText(): UiText = UiText("error_parse_nfo", listOf(line, message), "NFO 文件格式错误（第 $line 行）：$message")
        }
        data class InvalidEpisodePattern(val filename: String) : ParseError() {
            override fun toUiText(): UiText = UiText("error_parse_episode", listOf(filename), "无法识别剧集文件名：$filename")
        }
        data class XmlParseError(val cause: String) : ParseError() {
            override fun toUiText(): UiText = UiText("error_parse_xml", listOf(cause), "XML 解析失败：$cause")
        }
    }

    sealed class NetworkError : AppError() {
        data object NoConnectivity : NetworkError() {
            override fun toUiText(): UiText = uiText("error_net_offline", "无网络连接")
        }
        data class ServerUnreachable(val url: String) : NetworkError() {
            override fun toUiText(): UiText = UiText("error_net_unreachable", listOf(url), "无法连接服务器：$url")
        }
        data class HttpError(val code: Int, val message: String) : NetworkError() {
            override fun toUiText(): UiText = UiText("error_net_http", listOf(code, message), "HTTP 错误 $code：$message")
        }
        data class RateLimited(val retryAfter: Int) : NetworkError() {
            override fun toUiText(): UiText = UiText("error_net_rate_limited", listOf(retryAfter), "请求过于频繁，请 ${retryAfter}秒 后重试")
        }
    }

    sealed class ScrapingError : AppError() {
        data class NoMatchFound(val query: String) : ScrapingError() {
            override fun toUiText(): UiText = UiText("error_scrape_no_match", listOf(query), "未找到「${query}」的相关信息")
        }
        data class ApiError(val source: String, val message: String) : ScrapingError() {
            override fun toUiText(): UiText = UiText("error_scrape_api", listOf(source, message), "$source 错误：$message")
        }
        data class ParseError(val source: String, val detail: String) : ScrapingError() {
            override fun toUiText(): UiText = UiText("error_scrape_parse", listOf(source, detail), "$source 数据解析错误：$detail")
        }
    }

    sealed class PlaybackError : AppError() {
        data class CodecNotSupported(val codec: String) : PlaybackError() {
            override fun toUiText(): UiText = UiText("error_playback_codec", listOf(codec), "不支持的视频编码：$codec")
        }
        data class FileCorrupted(val path: String) : PlaybackError() {
            override fun toUiText(): UiText = UiText("error_playback_corrupt", listOf(path), "文件损坏：$path")
        }
        data class StreamError(val cause: String) : PlaybackError() {
            override fun toUiText(): UiText = UiText("error_playback_stream", listOf(cause), "播放出错：$cause")
        }
    }

    sealed class LibraryIndexError : AppError() {
        data class Missing(val path: String) : LibraryIndexError() {
            override fun toUiText(): UiText = UiText("error_index_missing", listOf(path), "未找到媒体库索引：$path")
        }
        data class UnsupportedVersion(val version: Int) : LibraryIndexError() {
            override fun toUiText(): UiText = UiText("error_index_version", listOf(version), "不支持的媒体库索引版本：$version")
        }
        data class InvalidSchema(val reason: String) : LibraryIndexError() {
            override fun toUiText(): UiText = UiText("error_index_schema", listOf(reason), "媒体库索引结构无效：$reason")
        }
        data class ReadFailed(val cause: String) : LibraryIndexError() {
            override fun toUiText(): UiText = UiText("error_index_read", listOf(cause), "读取媒体库索引失败：$cause")
        }
    }

    sealed class SyncError : AppError() {
        data class ConflictDetected(val episodeId: String) : SyncError() {
            override fun toUiText(): UiText = uiText("error_sync_conflict", "进度冲突，请手动选择保留哪边")
        }
        data class WriteFailed(val path: String, val cause: String) : SyncError() {
            override fun toUiText(): UiText = UiText("error_sync_write", listOf(cause), "写入失败：$cause")
        }
        data object ReadOnlyMedia : SyncError() {
            override fun toUiText(): UiText = uiText("error_sync_readonly", "媒体源为只读，无法保存进度")
        }
    }

    sealed class AppUpdateError : AppError() {
        data object NoReleaseFound : AppUpdateError() {
            override fun toUiText(): UiText = uiText("error_update_no_release", "GitHub 上暂未找到可用版本。")
        }

        data object NoInstallableApk : AppUpdateError() {
            override fun toUiText(): UiText = uiText("error_update_no_apk", "最新版本没有可安装的 APK。")
        }

        data class ChannelNoRelease(val channel: String) : AppUpdateError() {
            override fun toUiText(): UiText = UiText("error_update_no_channel_release", listOf(channel), "$channel 渠道暂无已发布的版本。")
        }

        data class DownloadFailed(val cause: String) : AppUpdateError() {
            override fun toUiText(): UiText = UiText("error_update_download", listOf(cause), "下载更新失败：$cause")
        }

        data class InstallIntentFailed(val cause: String) : AppUpdateError() {
            override fun toUiText(): UiText = UiText("error_update_installer", listOf(cause), "无法打开系统安装器：$cause")
        }
    }
}
