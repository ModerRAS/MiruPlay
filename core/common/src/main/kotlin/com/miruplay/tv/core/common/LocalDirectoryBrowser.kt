package com.miruplay.tv.core.common

import java.io.File

object LocalDirectoryBrowser {
    fun browse(path: String): Listing =
        browse(path) { localRootCandidates() }

    internal fun browse(path: String, rootsProvider: () -> List<File>): Listing {
        val trimmedPath = path.trim()
        if (trimmedPath.isBlank()) {
            val roots = rootsProvider()
            return Listing(
                path = "",
                displayPath = "设备存储",
                parentPath = null,
                entries = roots
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.absolutePath })
                    .map { it.toEntry() }
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

    internal fun localRootCandidates(
        preferredRootPaths: List<String> = preferredStorageRootPaths,
        discoverRootPaths: List<String> = discoverableStorageRootPaths,
        externalStorage: String? = System.getenv("EXTERNAL_STORAGE"),
        secondaryStorage: String? = System.getenv("SECONDARY_STORAGE"),
        systemRoots: Array<File> = File.listRoots(),
        listDirectoryChildren: (File) -> Array<File>? = { it.listFiles() },
    ): List<File> {
        val roots = linkedSetOf<File>()
        preferredRootPaths.mapTo(roots) { File(it) }

        externalStorage
            ?.takeIf { it.isNotBlank() }
            ?.let { path -> roots += File(path) }

        secondaryStorage
            ?.split(File.pathSeparator)
            .orEmpty()
            .filter { it.isNotBlank() }
            .forEach { roots += File(it) }

        discoverRootPaths
            .flatMap { rootPath -> listDirectoryChildren(File(rootPath)).orEmpty().asIterable() }
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .filter { it.name !in hiddenDirectoryNames }
            .forEach { roots += it }

        val discoveredRoots = roots
            .filter { it.exists() && it.isDirectory && it.canRead() }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.absolutePath })
        if (discoveredRoots.isNotEmpty()) {
            return discoveredRoots
        }

        return systemRoots
            .asSequence()
            .filter { it.exists() && it.isDirectory && it.canRead() }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.absolutePath })
            .toList()
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

    private val preferredStorageRootPaths = listOf(
        "/storage/emulated/0",
        "/sdcard",
        "/storage",
        "/mnt/media_rw",
        "/mnt/sdcard",
        "/mnt/usb_storage",
        "/storage/usb_storage"
    )

    private val discoverableStorageRootPaths = listOf(
        "/storage",
        "/mnt/media_rw",
        "/mnt/usb_storage"
    )
}
