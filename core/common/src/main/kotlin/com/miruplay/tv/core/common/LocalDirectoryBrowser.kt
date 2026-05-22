package com.miruplay.tv.core.common

import java.io.File

object LocalDirectoryBrowser {
    fun browse(path: String): Listing {
        val trimmedPath = path.trim()
        if (trimmedPath.isBlank()) {
            val roots = localRootCandidates()
            return Listing(
                path = "",
                displayPath = "设备存储",
                parentPath = null,
                entries = roots.map { it.toEntry() }
            )
        }

        val directory = File(trimmedPath)
        if (!directory.exists() || !directory.isDirectory) {
            throw IllegalArgumentException("目录不存在: $trimmedPath")
        }
        if (!directory.canRead()) {
            throw IllegalArgumentException("无权限读取目录: $trimmedPath")
        }

        return Listing(
            path = directory.absolutePath,
            displayPath = directory.absolutePath,
            parentPath = parentPathOf(directory),
            entries = directory.listFiles()
                .orEmpty()
                .asSequence()
                .filter { it.isDirectory }
                .filter { !it.name.startsWith(".") }
                .filter { it.name !in hiddenDirectoryNames }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .map { it.toEntry() }
                .toList()
        )
    }

    private fun localRootCandidates(): List<File> {
        val roots = linkedSetOf<File>()
        listOf(
            "/storage/emulated/0",
            "/sdcard",
            "/storage",
            "/mnt/media_rw",
            "/mnt/sdcard",
            "/mnt/usb_storage",
            "/storage/usb_storage"
        ).mapTo(roots) { File(it) }

        System.getenv("EXTERNAL_STORAGE")
            ?.takeIf { it.isNotBlank() }
            ?.let { roots += File(it) }

        System.getenv("SECONDARY_STORAGE")
            ?.split(File.pathSeparator)
            .orEmpty()
            .filter { it.isNotBlank() }
            .forEach { roots += File(it) }

        listOf("/storage", "/mnt/media_rw", "/mnt/usb_storage")
            .flatMap { File(it).listFiles().orEmpty().asIterable() }
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .filter { it.name !in hiddenDirectoryNames }
            .forEach { roots += it }

        return roots
            .filter { it.exists() && it.isDirectory && it.canRead() }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.absolutePath })
    }

    private fun File.toEntry(): Entry =
        Entry(
            name = name.ifBlank { absolutePath },
            path = absolutePath,
            canRead = canRead()
        )

    private fun parentPathOf(directory: File): String? {
        if (directory.absolutePath == File.separator) return null
        val parent = directory.parentFile ?: return ""
        return parent.absolutePath.takeUnless { it == directory.absolutePath }
    }

    data class Listing(
        val path: String,
        val displayPath: String,
        val parentPath: String?,
        val entries: List<Entry>
    )

    data class Entry(
        val name: String,
        val path: String,
        val canRead: Boolean
    )

    private val hiddenDirectoryNames = setOf(
        "proc", "sys", "dev", "selinux", "acct", "apex", "bin", "cache", "config",
        "d", "data_mirror", "debug_ramdisk", "etc", "linkerconfig", "postinstall",
        "system", "system_ext", "vendor", "vendor_dlkm"
    )
}
