package com.miruplay.tv

import android.annotation.TargetApi
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class StartupProbeProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return true
        StartupProbe.installCrashHandler(appContext)
        StartupProbe.writeCheckpoint(appContext, "provider_on_create")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

internal object StartupProbe {
    private const val TAG = "StartupProbe"
    private const val PUBLIC_DIRECTORY_NAME = "MiruPlay"
    private const val DIAGNOSTIC_FILE_NAME = "miruplay-startup-probe.jsonl"
    private val crashHandlerInstalled = AtomicBoolean(false)

    fun installCrashHandler(context: Context) {
        if (!crashHandlerInstalled.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeFatal(
                context = appContext,
                checkpoint = "uncaught_exception",
                throwable = throwable,
                attributes = mapOf(
                    "probe" to "content_provider",
                    "phase" to "pre_application",
                    "thread_name" to thread.name,
                    "thread_id" to thread.id.toString(),
                    "thread_state" to thread.state.name,
                    "thread_priority" to thread.priority.toString(),
                    "is_main_thread" to (thread.name == "main").toString(),
                ),
            )
            previousHandler?.uncaughtException(thread, throwable) ?: run {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun writeCheckpoint(context: Context, checkpoint: String) {
        write(
            context = context,
            event = "checkpoint",
            checkpoint = checkpoint,
            attributes = mapOf(
                "probe" to "content_provider",
                "phase" to "pre_application",
            ),
        )
    }

    fun writeFatal(context: Context, checkpoint: String, throwable: Throwable) {
        writeFatal(
            context = context,
            checkpoint = checkpoint,
            throwable = throwable,
            attributes = mapOf(
                "probe" to "content_provider",
                "phase" to "pre_application",
            ),
        )
    }

    private fun writeFatal(
        context: Context,
        checkpoint: String,
        throwable: Throwable,
        attributes: Map<String, String>,
    ) {
        write(
            context = context,
            event = "fatal",
            checkpoint = checkpoint,
            attributes = attributes,
            throwable = throwable,
        )
    }

    private fun write(
        context: Context,
        event: String,
        checkpoint: String,
        attributes: Map<String, String>,
        throwable: Throwable? = null,
    ) {
        runCatching {
            val packageInfo = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
            val line = StartupProbeFormatter.recordLine(
                event = event,
                checkpoint = checkpoint,
                timestampMs = System.currentTimeMillis(),
                processId = Process.myPid(),
                packageName = context.packageName,
                versionName = packageInfo?.versionName.orEmpty(),
                versionCode = packageInfo?.longVersionCode ?: 0L,
                attributes = attributes,
                throwableClass = throwable?.javaClass?.name,
                throwableMessage = throwable?.message,
                stackTrace = throwable?.stackTraceString(),
            )
            appendAppExternal(context, line)
            appendPublicDownload(context, line)
            StartupProbeFormatter.summaryMarkerFileName(
                event = event,
                checkpoint = checkpoint,
                throwableClass = throwable?.javaClass?.name,
                throwableMessage = throwable?.message,
                stackTrace = throwable?.stackTraceString(),
            )?.let { markerFileName ->
                createAppExternalMarker(context, markerFileName)
                createPublicDownloadMarker(context, markerFileName)
            }
            Log.i(TAG, "Wrote startup probe checkpoint: $checkpoint")
        }.onFailure { error ->
            Log.w(TAG, "Startup probe write failed", error)
        }
    }

    private fun appendAppExternal(context: Context, line: String) {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val diagnosticDir = File(baseDir, PUBLIC_DIRECTORY_NAME).apply { mkdirs() }
        appendFileLine(File(diagnosticDir, DIAGNOSTIC_FILE_NAME), line)
    }

    private fun createAppExternalMarker(context: Context, markerFileName: String) {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val diagnosticDir = File(baseDir, PUBLIC_DIRECTORY_NAME).apply { mkdirs() }
        File(diagnosticDir, markerFileName).writeText("", Charsets.UTF_8)
    }

    private fun appendPublicDownload(context: Context, line: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appendPublicDownloadWithMediaStore(context, line)
            return
        }
        val downloadDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val diagnosticDir = File(downloadDir, PUBLIC_DIRECTORY_NAME).apply { mkdirs() }
        appendFileLine(File(diagnosticDir, DIAGNOSTIC_FILE_NAME), line)
    }

    private fun createPublicDownloadMarker(context: Context, markerFileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createPublicDownloadMarkerWithMediaStore(context, markerFileName)
            return
        }
        val downloadDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val diagnosticDir = File(downloadDir, PUBLIC_DIRECTORY_NAME).apply { mkdirs() }
        File(diagnosticDir, markerFileName).writeText("", Charsets.UTF_8)
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun appendPublicDownloadWithMediaStore(context: Context, line: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DIRECTORY_NAME/"
        val existingUri = findPublicDownloadUri(context, DIAGNOSTIC_FILE_NAME, relativePath)
        val targetUri = existingUri ?: resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, DIAGNOSTIC_FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 0)
            },
        )
        checkNotNull(targetUri) { "could not create startup probe download item" }
        resolver.openOutputStream(targetUri, "wa")?.use { stream ->
            stream.write(line.toByteArray(Charsets.UTF_8))
        } ?: error("could not open startup probe download item")
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun createPublicDownloadMarkerWithMediaStore(
        context: Context,
        markerFileName: String,
    ) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DIRECTORY_NAME/"
        val existingUri = findPublicDownloadUri(context, markerFileName, relativePath)
        val targetUri = existingUri ?: resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, markerFileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 0)
            },
        )
        checkNotNull(targetUri) { "could not create startup probe marker" }
        resolver.openOutputStream(targetUri, "wt")?.use { stream ->
            stream.write(ByteArray(0))
        } ?: error("could not open startup probe marker")
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun findPublicDownloadUri(
        context: Context,
        displayName: String,
        relativePath: String,
    ): Uri? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        return context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.RELATIVE_PATH),
            "${MediaStore.Downloads.DISPLAY_NAME}=?",
            arrayOf(displayName),
            null,
        )?.use { cursor ->
            var matchingId: Long? = null
            while (cursor.moveToNext()) {
                if (cursor.getString(1).orEmpty().equals(relativePath, ignoreCase = true)) {
                    matchingId = cursor.getLong(0)
                    break
                }
            }
            matchingId?.let { id -> ContentUris.withAppendedId(collection, id) }
        }
    }

    private fun appendFileLine(file: File, line: String) {
        FileOutputStream(file, true).use { stream ->
            stream.write(line.toByteArray(Charsets.UTF_8))
        }
    }

    private fun Throwable.stackTraceString(): String = stackTraceToString()
}

internal object StartupProbeFormatter {
    fun recordLine(
        event: String,
        checkpoint: String,
        timestampMs: Long,
        processId: Int,
        packageName: String,
        versionName: String,
        versionCode: Long,
        attributes: Map<String, String> = emptyMap(),
        throwableClass: String? = null,
        throwableMessage: String? = null,
        stackTrace: String? = null,
    ): String =
        buildString {
            append("{")
            appendField("event", event)
            append(",")
            appendField("checkpoint", checkpoint)
            append(",")
            appendNumberField("timestampMs", timestampMs)
            append(",")
            appendNumberField("processId", processId.toLong())
            append(",")
            appendField("packageName", packageName)
            append(",")
            appendField("versionName", versionName)
            append(",")
            appendNumberField("versionCode", versionCode)
            append(",")
            append("\"androidSdk\":")
            append(Build.VERSION.SDK_INT)
            append(",")
            appendField("androidRelease", Build.VERSION.RELEASE.orEmpty())
            append(",")
            appendField("deviceManufacturer", Build.MANUFACTURER.orEmpty())
            append(",")
            appendField("deviceModel", Build.MODEL.orEmpty())
            append(",")
            append("\"attributes\":{")
            attributes.entries
                .filter { it.key.isNotBlank() }
                .forEachIndexed { index, entry ->
                    if (index > 0) append(",")
                    appendField(entry.key.take(MAX_KEY_LENGTH), entry.value)
                }
            append("}")
            if (throwableClass != null) {
                append(",")
                appendField("throwableClass", throwableClass)
            }
            if (throwableMessage != null) {
                append(",")
                appendField("throwableMessage", throwableMessage)
            }
            if (stackTrace != null) {
                append(",")
                appendField("stackTrace", stackTrace.take(MAX_STACK_TRACE_LENGTH))
            }
            append("}\n")
        }

    fun summaryMarkerFileName(
        event: String,
        checkpoint: String,
        throwableClass: String? = null,
        throwableMessage: String? = null,
        stackTrace: String? = null,
    ): String? {
        val normalizedEvent = event.markerPart(maxLength = 24)
        val normalizedCheckpoint = checkpoint.markerPart(maxLength = 48)
        if (normalizedEvent.isBlank() || normalizedCheckpoint.isBlank()) return null

        val throwableSimpleName = throwableClass
            ?.substringAfterLast('.')
            ?.markerPart(maxLength = 48)
            .orEmpty()
        val firstStackFrame = stackTrace
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.markerPart(maxLength = 72)
            .orEmpty()
        val message = throwableMessage
            ?.markerPart(maxLength = 48)
            .orEmpty()
        val details = listOf(throwableSimpleName, firstStackFrame, message)
            .filter { it.isNotBlank() }
            .joinToString("-")
            .ifBlank { "no_details" }
            .take(MAX_MARKER_DETAILS_LENGTH)
            .trim('-')
        val baseName = "probe-${normalizedEvent}_${normalizedCheckpoint}-$details"
        return baseName
            .take(MAX_MARKER_FILE_NAME_LENGTH - MARKER_EXTENSION.length)
            .trimEnd('-', '_', '.')
            .plus(MARKER_EXTENSION)
    }

    private fun StringBuilder.appendField(key: String, value: String) {
        append("\"")
        append(key.escapeJson())
        append("\":\"")
        append(value.redactSensitive().escapeJson().take(MAX_VALUE_LENGTH))
        append("\"")
    }

    private fun StringBuilder.appendNumberField(key: String, value: Long) {
        append("\"")
        append(key.escapeJson())
        append("\":")
        append(value)
    }

    private fun String.escapeJson(): String =
        buildString {
            this@escapeJson.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }

    private fun String.redactSensitive(): String {
        var value = this
        sensitiveQueryParamRegexes.forEach { regex ->
            value = regex.replace(value) { match -> "${match.groupValues[1]}<redacted>" }
        }
        value = authorizationRegex.replace(value) { match -> "${match.groupValues[1]}<redacted>" }
        return urlUserInfoRegex.replace(value) { match -> "${match.groupValues[1]}<redacted>@" }
    }

    private fun String.markerPart(maxLength: Int): String =
        redactSensitive()
            .replace(Regex("""(?i)<redacted>"""), "redacted")
            .replace(Regex("""[^A-Za-z0-9]+"""), "_")
            .trim('_')
            .take(maxLength)

    private const val MAX_KEY_LENGTH = 80
    private const val MAX_VALUE_LENGTH = 2_000
    private const val MAX_STACK_TRACE_LENGTH = 12_000
    private const val MAX_MARKER_DETAILS_LENGTH = 144
    private const val MAX_MARKER_FILE_NAME_LENGTH = 220
    private const val MARKER_EXTENSION = ".marker"

    private val sensitiveQueryParamRegexes = listOf(
        Regex("""(?i)([?&](?:access[_-]?token|api[_-]?key|token|password|passwd|secret)=)[^&#\s]+"""),
        Regex("""(?i)(\b(?:access[_-]?token|api[_-]?key|token|password|passwd|secret)\s*[:=]\s*)[^\s,;]+"""),
    )
    private val authorizationRegex =
        Regex("""(?i)(\bAuthorization\s*[:=]\s*)(?:Basic|Bearer)\s+[A-Za-z0-9._~+/=-]+""")
    private val urlUserInfoRegex = Regex("""(?i)(https?://)[^/@\s]+@""")
}
