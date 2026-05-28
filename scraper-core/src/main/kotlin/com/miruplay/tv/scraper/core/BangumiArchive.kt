package com.miruplay.tv.scraper.core

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import com.miruplay.tv.repository.BangumiMatchContext
import com.miruplay.tv.repository.BangumiSubjectMatchCandidate
import com.miruplay.tv.repository.BangumiSubjectMatcher
import com.miruplay.tv.repository.BangumiJsonMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
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

            latest.digest?.removePrefix("sha256:")?.takeIf { it.isNotBlank() }?.let { expected ->
                val actual = digest.digest().toHex()
                if (!actual.equals(expected, ignoreCase = true)) {
                    throw IllegalStateException("Bangumi Archive digest mismatch")
                }
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
        try {
            directory.mkdirs()
            val latest = client.fetchLatest()
            val zipFile = File(directory, "${latest.name}.download")
            val extractedFile = File(directory, "$SUBJECT_FILE_NAME.download")

            client.downloadZip(latest, zipFile, onProgress)
            extractSubjectJsonlines(zipFile, extractedFile)
            if (subjectFile.exists() && !subjectFile.delete()) {
                throw IllegalStateException("Unable to replace existing $SUBJECT_FILE_NAME")
            }
            if (!extractedFile.renameTo(subjectFile)) {
                throw IllegalStateException("Unable to move downloaded $SUBJECT_FILE_NAME into place")
            }
            latestFile.writeText(json.encodeToString(BangumiArchiveLatest.serializer(), latest))
            zipFile.delete()
            Result.success(snapshot())
        } catch (error: Exception) {
            Result.failure(AppError.ScrapingError.ApiError("Bangumi Archive", error.message ?: "Download failed"))
        }
    }

    private fun readLatestOrNull(): BangumiArchiveLatest? =
        runCatching {
            json.decodeFromString(BangumiArchiveLatest.serializer(), latestFile.readText())
        }.getOrNull()

    private fun extractSubjectJsonlines(zipFile: File, destination: File) {
        var found = false
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.substringAfterLast('/') == SUBJECT_FILE_NAME) {
                    destination.parentFile?.mkdirs()
                    destination.outputStream().use { output -> zip.copyTo(output) }
                    found = true
                    break
                }
            }
        }
        if (!found) throw IllegalStateException("$SUBJECT_FILE_NAME not found in Bangumi Archive zip")
    }

    companion object {
        const val SUBJECT_FILE_NAME = "subject.jsonlines"
        private const val LATEST_FILE_NAME = "latest.json"
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

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var cachedFile: File? = null
    private var cachedModifiedAt: Long = -1L
    private var cachedLength: Long = -1L
    private var cachedSubjects: List<BangumiArchiveSubject> = emptyList()

    fun search(query: String, limit: Int = 10): List<ScraperResult> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return emptyList()
        val subjects = loadSubjects(subjectFileProvider())
        if (subjects.isEmpty()) return emptyList()

        val normalizedQuery = normalizeQuery(trimmedQuery)
        val context = BangumiMatchContext.fromQueries(listOf(normalizedQuery))
        val ranked = BangumiSubjectMatcher.rank(
            context = context,
            candidates = subjects.map { subject ->
                BangumiSubjectMatchCandidate(
                    id = subject.id.toString(),
                    title = subject.name,
                    titleCn = subject.nameCn,
                    aliases = subject.aliases.map(normalizeQuery),
                    score = subject.score ?: 0f,
                    serverIndex = 1,
                    rank = subject.rank,
                    date = subject.date,
                )
            }
        )
        return ranked
            .filter { it.confidence >= minimumConfidence }
            .take(limit)
            .map { match ->
                ScraperResult(
                    animeId = match.candidate.id,
                    title = match.candidate.title,
                    titleCn = match.candidate.titleCn,
                    matchedTitle = match.candidate.titleCn ?: match.candidate.title,
                    confidence = match.confidence,
                    source = ScraperSource.BANGUMI,
                )
            }
    }

    private fun loadSubjects(file: File): List<BangumiArchiveSubject> {
        if (!file.isFile) return emptyList()
        val modifiedAt = file.lastModified()
        val length = file.length()
        synchronized(lock) {
            if (cachedFile == file && cachedModifiedAt == modifiedAt && cachedLength == length) {
                return cachedSubjects
            }
            val subjects = file.useLines { lines ->
                lines.mapNotNull { line ->
                    parseSubject(line)
                }.toList()
            }
            cachedFile = file
            cachedModifiedAt = modifiedAt
            cachedLength = length
            cachedSubjects = subjects
            return subjects
        }
    }

    private fun parseSubject(line: String): BangumiArchiveSubject? {
        if (line.isBlank()) return null
        val record = runCatching {
            json.decodeFromString(BangumiArchiveSubjectRecord.serializer(), line)
        }.getOrNull() ?: return null
        if (record.type != SUBJECT_TYPE_ANIME || record.name.isBlank()) return null

        val aliases = buildList {
            add(record.name)
            record.nameCn?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(record.infobox.extractInfoboxAliases())
            addAll(record.metaTags)
        }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

        return BangumiArchiveSubject(
            id = record.id,
            name = record.name,
            nameCn = record.nameCn?.ifBlank { null },
            aliases = aliases,
            platform = record.platform,
            date = record.date,
            score = record.score,
            rank = record.rank,
        )
    }

    companion object {
        private const val SUBJECT_TYPE_ANIME = 2
    }
}

data class BangumiArchiveSubject(
    val id: Int,
    val name: String,
    val nameCn: String?,
    val aliases: List<String>,
    val platform: String?,
    val date: String?,
    val score: Float?,
    val rank: Int?,
)

@Serializable
private data class BangumiArchiveSubjectRecord(
    val id: Int,
    val type: Int,
    val name: String,
    @SerialName("name_cn") val nameCn: String? = null,
    val infobox: JsonElement? = null,
    val platform: String? = null,
    val date: String? = null,
    val score: Float? = null,
    val rank: Int? = null,
    @SerialName("meta_tags") val metaTags: List<String> = emptyList(),
)

private fun JsonElement?.extractInfoboxAliases(): List<String> =
    when (this) {
        null -> emptyList()
        is JsonArray -> jsonArray.flatMap { it.extractInfoboxAliases() }
        is JsonObject -> extractStructuredInfoboxAliases()
        else -> jsonPrimitive.contentOrNull?.extractWikiAliases().orEmpty()
    }

private fun JsonObject.extractStructuredInfoboxAliases(): List<String> {
    val itemKey = stringValue("key")
    val itemValue = jsonObject["value"]
    if (itemKey != null && itemValue != null) {
        return if (itemKey in infoboxAliasKeys) itemValue.extractInfoboxValueAliases() else emptyList()
    }

    return jsonObject.flatMap { (key, value) ->
        if (key in infoboxAliasKeys) {
            value.extractInfoboxValueAliases()
        } else {
            value.extractInfoboxAliases()
        }
    }
}

private fun JsonElement?.extractInfoboxValueAliases(): List<String> =
    when (this) {
        null -> emptyList()
        is JsonArray -> jsonArray.flatMap { it.extractInfoboxValueAliases() }
        is JsonObject -> stringValue("v")?.extractStructuredAliases()
            ?: jsonObject.values.flatMap { it.extractInfoboxValueAliases() }
        else -> jsonPrimitive.contentOrNull?.extractStructuredAliases().orEmpty()
    }

private fun JsonObject.stringValue(key: String): String? =
    jsonObject[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

private fun String.extractWikiAliases(): List<String> =
    lines().flatMap { line ->
        val trimmed = line.trim().trimStart('|').trim()
        val key = trimmed.substringBefore('=', "").trim()
        if (key !in infoboxAliasKeys) return@flatMap emptyList()
        trimmed.substringAfter('=', "")
            .replace(Regex("""\{\{[^{}]*}}"""), " ")
            .replace(Regex("""\[\[([^]|]+)(?:\|[^]]+)?]]"""), "$1")
            .split('\n', ';', '；', '、')
            .map { it.trimWikiValue() }
            .filter { it.isNotBlank() }
    }

private fun String.extractStructuredAliases(): List<String> =
    split('\n', ';', '；', '、')
        .map { it.trimWikiValue() }
        .filter { it.isNotBlank() }

private fun String.trimWikiValue(): String =
    replace(Regex("""<[^>]*>"""), " ")
        .replace(Regex("""'{2,}"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '"', '\'', '[', ']', '{', '}')

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { "%02x".format(Locale.US, it.toInt() and 0xff) }

private val infoboxAliasKeys = setOf(
    "中文名",
    "别名",
    "其他名称",
    "英文名",
    "日文名",
)
