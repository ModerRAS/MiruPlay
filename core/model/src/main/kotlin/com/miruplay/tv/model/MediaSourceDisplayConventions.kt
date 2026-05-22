package com.miruplay.tv.model

fun MediaSourceType.tvLabel(): String =
    when (this) {
        MediaSourceType.LOCAL -> "本地"
        MediaSourceType.WEBDAV -> "WebDAV"
        MediaSourceType.SMB -> "SMB"
    }

fun MediaSourceType.defaultSourceName(): String =
    when (this) {
        MediaSourceType.LOCAL -> "本地下载"
        MediaSourceType.WEBDAV -> "WebDAV 媒体库"
        MediaSourceType.SMB -> "SMB 共享"
    }

fun MediaSourceType.genericSourceName(): String =
    when (this) {
        MediaSourceType.LOCAL -> "本地媒体源"
        MediaSourceType.WEBDAV -> "WebDAV 媒体源"
        MediaSourceType.SMB -> "SMB 媒体源"
    }

fun MediaSourceType.tvSourceHint(): String =
    when (this) {
        MediaSourceType.LOCAL -> "设备文件夹"
        MediaSourceType.WEBDAV -> "HTTP 文件服务"
        MediaSourceType.SMB -> "局域网共享"
    }

fun MediaSourceType.tvLocationLabel(): String =
    when (this) {
        MediaSourceType.LOCAL -> "媒体文件夹"
        MediaSourceType.WEBDAV -> "WebDAV 地址"
        MediaSourceType.SMB -> "SMB 地址"
    }

fun MediaSourceInfo.tvConnectionStatusLabel(): String =
    if (isConnected) "可连接" else "待验证"

fun MediaSourceInfo.tvDisplayName(fallbackName: String? = null): String =
    name.ifBlank {
        fallbackName?.takeIf { it.isNotBlank() } ?: type.defaultSourceName()
    }

fun MediaSourceInfo.tvDisplayLabel(fallbackName: String? = null): String =
    "${tvDisplayName(fallbackName)} · ${type.tvLabel()}"

fun MediaSourceInfo.tvDisplayStatusLabel(): String =
    "${type.tvLabel()} · ${tvConnectionStatusLabel()}"
