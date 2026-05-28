package com.miruplay.tv.data.logging

import android.content.Context
import com.miruplay.tv.core.common.logging.MiruLogRecord
import com.miruplay.tv.core.common.logging.MiruLogSink
import com.miruplay.tv.repository.LocalLogSnapshot
import com.miruplay.tv.repository.MAX_LOCAL_LOG_READ_LIMIT
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class LocalLogStore @Inject constructor(
    @ApplicationContext context: Context
) : MiruLogSink {
    private val logDir = File(context.filesDir, "logs").apply { mkdirs() }
    private val pendingFile = File(logDir, "miruplay-pending.jsonl")
    private val rotatedFile = File(logDir, "miruplay-pending.old.jsonl")
    private val recentFile = File(logDir, "miruplay-recent.jsonl")
    private val lock = ReentrantLock()
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    init {
        lock.withLock { seedRecentFileIfMissing() }
        _pendingCount.value = lock.withLock { countPendingLocked() }
    }

    override fun log(record: MiruLogRecord) {
        lock.withLock {
            rotateIfNeeded()
            val line = json.encodeToString(record) + "\n"
            pendingFile.appendText(line, Charsets.UTF_8)
            recentFile.appendText(line, Charsets.UTF_8)
            trimRecentIfNeeded()
            _pendingCount.value = countPendingLocked()
        }
    }

    fun pendingCount(): Int = lock.withLock {
        countPendingLocked()
    }

    fun readBatch(limit: Int): List<MiruLogRecord> = lock.withLock {
        val records = mutableListOf<MiruLogRecord>()
        for (file in queueFiles()) {
            if (records.size >= limit) break
            file.useLines(Charsets.UTF_8) { lines ->
                lines
                    .filter { it.isNotBlank() }
                    .take(limit - records.size)
                    .mapNotNullTo(records) { line -> decodeRecord(line) }
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
                    val content = file.readText(Charsets.UTF_8)
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
        lock.withLock {
            queueFiles().forEach { file -> removeUploadedFromFile(file, uploadedIds) }
            _pendingCount.value = countPendingLocked()
        }
    }

    private fun rotateIfNeeded() {
        if (pendingFile.length() <= MAX_PENDING_BYTES) return
        if (rotatedFile.exists()) return
        pendingFile.renameTo(rotatedFile)
    }

    private fun trimRecentIfNeeded() {
        if (recentFile.length() <= MAX_RECENT_BYTES) return
        val retained = recentFile.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
            .takeLast(MAX_RECENT_RECORDS)
        recentFile.writeText(
            retained.joinToString(separator = "\n", postfix = if (retained.isEmpty()) "" else "\n"),
            Charsets.UTF_8,
        )
    }

    private fun countPendingLocked(): Int {
        return queueFiles().sumOf { file ->
            file.useLines(Charsets.UTF_8) { lines ->
                lines.count { it.isNotBlank() }
            }
        }
    }

    private fun removeUploadedFromFile(file: File, uploadedIds: Set<String>) {
        if (!file.exists()) return
        val remaining = file.readLines(Charsets.UTF_8)
            .filter { line ->
                val record = decodeRecord(line)
                record == null || record.id !in uploadedIds
            }
        if (remaining.isEmpty()) {
            file.delete()
        } else {
            file.writeText(remaining.joinToString(separator = "\n") + "\n", Charsets.UTF_8)
        }
    }

    private fun queueFiles(): List<File> =
        listOf(rotatedFile, pendingFile).filter { it.exists() }

    private fun localLogFiles(): List<File> =
        if (recentFile.exists()) {
            listOf(recentFile)
        } else {
            queueFiles()
        }

    private fun seedRecentFileIfMissing() {
        if (recentFile.exists()) return
        val seeded = queueFiles()
            .joinToString(separator = "") { file ->
                val content = file.readText(Charsets.UTF_8)
                if (content.isBlank() || content.endsWith("\n")) content else "$content\n"
            }
        if (seeded.isNotBlank()) {
            recentFile.writeText(seeded, Charsets.UTF_8)
            trimRecentIfNeeded()
        }
    }

    private fun readRecords(file: File): List<MiruLogRecord> {
        if (!file.exists()) return emptyList()
        return file.useLines(Charsets.UTF_8) { lines ->
            lines
                .filter { it.isNotBlank() }
                .mapNotNull { line -> decodeRecord(line) }
                .toList()
        }
    }

    private fun decodeRecord(line: String): MiruLogRecord? =
        runCatching { json.decodeFromString<MiruLogRecord>(line) }.getOrNull()

    companion object {
        private const val MAX_PENDING_BYTES = 2L * 1024L * 1024L
        private const val MAX_RECENT_BYTES = 4L * 1024L * 1024L
        private const val MAX_RECENT_RECORDS = 1_000
    }
}
