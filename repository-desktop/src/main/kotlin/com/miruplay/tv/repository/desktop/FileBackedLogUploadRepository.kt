package com.miruplay.tv.repository.desktop

import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.core.common.logging.MiruLogRecord
import com.miruplay.tv.core.common.logging.MiruLogSink
import com.miruplay.tv.repository.LocalLogSnapshot
import com.miruplay.tv.repository.LogUploadRepository
import com.miruplay.tv.repository.LogUploadStatus
import com.miruplay.tv.repository.MAX_LOCAL_LOG_READ_LIMIT
import com.miruplay.tv.repository.OtlpLogUploadConfig
import com.miruplay.tv.repository.OpenObserveLogConventions
import com.miruplay.tv.repository.OpenObservePayloadContext
import com.miruplay.tv.repository.DEFAULT_OTLP_LOG_UPLOAD_STREAM_NAME
import com.miruplay.tv.repository.normalizeOtlpLogUploadConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class FileBackedLogUploadRepository(
    private val store: DesktopRepositoryStore,
) : LogUploadRepository {
    private val credentials = FileBackedCredentialStore(store)
    private val queue = DesktopLocalLogQueue(
        baseDir = store.path().toAbsolutePath().normalize().parent
            ?: Paths.get(System.getProperty("user.home"), ".miruplay"),
        onChanged = ::refreshStatus,
    )
    private val httpClient: HttpClient = HttpClient.newHttpClient()
    private val lock = ReentrantLock()
    private val configFlow = MutableStateFlow(readConfig())

    private val _status = MutableStateFlow(currentStatus(isUploading = false))
    override val status: Flow<LogUploadStatus> = _status.asStateFlow()

    init {
        MiruLog.setSink(queue)
        refreshStatus()
    }

    override fun observeConfig(): Flow<OtlpLogUploadConfig> =
        configFlow.asStateFlow()

    override fun getConfig(): OtlpLogUploadConfig =
        configFlow.value

    override fun isTokenConfigured(): Boolean =
        !credentials.otlpAccessToken.isNullOrBlank()

    override suspend fun saveConfig(enabled: Boolean, endpoint: String, streamName: String) {
        val normalized = normalizeOtlpLogUploadConfig(
            enabled = enabled,
            endpoint = endpoint,
            streamName = streamName,
        )
        updateBlocking { state ->
            state.copy(
                otlpEnabled = normalized.enabled,
                otlpEndpoint = normalized.endpoint,
                otlpStreamName = normalized.streamName,
            )
        }
        refreshConfig()
        refreshStatus()
    }

    override suspend fun saveToken(token: String) {
        credentials.otlpAccessToken = token.trim()
        refreshStatus()
    }

    override suspend fun clearToken() {
        credentials.clearOtlpAccessToken()
        refreshStatus()
    }

    override suspend fun uploadPendingLogs(): LogUploadStatus {
        if (!lock.tryLock()) {
            return currentStatus(isUploading = true).also { _status.value = it }
        }
        try {
            return uploadPendingLogsLocked()
        } finally {
            lock.unlock()
            if (_status.value.isUploading) refreshStatus()
        }
    }

    override suspend fun readLocalLogs(limit: Int): LocalLogSnapshot =
        queue.readRecent(limit)

    override suspend fun exportLocalLogs(sinceTimestampMs: Long?): String =
        queue.exportJsonLines(sinceTimestampMs)

    private fun uploadPendingLogsLocked(): LogUploadStatus {
        val config = getConfig()
        val token = credentials.otlpAccessToken.orEmpty()
        if (!config.enabled) return currentStatus(isUploading = false)
        if (config.endpoint.isBlank()) return updateStatus("请填写 OpenObserve API 地址")
        if (token.isBlank()) return updateStatus("请填写 OpenObserve Token")

        var uploadedCount = 0
        var batchCount = 0
        _status.value = currentStatus(isUploading = true)

        while (true) {
            val records = queue.readBatch(MAX_UPLOAD_BATCH)
            if (records.isEmpty()) {
                return updateStatus(
                    if (uploadedCount > 0) {
                        "已上报 $uploadedCount 条日志"
                    } else {
                        "没有待上报日志"
                    }
                )
            }

            batchCount += 1
            when (val result = uploadBatch(config, token, records)) {
                is DesktopOtlpUploadResult.Success -> {
                    queue.removeUploaded(records.map { it.id }.toSet())
                    uploadedCount += records.size
                    _status.value = currentStatus(isUploading = true)
                }
                is DesktopOtlpUploadResult.Failed -> {
                    MiruLog.withoutSinkRecording {
                        MiruLog.w(
                            "LogUploadRepository",
                            "OpenObserve log upload failed",
                            attributes = mapOf(
                                "failure_message" to result.message,
                                "uploaded_count" to uploadedCount.toString(),
                                "completed_batches" to (batchCount - 1).toString(),
                            ),
                        )
                    }
                    return updateStatus(
                        if (uploadedCount > 0) {
                            "已上报 $uploadedCount 条日志，后续上报失败：${result.message}"
                        } else {
                            "上报失败：${result.message}"
                        }
                    )
                }
            }
        }
    }

    private fun uploadBatch(
        config: OtlpLogUploadConfig,
        token: String,
        records: List<MiruLogRecord>,
    ): DesktopOtlpUploadResult {
        if (records.isEmpty()) return DesktopOtlpUploadResult.Success(0)
        val endpoint = OpenObserveLogConventions.normalizeEndpoint(config.endpoint, config.streamName)
        val payload = json.encodeToString(
            OpenObserveLogConventions.buildJsonPayload(
                records = records,
                context = WINDOWS_PAYLOAD_CONTEXT,
            )
        )
        val request = HttpRequest.newBuilder(URI(endpoint))
            .header("Authorization", authorizationHeader(token))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()
        val response = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }.getOrElse { error ->
            return DesktopOtlpUploadResult.Failed(error.message ?: error::class.simpleName.orEmpty())
        }
        return if (response.statusCode() in 200..299) {
            DesktopOtlpUploadResult.Success(records.size)
        } else {
            val body = response.body().orEmpty().take(240)
            DesktopOtlpUploadResult.Failed(
                "HTTP ${response.statusCode()}${if (body.isBlank()) "" else ": $body"}"
            )
        }
    }

    private fun authorizationHeader(token: String): String =
        when {
            token.startsWith("Basic ", ignoreCase = true) -> token
            token.contains(':') -> {
                val encoded = Base64.getEncoder().encodeToString(token.toByteArray(Charsets.UTF_8))
                "Basic $encoded"
            }
            else -> "Basic $token"
        }

    private fun updateStatus(message: String): LogUploadStatus {
        val now = System.currentTimeMillis()
        updateBlocking { state ->
            state.copy(
                otlpLastUploadAt = now,
                otlpLastUploadStatus = message,
            )
        }
        refreshConfig()
        return currentStatus(isUploading = false).also { _status.value = it }
    }

    private fun refreshConfig() {
        configFlow.value = readConfig()
    }

    private fun refreshStatus() {
        _status.value = currentStatus(isUploading = lock.isLocked)
    }

    private fun currentStatus(isUploading: Boolean): LogUploadStatus {
        val config = getConfig()
        return LogUploadStatus(
            pendingCount = queue.pendingCount(),
            isUploading = isUploading,
            lastUploadAt = config.lastUploadAt,
            lastUploadStatus = config.lastUploadStatus,
            tokenConfigured = isTokenConfigured(),
        )
    }

    private fun <T> storeBlocking(block: (DesktopRepositoryState) -> T): T =
        kotlinx.coroutines.runBlocking { store.read(block) }

    private fun updateBlocking(block: (DesktopRepositoryState) -> DesktopRepositoryState) {
        kotlinx.coroutines.runBlocking { store.update { state -> block(state) to Unit } }
    }

    private fun readConfig(): OtlpLogUploadConfig =
        storeBlocking {
            OtlpLogUploadConfig(
                enabled = it.otlpEnabled,
                endpoint = it.otlpEndpoint,
                streamName = it.otlpStreamName.ifBlank { DEFAULT_OTLP_LOG_UPLOAD_STREAM_NAME },
                lastUploadAt = it.otlpLastUploadAt,
                lastUploadStatus = it.otlpLastUploadStatus,
            )
        }

    private companion object {
        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
        private const val MAX_UPLOAD_BATCH = 200
        private val WINDOWS_PAYLOAD_CONTEXT = OpenObservePayloadContext(
            serviceName = "miruplay-windows",
            deploymentEnvironment = "windows",
        )
    }
}

private sealed class DesktopOtlpUploadResult {
    data class Success(val uploadedCount: Int) : DesktopOtlpUploadResult()
    data class Failed(val message: String) : DesktopOtlpUploadResult()
}

private class DesktopLocalLogQueue(
    baseDir: Path,
    private val onChanged: () -> Unit = {},
) : MiruLogSink {
    private val lock = ReentrantLock()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val logDir: Path = baseDir.resolve("logs")
    private val pendingFile: Path = logDir.resolve("miruplay-pending.jsonl")
    private val rotatedFile: Path = logDir.resolve("miruplay-pending.old.jsonl")
    private val recentFile: Path = logDir.resolve("miruplay-recent.jsonl")

    init {
        Files.createDirectories(logDir)
        seedRecentFileIfMissing()
    }

    override fun log(record: MiruLogRecord) {
        var changed = false
        lock.withLock {
            rotateIfNeeded()
            val line = json.encodeToString(record) + "\n"
            Files.writeString(
                pendingFile,
                line,
                Charsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND,
            )
            Files.writeString(
                recentFile,
                line,
                Charsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND,
            )
            trimRecentIfNeeded()
            changed = true
        }
        if (changed) {
            onChanged()
        }
    }

    fun pendingCount(): Int = lock.withLock {
        queueFiles().sumOf { file ->
            Files.newBufferedReader(file, Charsets.UTF_8).useLines { lines ->
                lines.count { it.isNotBlank() }
            }
        }
    }

    fun readBatch(limit: Int): List<MiruLogRecord> = lock.withLock {
        val records = mutableListOf<MiruLogRecord>()
        queueFiles().forEach { file ->
            if (records.size >= limit) return@forEach
            Files.newBufferedReader(file, Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() }
                    .take(limit - records.size)
                    .mapNotNullTo(records, ::decodeRecord)
            }
        }
        records
    }

    fun readRecent(limit: Int = MAX_LOCAL_LOG_READ_LIMIT): LocalLogSnapshot = lock.withLock {
        val safeLimit = limit.coerceIn(1, MAX_LOCAL_LOG_READ_LIMIT)
        val records = localLogFiles()
            .flatMap { file -> readRecords(file) }
        LocalLogSnapshot(
            totalCount = records.size,
            records = records.takeLast(safeLimit),
        )
    }

    fun exportJsonLines(sinceTimestampMs: Long? = null): String = lock.withLock {
        if (sinceTimestampMs == null) {
            return@withLock localLogFiles()
                .joinToString(separator = "") { file ->
                    val content = Files.readString(file, Charsets.UTF_8)
                    if (content.isBlank() || content.endsWith("\n")) content else "$content\n"
                }
        }
        val records = localLogFiles()
            .flatMap { file -> readRecords(file) }
            .filter { record -> record.timestampMs >= sinceTimestampMs }
        if (records.isEmpty()) return@withLock ""
        records.joinToString(separator = "\n", postfix = "\n") { record ->
            json.encodeToString(record)
        }
    }

    fun removeUploaded(uploadedIds: Set<String>) {
        if (uploadedIds.isEmpty()) return
        var changed = false
        lock.withLock {
            queueFiles().forEach { file ->
                val remaining = Files.newBufferedReader(file, Charsets.UTF_8).useLines { lines ->
                    lines.filter { line ->
                        val record = decodeRecord(line)
                        record == null || record.id !in uploadedIds
                    }.toList()
                }
                if (remaining.isEmpty()) {
                    Files.deleteIfExists(file)
                } else {
                    Files.writeString(
                        file,
                        remaining.joinToString(separator = "\n", postfix = "\n"),
                        Charsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                    )
                }
                changed = true
            }
        }
        if (changed) {
            onChanged()
        }
    }

    private fun rotateIfNeeded() {
        if (!Files.exists(pendingFile)) return
        if (Files.size(pendingFile) <= MAX_PENDING_BYTES) return
        if (Files.exists(rotatedFile)) return
        Files.move(pendingFile, rotatedFile)
    }

    private fun queueFiles(): List<Path> =
        listOf(rotatedFile, pendingFile).filter(Files::exists)

    private fun localLogFiles(): List<Path> =
        if (Files.exists(recentFile)) {
            listOf(recentFile)
        } else {
            queueFiles()
        }

    private fun seedRecentFileIfMissing() {
        lock.withLock {
            if (Files.exists(recentFile)) return
            val seeded = queueFiles()
                .joinToString(separator = "") { file ->
                    val content = Files.readString(file, Charsets.UTF_8)
                    if (content.isBlank() || content.endsWith("\n")) content else "$content\n"
                }
            if (seeded.isNotBlank()) {
                Files.writeString(
                    recentFile,
                    seeded,
                    Charsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                )
                trimRecentIfNeeded()
            }
        }
    }

    private fun trimRecentIfNeeded() {
        if (!Files.exists(recentFile)) return
        if (Files.size(recentFile) <= MAX_RECENT_BYTES) return
        val retained = Files.newBufferedReader(recentFile, Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() }.toList().takeLast(MAX_RECENT_RECORDS)
        }
        Files.writeString(
            recentFile,
            retained.joinToString(separator = "\n", postfix = if (retained.isEmpty()) "" else "\n"),
            Charsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
    }

    private fun readRecords(file: Path): List<MiruLogRecord> {
        if (!Files.exists(file)) return emptyList()
        return Files.newBufferedReader(file, Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() }
                .mapNotNull(::decodeRecord)
                .toList()
        }
    }

    private fun decodeRecord(line: String): MiruLogRecord? =
        runCatching { json.decodeFromString<MiruLogRecord>(line) }.getOrNull()

    private companion object {
        private const val MAX_PENDING_BYTES = 2L * 1024L * 1024L
        private const val MAX_RECENT_BYTES = 4L * 1024L * 1024L
        private const val MAX_RECENT_RECORDS = 1_000
    }
}
