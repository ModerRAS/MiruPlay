package com.miruplay.tv.scanner

import android.graphics.BitmapFactory
import android.util.Log
import com.miruplay.tv.mediasource.MediaSource
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal data class MlipArtworkPack(
    val id: Long,
    val path: String,
    val sha256: String,
    val size: Long,
    val assetCount: Int,
)

internal data class MlipArtworkAsset(
    val id: Long,
    val packId: Long,
    val sha256: String,
    val memberName: String,
    val mediaType: String,
    val width: Int?,
    val height: Int?,
    val dataOffset: Long,
    val length: Long,
) {
    val extension: String = memberName.substringAfterLast('.', "").lowercase()
}

internal data class MlipArtworkBinding(
    val path: String?,
    val asset: MlipArtworkAsset?,
    val ownerKind: String = "series",
    val ownerId: Long = 0L,
    val artworkKind: Int = 1,
    val sourceProvider: Int? = null,
    val sourceSubjectId: String? = null,
    val sourceUrl: String? = null,
    val downloadedAt: String? = null,
)

internal class ArtworkPackCache(
    private val cacheRoot: File,
    private val sourceId: Long,
) {
    private val sourceRoot = File(cacheRoot, "mlip/$sourceId")
    private val packsDirectory = File(sourceRoot, "packs")
    private val artworkDirectory = File(sourceRoot, "artwork")

    suspend fun cache(
        mediaSource: MediaSource,
        bindings: Collection<MlipArtworkBinding>,
        packs: Map<Long, MlipArtworkPack>,
    ): Map<Long, String> {
        val requiredAssets = bindings.mapNotNull(MlipArtworkBinding::asset).distinctBy(MlipArtworkAsset::id)
        if (requiredAssets.isEmpty()) return emptyMap()
        packsDirectory.mkdirs()
        artworkDirectory.mkdirs()

        // One prewarm per v4 scan, including scans satisfied entirely by local cache.
        runCatching { mediaSource.listFiles(ARTWORK_DIRECTORY) }
        val cached = requiredAssets.mapNotNull { asset ->
            validCachedAsset(asset)?.let { asset.id to it.absolutePath }
        }.toMap().toMutableMap()
        val missingByPack = requiredAssets
            .filterNot { it.id in cached }
            .groupBy(MlipArtworkAsset::packId)
        if (missingByPack.isEmpty()) return cached

        for ((packId, assets) in missingByPack) {
            val pack = packs[packId] ?: continue
            runCatching { cachePack(mediaSource, pack, assets) }
                .onFailure { error -> Log.w(TAG, "MLIP artwork pack ${pack.sha256} was rejected", error) }
                .getOrDefault(emptyMap())
                .forEach { (assetId, path) -> cached[assetId] = path }
        }
        return cached
    }

    private fun validCachedAsset(asset: MlipArtworkAsset): File? {
        val file = assetFile(asset)
        if (!file.isFile || file.length() != asset.length) return null
        if (!file.sha256().equals(asset.sha256, ignoreCase = true)) return null
        return file.takeIf { validateImage(it, asset) }
    }

    private suspend fun cachePack(
        mediaSource: MediaSource,
        pack: MlipArtworkPack,
        requiredAssets: List<MlipArtworkAsset>,
    ): Map<Long, String> {
        require(pack.sha256.isSha256()) { "Invalid pack SHA-256" }
        require(pack.assetCount > 0) { "Artwork pack has no assets" }
        require(
            pack.size in 1..STANDARD_PACK_LIMIT_BYTES ||
                (pack.assetCount == 1 && pack.size <= MAX_OVERSIZE_PACK_BYTES)
        ) { "Unsafe artwork pack size: ${pack.size}" }
        val remotePath = normalizePackPath(pack.path)
        val packTemp = File.createTempFile(pack.sha256.lowercase(), ".tmp", packsDirectory)
        val stream = mediaSource.openStream(remotePath).getOrNull() ?: return emptyMap()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            stream.use { input ->
                packTemp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_OVERSIZE_PACK_BYTES) { "Artwork pack exceeds safety limit" }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
            require(total == pack.size) { "Artwork pack length mismatch" }
            require(digest.hex().equals(pack.sha256, ignoreCase = true)) { "Artwork pack hash mismatch" }
            val extracted = extractRequiredAssets(packTemp, pack.assetCount, requiredAssets)
            val marker = File(packsDirectory, "${pack.sha256.lowercase()}.complete")
            val markerTemp = File.createTempFile(pack.sha256.lowercase(), ".complete.tmp", packsDirectory)
            try {
                markerTemp.writeText(pack.sha256.lowercase())
                replaceAtomically(markerTemp, marker)
            } finally {
                markerTemp.delete()
            }
            return extracted
        } finally {
            packTemp.delete()
        }
    }

    private fun extractRequiredAssets(
        tarFile: File,
        expectedMemberCount: Int,
        requiredAssets: List<MlipArtworkAsset>,
    ): Map<Long, String> {
        val expected = requiredAssets.associateBy(MlipArtworkAsset::memberName)
        val extracted = mutableMapOf<Long, String>()
        var memberCount = 0
        RandomAccessFile(tarFile, "r").use { tar ->
            require(tar.length() % TAR_BLOCK_BYTES == 0L) { "Artwork tar is not block aligned" }
            var totalDeclared = 0L
            var zeroBlocks = 0
            while (tar.filePointer + TAR_BLOCK_BYTES <= tar.length()) {
                val headerOffset = tar.filePointer
                val header = ByteArray(TAR_BLOCK_BYTES)
                tar.readFully(header)
                if (header.all { it == 0.toByte() }) {
                    zeroBlocks += 1
                    if (zeroBlocks >= 2) {
                        while (tar.filePointer < tar.length()) {
                            tar.readFully(header)
                            require(header.all { it == 0.toByte() }) { "Artwork tar has nonzero trailing data" }
                        }
                        break
                    }
                    continue
                }
                require(zeroBlocks == 0) { "Artwork tar has an invalid trailer" }
                memberCount += 1
                require(memberCount <= MAX_TAR_MEMBERS) { "Too many tar members" }
                validateTarChecksum(header)
                val name = header.tarString(0, 100)
                require(name.isSafeTarMember()) { "Unsafe tar member: $name" }
                val type = header[156].toInt().toChar()
                require(type == '\u0000' || type == '0') { "Non-regular tar member: $name" }
                val size = header.tarOctal(124, 12)
                require(size in 0..MAX_ASSET_BYTES) { "Unsafe tar member size: $size" }
                totalDeclared += size
                require(totalDeclared <= MAX_EXTRACTED_BYTES) { "Tar extraction limit exceeded" }
                val dataOffset = headerOffset + TAR_BLOCK_BYTES
                expected[name]?.let { asset ->
                    require(size == asset.length) { "Artwork asset length mismatch" }
                    require(asset.dataOffset == dataOffset) { "Artwork asset offset mismatch" }
                    extracted[asset.id] = extractAsset(tar, asset)
                }
                tar.seek(dataOffset + alignedTarSize(size))
            }
            require(zeroBlocks >= 2) { "Artwork tar is missing its two-block trailer" }
        }
        require(memberCount == expectedMemberCount) { "Artwork pack member count mismatch" }
        require(extracted.keys.containsAll(requiredAssets.map(MlipArtworkAsset::id))) {
            "Artwork pack is missing required assets"
        }
        return extracted
    }

    private fun extractAsset(tar: RandomAccessFile, asset: MlipArtworkAsset): String {
        require(asset.sha256.isSha256()) { "Invalid asset SHA-256" }
        require(asset.memberName == "${asset.sha256.lowercase()}.${asset.extension}") { "Invalid asset member name" }
        require(asset.length in 1..MAX_ASSET_BYTES) { "Unsafe artwork asset length" }
        require(asset.extension in ALLOWED_EXTENSIONS) { "Unsupported artwork extension" }
        require(asset.mediaType.lowercase() in ALLOWED_MEDIA_TYPES) { "Unsupported artwork media type" }
        val output = assetFile(asset)
        val temp = File.createTempFile(asset.sha256.lowercase(), ".tmp", artworkDirectory)
        val digest = MessageDigest.getInstance("SHA-256")
        var remaining = asset.length
        try {
            temp.outputStream().use { target ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (remaining > 0L) {
                    val read = tar.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    require(read > 0) { "Truncated artwork asset" }
                    target.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    remaining -= read
                }
            }
            val actualHash = digest.hex()
            require(actualHash.equals(asset.sha256, ignoreCase = true)) {
                "Artwork asset hash mismatch: expected ${asset.sha256}, got $actualHash"
            }
            require(validateImage(temp, asset)) { "Artwork asset type or dimensions mismatch" }
            replaceAtomically(temp, output)
            return output.absolutePath
        } finally {
            temp.delete()
        }
    }

    private fun validateImage(file: File, asset: MlipArtworkAsset): Boolean {
        val prefix = file.inputStream().use { input -> ByteArray(12).also { input.read(it) } }
        val expectedType = when {
            prefix.size >= 3 && prefix[0] == 0xFF.toByte() && prefix[1] == 0xD8.toByte() &&
                prefix[2] == 0xFF.toByte() -> "image/jpeg"
            prefix.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE) -> "image/png"
            prefix.copyOfRange(0, 4).decodeToString() == "RIFF" &&
                prefix.copyOfRange(8, 12).decodeToString() == "WEBP" -> "image/webp"
            else -> return false
        }
        if (expectedType != asset.mediaType.lowercase()) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth !in 1..MAX_IMAGE_EDGE || bounds.outHeight !in 1..MAX_IMAGE_EDGE) return false
        if (asset.width != null && asset.width != bounds.outWidth) return false
        if (asset.height != null && asset.height != bounds.outHeight) return false
        return true
    }

    private fun assetFile(asset: MlipArtworkAsset): File =
        File(artworkDirectory, "${asset.sha256.lowercase()}.${asset.extension}")

    private fun normalizePackPath(path: String): String {
        val normalized = normalizeMlipRelativePath(path) ?: throw IllegalArgumentException("Unsafe pack path")
        return if (normalized.startsWith("$ARTWORK_DIRECTORY/")) normalized else "$ARTWORK_DIRECTORY/$normalized"
    }

    private companion object {
        private const val TAG = "ArtworkPackCache"
        private const val ARTWORK_DIRECTORY = "MLIP-Artwork"
        private const val TAR_BLOCK_BYTES = 512
        private const val MAX_TAR_MEMBERS = 4_096
        private const val STANDARD_PACK_LIMIT_BYTES = 96L * 1024L * 1024L
        private const val MAX_OVERSIZE_PACK_BYTES = 256L * 1024L * 1024L
        private const val MAX_ASSET_BYTES = 256L * 1024L * 1024L
        private const val MAX_EXTRACTED_BYTES = 256L * 1024L * 1024L
        private const val MAX_IMAGE_EDGE = 16_384
        private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        private val ALLOWED_MEDIA_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}

private fun String.isSha256(): Boolean = length == 64 && all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

private fun String.isSafeTarMember(): Boolean =
    isNotBlank() && '/' !in this && '\\' !in this && this != "." && this != ".." &&
        substringBeforeLast('.', "").isSha256()

private fun File.sha256(): String = inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    digest.hex()
}

private fun MessageDigest.hex(): String = digest().joinToString("") { byte -> "%02x".format(byte) }

private fun ByteArray.tarString(offset: Int, length: Int): String =
    copyOfRange(offset, offset + length).takeWhile { it != 0.toByte() }.toByteArray().decodeToString()

private fun ByteArray.tarOctal(offset: Int, length: Int): Long {
    val value = tarString(offset, length).trim()
    require(value.isNotEmpty() && value.all { it in '0'..'7' }) { "Invalid tar size" }
    return value.toLong(8)
}

private fun validateTarChecksum(header: ByteArray) {
    val expected = header.tarString(148, 8).trim().toLongOrNull(8)
        ?: throw IllegalArgumentException("Invalid tar checksum")
    val actual = header.indices.sumOf { index ->
        if (index in 148 until 156) 0x20 else header[index].toInt() and 0xFF
    }.toLong()
    require(expected == actual) { "Tar checksum mismatch" }
}

private fun replaceAtomically(source: File, target: File) {
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

private fun alignedTarSize(size: Long): Long = ((size + 511L) / 512L) * 512L
