package com.miruplay.tv.core.common

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 路径相关工具函数
 */
object PathUtils {
    /**
     * 规范化路径分隔符
     */
    fun normalizePath(path: String): String = path.replace("\\", "/")

    /**
     * 获取文件扩展名
     */
    fun getExtension(path: String): String {
        val lastDot = path.lastIndexOf('.')
        val lastSlash = path.lastIndexOf('/')
        return if (lastDot > lastSlash && lastDot < path.length - 1) {
            path.substring(lastDot + 1).lowercase()
        } else ""
    }

    /**
     * 获取不带扩展名的文件名
     */
    fun getNameWithoutExtension(path: String): String {
        val fileName = path.substringAfterLast('/')
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot > 0) fileName.substring(0, lastDot) else fileName
    }

    /**
     * URL 编码
     */
    fun encodePath(path: String): String =
        URLEncoder.encode(path, StandardCharsets.UTF_8)

    /**
     * URL 解码
     */
    fun decodePath(path: String): String =
        URLDecoder.decode(path, StandardCharsets.UTF_8)

    /**
     * 拼接路径
     */
    fun joinPath(vararg parts: String): String =
        parts.filter { it.isNotEmpty() }.joinToString("/") { it.trim('/') }

    /**
     * 获取父目录路径
     */
    fun getParentPath(path: String): String {
        val normalized = normalizePath(path).trimEnd('/')
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash > 0) normalized.substring(0, lastSlash) else ""
    }

    /**
     * 判断是否为视频文件
     */
    fun isVideoFile(path: String): Boolean {
        val ext = getExtension(path)
        return ext in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "m2ts")
    }

    /**
     * 判断是否为字幕文件
     */
    fun isSubtitleFile(path: String): Boolean {
        val ext = getExtension(path)
        return ext in listOf("srt", "ass", "ssa", "sub", "vtt")
    }

    /**
     * 判断是否为图片文件
     */
    fun isImageFile(path: String): Boolean {
        val ext = getExtension(path)
        return ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    }
}