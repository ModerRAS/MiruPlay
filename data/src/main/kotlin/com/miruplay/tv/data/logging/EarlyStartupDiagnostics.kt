package com.miruplay.tv.data.logging

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun interface StartupDiagnosticsWriter {
    fun append(line: String)
}

class EarlyStartupDiagnosticsRecorder(
    context: Context,
    private val writer: StartupDiagnosticsWriter = ExternalStartupDiagnosticsWriter(
        context.applicationContext
    ),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val processId: () -> Int = { Process.myPid() },
    sessionId: () -> String = { UUID.randomUUID().toString() },
) {
    private val appContext = context.applicationContext
    private val sessionIdValue = sessionId()

    fun checkpoint(
        checkpoint: String,
        attributes: Map<String, String> = emptyMap(),
    ) {
        write(
            event = "checkpoint",
            checkpoint = checkpoint,
            throwable = null,
            attributes = attributes,
        )
    }

    fun fatal(
        checkpoint: String,
        throwable: Throwable,
        attributes: Map<String, String> = emptyMap(),
    ) {
        write(
            event = "fatal",
            checkpoint = checkpoint,
            throwable = throwable,
            attributes = attributes,
        )
    }

    private fun write(
        event: String,
        checkpoint: String,
        throwable: Throwable?,
        attributes: Map<String, String>,
    ) {
        runCatching {
            writer.append(recordLine(event, checkpoint, throwable, attributes))
        }
    }

    private fun recordLine(
        event: String,
        checkpoint: String,
        throwable: Throwable?,
        attributes: Map<String, String>,
    ): String {
        val packageInfo = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }.getOrNull()
        val payload = buildJsonObject {
            put("event", event)
            put("checkpoint", checkpoint.sanitized())
            put("timestampMs", clock())
            put("sessionId", sessionIdValue.sanitized())
            put("processId", processId().toString())
            put("packageName", appContext.packageName.sanitized())
            put("versionName", packageInfo?.versionName.orEmpty().sanitized())
            put("versionCode", (packageInfo?.longVersionCode ?: 0L).toString())
            put("androidSdk", Build.VERSION.SDK_INT.toString())
            put("androidRelease", Build.VERSION.RELEASE.orEmpty().sanitized())
            put("deviceManufacturer", Build.MANUFACTURER.orEmpty().sanitized())
            put("deviceModel", Build.MODEL.orEmpty().sanitized())
            put("deviceProduct", Build.PRODUCT.orEmpty().sanitized())
            put("attributes", buildJsonObject {
                attributes
                    .filterKeys { it.isNotBlank() }
                    .forEach { (key, value) ->
                        put(key.take(MAX_ATTRIBUTE_KEY_LENGTH), value.sanitized())
                    }
            })
            if (throwable != null) {
                put("throwableClass", throwable.javaClass.name.sanitized())
                put("throwableMessage", throwable.message.orEmpty().sanitized())
                put("stackTrace", throwable.stackTraceString().sanitized(MAX_STACK_TRACE_LENGTH))
            }
        }
        return json.encodeToString(payload) + "\n"
    }

    private fun Throwable.stackTraceString(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun String.sanitized(maxLength: Int = MAX_VALUE_LENGTH): String =
        redactSensitive().take(maxLength)

    private fun String.redactSensitive(): String {
        var value = this
        sensitiveQueryParamRegexes.forEach { regex ->
            value = regex.replace(value) { match -> "${match.groupValues[1]}<redacted>" }
        }
        value = authorizationRegex.replace(value) { match -> "${match.groupValues[1]}<redacted>" }
        return urlUserInfoRegex.replace(value) { match -> "${match.groupValues[1]}<redacted>@" }
    }

    companion object {
        private val json = Json { encodeDefaults = true }
        private const val MAX_VALUE_LENGTH = 2_000
        private const val MAX_STACK_TRACE_LENGTH = 12_000
        private const val MAX_ATTRIBUTE_KEY_LENGTH = 80

        private val sensitiveQueryParamRegexes = listOf(
            Regex("""(?i)([?&](?:access[_-]?token|api[_-]?key|token|password|passwd|secret)=)[^&#\s]+"""),
            Regex("""(?i)(\b(?:access[_-]?token|api[_-]?key|token|password|passwd|secret)\s*[:=]\s*)[^\s,;]+"""),
        )
        private val authorizationRegex =
            Regex("""(?i)(\bAuthorization\s*[:=]\s*)(?:Basic|Bearer)\s+[A-Za-z0-9._~+/=-]+""")
        private val urlUserInfoRegex = Regex("""(?i)(https?://)[^/@\s]+@""")
    }
}

internal class ExternalStartupDiagnosticsWriter(
    private val context: Context,
) : StartupDiagnosticsWriter {
    override fun append(line: String) {
        var wrote = false
        runCatching {
            appendAppExternal(line)
            wrote = true
        }
        runCatching {
            appendPublicDownload(line)
            wrote = true
        }
        check(wrote) { "startup diagnostics could not be written" }
    }

    private fun appendAppExternal(line: String) {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val diagnosticDir = File(baseDir, "MiruPlay").apply { mkdirs() }
        File(diagnosticDir, DIAGNOSTIC_FILE_NAME).appendText(line, Charsets.UTF_8)
    }

    private fun appendPublicDownload(line: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appendPublicDownloadWithMediaStore(line)
        } else {
            val downloadDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val diagnosticDir = File(downloadDir, PUBLIC_DIRECTORY_NAME).apply { mkdirs() }
            File(diagnosticDir, DIAGNOSTIC_FILE_NAME).appendText(line, Charsets.UTF_8)
        }
    }

    private fun appendPublicDownloadWithMediaStore(line: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DIRECTORY_NAME/"
        val existingUri = resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf(DIAGNOSTIC_FILE_NAME, relativePath),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContentUris.withAppendedId(collection, cursor.getLong(0))
            } else {
                null
            }
        }
        val targetUri = existingUri ?: resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, DIAGNOSTIC_FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 0)
            },
        )
        checkNotNull(targetUri) { "could not create startup diagnostics download item" }
        resolver.openOutputStream(targetUri, "wa")?.use { stream ->
            stream.write(line.toByteArray(Charsets.UTF_8))
        } ?: error("could not open startup diagnostics download item")
    }

    companion object {
        private const val PUBLIC_DIRECTORY_NAME = "MiruPlay"
        private const val DIAGNOSTIC_FILE_NAME = "miruplay-startup-diagnostics.jsonl"
    }
}
