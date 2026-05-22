package com.miruplay.tv.data.logging

import android.content.Context
import com.miruplay.tv.core.common.logging.MiruLogRecord
import com.miruplay.tv.core.common.logging.MiruLogSink
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
    private val lock = ReentrantLock()
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    init {
        _pendingCount.value = lock.withLock { countPendingLocked() }
    }

    override fun log(record: MiruLogRecord) {
        lock.withLock {
            rotateIfNeeded()
            pendingFile.appendText(json.encodeToString(record) + "\n", Charsets.UTF_8)
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

    private fun decodeRecord(line: String): MiruLogRecord? =
        runCatching { json.decodeFromString<MiruLogRecord>(line) }.getOrNull()

    companion object {
        private const val MAX_PENDING_BYTES = 2L * 1024L * 1024L
    }
}
