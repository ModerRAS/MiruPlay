package com.miruplay.tv.core.common

sealed class AppError {
    abstract fun toUserMessage(): String

    sealed class MediaSourceError : AppError() {
        data class NotFound(val path: String) : MediaSourceError() {
            override fun toUserMessage(): String = "找不到文件或目录：$path"
        }
        data class AuthenticationFailed(val source: String) : MediaSourceError() {
            override fun toUserMessage(): String = "$source 认证失败，请检查用户名和密码"
        }
        data class ConnectionLost(val source: String) : MediaSourceError() {
            override fun toUserMessage(): String = "与 $source 的连接已断开"
        }
        data class Timeout(val source: String) : MediaSourceError() {
            override fun toUserMessage(): String = "$source 连接超时，请检查网络"
        }
        data class PermissionDenied(val path: String) : MediaSourceError() {
            override fun toUserMessage(): String = "无权限访问：$path"
        }
    }

    sealed class ParseError : AppError() {
        data class NfoMalformed(val line: Int, val message: String) : ParseError() {
            override fun toUserMessage(): String = "NFO 文件格式错误（第 $line 行）：$message"
        }
        data class InvalidEpisodePattern(val filename: String) : ParseError() {
            override fun toUserMessage(): String = "无法识别剧集文件名：$filename"
        }
        data class XmlParseError(val cause: String) : ParseError() {
            override fun toUserMessage(): String = "XML 解析失败：$cause"
        }
    }

    sealed class NetworkError : AppError() {
        data object NoConnectivity : NetworkError() {
            override fun toUserMessage(): String = "无网络连接"
        }
        data class ServerUnreachable(val url: String) : NetworkError() {
            override fun toUserMessage(): String = "无法连接服务器：$url"
        }
        data class HttpError(val code: Int, val message: String) : NetworkError() {
            override fun toUserMessage(): String = "HTTP 错误 $code：$message"
        }
        data class RateLimited(val retryAfter: Int) : NetworkError() {
            override fun toUserMessage(): String = "请求过于频繁，请 ${retryAfter}秒 后重试"
        }
    }

    sealed class ScrapingError : AppError() {
        data class NoMatchFound(val query: String) : ScrapingError() {
            override fun toUserMessage(): String = "未找到「${query}」的相关信息"
        }
        data class ApiError(val source: String, val message: String) : ScrapingError() {
            override fun toUserMessage(): String = "$source 错误：$message"
        }
        data class ParseError(val source: String, val detail: String) : ScrapingError() {
            override fun toUserMessage(): String = "$source 数据解析错误：$detail"
        }
    }

    sealed class PlaybackError : AppError() {
        data class CodecNotSupported(val codec: String) : PlaybackError() {
            override fun toUserMessage(): String = "不支持的视频编码：$codec"
        }
        data class FileCorrupted(val path: String) : PlaybackError() {
            override fun toUserMessage(): String = "文件损坏：$path"
        }
        data class StreamError(val cause: String) : PlaybackError() {
            override fun toUserMessage(): String = "播放出错：$cause"
        }
    }

    sealed class SyncError : AppError() {
        data class ConflictDetected(val episodeId: String) : SyncError() {
            override fun toUserMessage(): String = "进度冲突，请手动选择保留哪边"
        }
        data class WriteFailed(val path: String, val cause: String) : SyncError() {
            override fun toUserMessage(): String = "写入失败：$cause"
        }
        data object ReadOnlyMedia : SyncError() {
            override fun toUserMessage(): String = "媒体源为只读，无法保存进度"
        }
    }

    sealed class AppUpdateError : AppError() {
        data object NoReleaseFound : AppUpdateError() {
            override fun toUserMessage(): String = "GitHub 上暂未找到可用版本。"
        }

        data object NoInstallableApk : AppUpdateError() {
            override fun toUserMessage(): String = "最新版本没有可安装的 APK。"
        }

        data class DownloadFailed(val cause: String) : AppUpdateError() {
            override fun toUserMessage(): String = "下载更新失败：$cause"
        }

        data class InstallIntentFailed(val cause: String) : AppUpdateError() {
            override fun toUserMessage(): String = "无法打开系统安装器：$cause"
        }
    }
}
