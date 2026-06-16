package com.miruplay.tv.scraper.core

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.logging.PerformanceLog
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.ScraperResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

@Serializable
data class BangumiArchiveLatest(
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val digest: String? = null,
    val name: String,
    val size: Long = 0L,
)

data class BangumiArchiveSnapshot(
    val latest: BangumiArchiveLatest?,
    val subjectFile: File,
    val subjectFileSizeBytes: Long,
) {
    val hasSubjectData: Boolean = subjectFile.isFile && subjectFileSizeBytes > 0L
}

class BangumiArchiveClient(
    private val latestUrl: String = DEFAULT_LATEST_URL,
    private val userAgent: String = BangumiApiClient.DEFAULT_USER_AGENT,
    client: OkHttpClient = defaultClient(),
) {
    private val client = BangumiProxyAwareOkHttpClient(client)
    private val json = Json { ignoreUnknownKeys = true }

    fun configureProxy(proxyConfig: BangumiHttpProxyConfig) {
        client.configureProxy(proxyConfig)
    }

    fun fetchLatest(): BangumiArchiveLatest {
        val request = Request.Builder()
            .url(latestUrl)
            .addHeader("User-Agent", userAgent)
            .addHeader("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${body.ifBlank { response.message }}")
            }
            if (body.isBlank()) throw IllegalStateException("Empty Bangumi Archive latest.json")
            return json.decodeFromString(BangumiArchiveLatest.serializer(), body)
        }
    }

    fun downloadZip(
        latest: BangumiArchiveLatest,
        destination: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        val request = Request.Builder()
            .url(latest.browserDownloadUrl)
            .addHeader("User-Agent", userAgent)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throw IllegalStateException("HTTP ${response.code}: ${body.ifBlank { response.message }}")
            }
            val body = response.body ?: throw IllegalStateException("Empty Bangumi Archive download")
            destination.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            var bytesRead = 0L
            val totalBytes = latest.size.takeIf { it > 0L } ?: body.contentLength()
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytesRead += read
                        onProgress(bytesRead, totalBytes)
                    }
                }
            }

            validateDigest(latest, digest)
        }
    }

    fun downloadSubjectJsonlines(
        latest: BangumiArchiveLatest,
        destination: File,
        subjectFileName: String,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        val request = Request.Builder()
            .url(latest.browserDownloadUrl)
            .addHeader("User-Agent", userAgent)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throw IllegalStateException("HTTP ${response.code}: ${body.ifBlank { response.message }}")
            }
            val body = response.body ?: throw IllegalStateException("Empty Bangumi Archive download")
            val digest = MessageDigest.getInstance("SHA-256")
            val totalBytes = latest.size.takeIf { it > 0L } ?: body.contentLength()
            val input = ProgressDigestInputStream(
                delegate = body.byteStream(),
                digest = digest,
                totalBytes = totalBytes,
                onProgress = onProgress,
            )

            var found = false
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.substringAfterLast('/') == subjectFileName) {
                        destination.parentFile?.mkdirs()
                        destination.outputStream().use { output -> zip.copyTo(output) }
                        found = true
                    } else {
                        zip.copyTo(DiscardOutputStream)
                    }
                    zip.closeEntry()
                }
                input.drainRemaining()
            }
            if (!found) throw IllegalStateException("$subjectFileName not found in Bangumi Archive zip")
            validateDigest(latest, digest)
        }
    }

    private fun validateDigest(latest: BangumiArchiveLatest, digest: MessageDigest) {
        latest.digest?.removePrefix("sha256:")?.takeIf { it.isNotBlank() }?.let { expected ->
            val actual = digest.digest().toHex()
            if (!actual.equals(expected, ignoreCase = true)) {
                throw IllegalStateException("Bangumi Archive digest mismatch")
            }
        }
    }

    companion object {
        const val DEFAULT_LATEST_URL = "https://raw.githubusercontent.com/bangumi/Archive/master/aux/latest.json"

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .build()
    }

    private class ProgressDigestInputStream(
        delegate: InputStream,
        private val digest: MessageDigest,
        private val totalBytes: Long,
        private val onProgress: (bytesRead: Long, totalBytes: Long) -> Unit,
    ) : FilterInputStream(delegate) {
        private var bytesRead = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                digest.update(value.toByte())
                reportProgress(1)
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) {
                digest.update(buffer, offset, read)
                reportProgress(read)
            }
            return read
        }

        private fun reportProgress(read: Int) {
            bytesRead += read
            onProgress(bytesRead, totalBytes)
        }

        fun drainRemaining() {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (read(buffer) >= 0) {
                // Keep draining so the SHA-256 check covers the whole zip, including the central directory.
            }
        }
    }

    private object DiscardOutputStream : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
    }
}

class BangumiArchiveStore(
    private val directory: File,
    private val client: BangumiArchiveClient = BangumiArchiveClient(),
) {
    val subjectFile: File = File(directory, SUBJECT_FILE_NAME)

    private val latestFile: File = File(directory, LATEST_FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun configureProxy(proxyConfig: BangumiHttpProxyConfig) {
        client.configureProxy(proxyConfig)
    }

    fun snapshot(): BangumiArchiveSnapshot =
        BangumiArchiveSnapshot(
            latest = readLatestOrNull(),
            subjectFile = subjectFile,
            subjectFileSizeBytes = subjectFile.takeIf { it.isFile }?.length() ?: 0L,
        )

    suspend fun downloadLatest(
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<BangumiArchiveSnapshot> = withContext(Dispatchers.IO) {
        var extractedFile: File? = null
        try {
            directory.mkdirs()
            deleteDownloadArtifacts()
            val latest = client.fetchLatest()
            if (isCurrent(latest)) {
                return@withContext Result.success(snapshot())
            }

            extractedFile = File(directory, "$SUBJECT_FILE_NAME.download")

            client.downloadSubjectJsonlines(latest, extractedFile, SUBJECT_FILE_NAME, onProgress)
            if (subjectFile.exists() && !subjectFile.delete()) {
                throw IllegalStateException("Unable to replace existing $SUBJECT_FILE_NAME")
            }
            if (!extractedFile.renameTo(subjectFile)) {
                throw IllegalStateException("Unable to move downloaded $SUBJECT_FILE_NAME into place")
            }
            latestFile.writeText(json.encodeToString(BangumiArchiveLatest.serializer(), latest))
            Result.success(snapshot())
        } catch (error: Exception) {
            extractedFile?.delete()
            Result.failure(AppError.ScrapingError.ApiError("Bangumi Archive", error.message ?: "Download failed"))
        }
    }

    suspend fun importLocalArchive(
        source: File,
        originalName: String = source.name,
    ): Result<BangumiArchiveSnapshot> = withContext(Dispatchers.IO) {
        importLocalArchiveFile(source, originalName)
    }

    suspend fun importArchiveStream(
        input: InputStream,
        originalName: String,
        contentLength: Long,
        maxBytes: Long = MAX_ARCHIVE_IMPORT_BYTES,
    ): Result<BangumiArchiveSnapshot> = withContext(Dispatchers.IO) {
        var uploadedFile: File? = null
        try {
            if (contentLength <= 0L) {
                throw IllegalStateException("上传文件为空")
            }
            if (contentLength > maxBytes) {
                throw IllegalStateException("上传文件过大，最大支持 ${maxBytes / 1024L / 1024L} MB")
            }

            directory.mkdirs()
            deleteDownloadArtifacts()
            uploadedFile = File(directory, "$SUBJECT_FILE_NAME.raw-upload")
            copyUploadToFile(input, uploadedFile, contentLength)

            importLocalArchiveFile(uploadedFile, originalName)
        } catch (error: Exception) {
            Result.failure(AppError.ScrapingError.ApiError("Bangumi Archive", error.message ?: "Import failed"))
        } finally {
            uploadedFile?.delete()
        }
    }

    private fun importLocalArchiveFile(
        source: File,
        originalName: String,
    ): Result<BangumiArchiveSnapshot> {
        var importedFile: File? = null
        return try {
            if (!source.isFile || source.length() <= 0L) {
                throw IllegalStateException("上传文件为空")
            }

            directory.mkdirs()
            deleteDownloadArtifacts()
            importedFile = File(directory, "$SUBJECT_FILE_NAME.upload")
            val isZip = source.hasZipHeader()
            if (isZip) {
                source.inputStream().use { input ->
                    extractSubjectJsonlinesFromZip(input, importedFile, SUBJECT_FILE_NAME)
                }
            } else {
                source.copyTo(importedFile, overwrite = true)
            }

            validateSubjectJsonlines(importedFile)
            replaceSubjectFile(importedFile)

            val importedAt = utcNowIsoString()
            latestFile.writeText(
                json.encodeToString(
                    BangumiArchiveLatest.serializer(),
                    BangumiArchiveLatest(
                        browserDownloadUrl = "manual://${originalName.ifBlank { "archive" }}",
                        contentType = if (isZip) "application/zip" else "application/x-jsonlines",
                        createdAt = importedAt,
                        updatedAt = importedAt,
                        name = originalName.ifBlank { "manual-upload" },
                        size = source.length(),
                    )
                )
            )
            Result.success(snapshot())
        } catch (error: Exception) {
            importedFile?.delete()
            Result.failure(AppError.ScrapingError.ApiError("Bangumi Archive", error.message ?: "Import failed"))
        }
    }

    private fun isCurrent(latest: BangumiArchiveLatest): Boolean =
        subjectFile.isFile && readLatestOrNull() == latest

    private fun replaceSubjectFile(replacement: File) {
        if (subjectFile.exists() && !subjectFile.delete()) {
            throw IllegalStateException("Unable to replace existing $SUBJECT_FILE_NAME")
        }
        if (!replacement.renameTo(subjectFile)) {
            throw IllegalStateException("Unable to move imported $SUBJECT_FILE_NAME into place")
        }
    }

    private fun validateSubjectJsonlines(file: File) {
        var checkedLines = 0
        file.useLines { lines ->
            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.isBlank()) return@forEachIndexed
                val record = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
                    ?: throw IllegalStateException("subject.jsonlines 第 ${index + 1} 行不是有效 JSON")
                val id = record["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                val type = record["type"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                if (id == null || type == null) {
                    throw IllegalStateException("subject.jsonlines 第 ${index + 1} 行缺少 Bangumi subject 字段")
                }
                checkedLines += 1
                if (checkedLines >= SUBJECT_VALIDATE_LINE_LIMIT) return@useLines
            }
        }
        if (checkedLines == 0) {
            throw IllegalStateException("subject.jsonlines 没有可用数据")
        }
    }

    private fun deleteDownloadArtifacts() {
        directory
            .listFiles { file -> file.isFile && file.name.endsWith(".download") }
            .orEmpty()
            .forEach { it.delete() }
    }

    private fun readLatestOrNull(): BangumiArchiveLatest? =
        runCatching {
            json.decodeFromString(BangumiArchiveLatest.serializer(), latestFile.readText())
        }.getOrNull()

    companion object {
        const val SUBJECT_FILE_NAME = "subject.jsonlines"
        const val MAX_ARCHIVE_IMPORT_BYTES = 2L * 1024L * 1024L * 1024L
        private const val LATEST_FILE_NAME = "latest.json"
        private const val SUBJECT_VALIDATE_LINE_LIMIT = 50
    }
}

class BangumiArchiveSubjectSearch(
    private val subjectFileProvider: () -> File,
    private val normalizeQuery: (String) -> String = { it },
    private val minimumConfidence: Float = 0.62f,
) {
    constructor(
        subjectFile: File,
        normalizeQuery: (String) -> String = { it },
        minimumConfidence: Float = 0.62f,
    ) : this({ subjectFile }, normalizeQuery, minimumConfidence)

    private val mapper = BangumiArchiveDocumentMapper(normalizeQuery)

    fun search(
        query: String,
        limit: Int = 10,
        minimumConfidence: Float = this.minimumConfidence,
    ): List<ScraperResult> = PerformanceLog.measure(
        tag = ARCHIVE_PERFORMANCE_TAG,
        operation = "bangumi.archive.search",
        attributes = archiveQueryAttributes(query) + mapOf(
            "limit" to limit.toString(),
            "minimum_confidence" to minimumConfidence.toString(),
        ),
    ) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return@measure emptyList()
        val subjectId = trimmedQuery.takeIf { it.all(Char::isDigit) }?.toIntOrNull()
        if (subjectId != null) {
            return@measure findById(trimmedQuery)?.let { subject ->
                listOf(subject.toArchiveHit(trimmedQuery, confidence = 1.0f).toScraperResult())
            }.orEmpty()
        }

        val requestedSeason = extractArchiveSeasonNumber(trimmedQuery)
            ?: extractArchiveSeasonNumber(normalizeArchiveIndexedText(trimmedQuery, normalizeQuery))
        readSubjects()
            .asSequence()
            .map { subject ->
                val match = mapper.matchSubject(subject, trimmedQuery)
                ArchiveHit(
                    subject = subject,
                    matchedTitle = match.title,
                    confidence = match.confidence,
                )
            }
            .adjustSeasonalConfidence(requestedSeason)
            .filter { it.confidence >= minimumConfidence }
            .sortedWith(
                compareByDescending<ArchiveHit> { it.confidence }
                    .thenBy { it.subject.rank ?: Int.MAX_VALUE }
                    .thenByDescending { it.subject.score ?: 0f }
                    .thenByDescending { it.subject.date.orEmpty() }
            )
            .take(limit.coerceAtLeast(1))
            .map(ArchiveHit::toScraperResult)
            .toList()
    }

    fun findById(animeId: String): BangumiArchiveSubject? = PerformanceLog.measure(
        tag = ARCHIVE_PERFORMANCE_TAG,
        operation = "bangumi.archive.find_by_id",
        attributes = mapOf("anime_id" to animeId),
    ) {
        val subjectId = animeId.toIntOrNull() ?: return@measure null
        readSubjects().firstOrNull { it.id == subjectId }
    }

    private fun readSubjects(): List<BangumiArchiveSubject> {
        val subjectFile = subjectFileProvider()
        if (!subjectFile.isFile) return emptyList()
        return subjectFile.useLines { lines ->
            lines.mapNotNull(mapper::parseSubject).toList()
        }
    }

    private fun BangumiArchiveSubject.toArchiveHit(
        query: String,
        confidence: Float,
    ): ArchiveHit =
        ArchiveHit(
            subject = this,
            matchedTitle = mapper.matchedTitle(this, query),
            confidence = confidence,
        )
}

private data class ArchiveHit(
    val subject: BangumiArchiveSubject,
    val matchedTitle: String,
    val confidence: Float,
)

private fun ArchiveHit.toScraperResult(): ScraperResult =
    ScraperResult(
        animeId = subject.id.toString(),
        title = subject.name,
        titleCn = subject.nameCn,
        matchedTitle = matchedTitle,
        confidence = confidence,
        source = com.miruplay.tv.model.ScraperSource.BANGUMI,
        fromLocalArchive = true,
    )

private fun Sequence<ArchiveHit>.adjustSeasonalConfidence(requestedSeason: Int?): Sequence<ArchiveHit> {
    val season = requestedSeason ?: return this
    val hits = toList()
    val hasExplicitSeasonHit = hits.any { hit ->
        hit.subject.hasSeason(season) && hit.confidence >= 0.9f
    }
    if (!hasExplicitSeasonHit) return hits.asSequence()

    return hits.asSequence().map { hit ->
        when {
            hit.subject.hasSeason(season) -> hit
            hit.subject.hasAnySeason() -> hit.copy(confidence = minOf(hit.confidence, 0.48f))
            else -> hit.copy(confidence = minOf(hit.confidence, 0.58f))
        }
    }
}

private fun BangumiArchiveSubject.hasSeason(season: Int): Boolean =
    archiveTitleVariants().any { extractArchiveSeasonNumber(it) == season }

private fun BangumiArchiveSubject.hasAnySeason(): Boolean =
    archiveTitleVariants().any { extractArchiveSeasonNumber(it) != null }

private fun BangumiArchiveSubject.archiveTitleVariants(): List<String> =
    buildList {
        add(name)
        nameCn?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(aliases)
    }.map(String::trim).filter(String::isNotBlank).distinct()

private fun File.hasZipHeader(): Boolean =
    inputStream().use { input ->
        val header = ByteArray(4)
        input.read(header) == header.size &&
            header[0] == 0x50.toByte() &&
            header[1] == 0x4b.toByte() &&
            header[2] == 0x03.toByte() &&
            header[3] == 0x04.toByte()
    }

private fun extractSubjectJsonlinesFromZip(
    input: InputStream,
    destination: File,
    subjectFileName: String,
) {
    var found = false
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && entry.name.substringAfterLast('/') == subjectFileName) {
                destination.parentFile?.mkdirs()
                destination.outputStream().use { output -> zip.copyTo(output) }
                found = true
            }
            zip.closeEntry()
        }
    }
    if (!found) throw IllegalStateException("$subjectFileName not found in Bangumi Archive zip")
}

private fun copyUploadToFile(
    input: InputStream,
    destination: File,
    contentLength: Long,
) {
    destination.parentFile?.mkdirs()
    var copied = 0L
    destination.outputStream().use { output ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (copied < contentLength) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), contentLength - copied).toInt())
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read
        }
    }
    if (copied != contentLength) {
        throw IllegalStateException("上传文件读取不完整")
    }
}

private fun utcNowIsoString(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date())

private const val ARCHIVE_PERFORMANCE_TAG = "BangumiPerformance"

private fun archiveQueryAttributes(query: String): Map<String, String> =
    mapOf(
        "query_length" to query.length.toString(),
        "query_hash" to Integer.toHexString(query.hashCode()),
    )

data class BangumiArchiveSubject(
    val id: Int,
    val name: String,
    val nameCn: String?,
    val summary: String? = null,
    val aliases: List<String>,
    val platform: String?,
    val date: String?,
    val episodeCount: Int = 0,
    val score: Float?,
    val rank: Int?,
)

fun BangumiArchiveSubject.toAnime(): Anime =
    Anime(
        id = id.toString(),
        title = name,
        titleCn = nameCn,
        summary = summary.orEmpty(),
        episodeCount = episodeCount,
        airDate = date,
        rating = score ?: 0f,
        bangumiId = id,
    )

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { "%02x".format(Locale.US, it.toInt() and 0xff) }
