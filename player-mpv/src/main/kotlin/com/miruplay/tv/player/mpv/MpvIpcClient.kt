package com.miruplay.tv.player.mpv

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

interface MpvIpcController {
    suspend fun cyclePause(): Result<Unit>
    suspend fun setPaused(paused: Boolean): Result<Unit>
    suspend fun setSpeed(speed: Double): Result<Unit>
    suspend fun seekBy(seconds: Double, mode: MpvSeekMode = MpvSeekMode.RELATIVE_EXACT): Result<Unit>
    suspend fun quit(): Result<Unit>
    suspend fun getTimePositionSeconds(): Result<Double?>
    suspend fun getDurationSeconds(): Result<Double?>
    suspend fun getPaused(): Result<Boolean?>
    suspend fun getEofReached(): Result<Boolean?>
}

class MpvIpcClient(
    private val serverPath: String,
    private val connectTimeoutMillis: Long = 1_500L,
    private val retryDelayMillis: Long = 50L,
    private val transport: MpvIpcTransport = FileMpvIpcTransport(
        serverPath = serverPath,
        connectTimeoutMillis = connectTimeoutMillis,
        retryDelayMillis = retryDelayMillis,
    ),
) : MpvIpcController {
    override suspend fun cyclePause(): Result<Unit> =
        sendCommand("cycle".json, "pause".json)

    override suspend fun setPaused(paused: Boolean): Result<Unit> =
        sendCommand("set_property".json, "pause".json, JsonPrimitive(paused))

    override suspend fun setSpeed(speed: Double): Result<Unit> =
        sendCommand("set_property".json, "speed".json, JsonPrimitive(speed))

    override suspend fun seekBy(seconds: Double, mode: MpvSeekMode): Result<Unit> =
        sendCommand("seek".json, JsonPrimitive(seconds), mode.mpvValue.json)

    override suspend fun quit(): Result<Unit> =
        sendCommand("quit".json)

    override suspend fun getTimePositionSeconds(): Result<Double?> =
        getProperty("time-pos").map { data ->
            data?.jsonPrimitive?.doubleOrNull
        }

    override suspend fun getDurationSeconds(): Result<Double?> =
        getProperty("duration").map { data ->
            data?.jsonPrimitive?.doubleOrNull
        }

    override suspend fun getPaused(): Result<Boolean?> =
        getProperty("pause").map { data ->
            data?.jsonPrimitive?.booleanOrNull
        }

    override suspend fun getEofReached(): Result<Boolean?> =
        getProperty("eof-reached").map { data ->
            data?.jsonPrimitive?.booleanOrNull
        }

    suspend fun sendCommand(vararg command: JsonElement): Result<Unit> = withContext(Dispatchers.IO) {
        if (serverPath.isBlank()) {
            return@withContext Result.failure(AppError.PlaybackError.StreamError("mpv IPC server is not configured"))
        }

        val payload = buildJsonObject {
            put("command", JsonArray(command.toList()))
        }.toString()

        transport.send(payload)
    }

    suspend fun getProperty(name: String): Result<JsonElement?> = withContext(Dispatchers.IO) {
        if (serverPath.isBlank()) {
            return@withContext Result.failure(AppError.PlaybackError.StreamError("mpv IPC server is not configured"))
        }

        val payload = buildJsonObject {
            put("command", JsonArray(listOf("get_property".json, name.json)))
        }.toString()

        when (val response = transport.request(payload)) {
            is Result.Success -> runCatching {
                if (response.data.isBlank()) null else Json.parseToJsonElement(response.data).jsonObject["data"]
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { error ->
                    Result.failure(AppError.PlaybackError.StreamError(error.message ?: "Invalid mpv IPC response"))
                },
            )
            is Result.Error -> response
        }
    }

    private val String.json: JsonPrimitive
        get() = JsonPrimitive(this)
}

interface MpvIpcTransport {
    suspend fun send(payload: String): Result<Unit>
    suspend fun request(payload: String): Result<String>
}

private class FileMpvIpcTransport(
    private val serverPath: String,
    private val connectTimeoutMillis: Long,
    private val retryDelayMillis: Long,
) : MpvIpcTransport {
    override suspend fun send(payload: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            openWriterWithRetry().use { writer ->
                writer.write(payload)
                writer.newLine()
                writer.flush()
            }
            Result.success(Unit)
        }.getOrElse { error ->
            Result.failure(
                AppError.PlaybackError.StreamError(error.message ?: "Failed to send mpv IPC command")
            )
        }
    }

    override suspend fun request(payload: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            openWriterWithRetry().use { writer ->
                openReaderWithRetry().use { reader ->
                    writer.write(payload)
                    writer.newLine()
                    writer.flush()
                    val response = withTimeout(connectTimeoutMillis.coerceAtLeast(1L) * 4L) {
                        reader.readLine()
                    }
                    Result.success(response.orEmpty())
                }
            }
        }.getOrElse { error ->
            Result.failure(
                AppError.PlaybackError.StreamError(error.message ?: "Failed to read mpv IPC response")
            )
        }
    }

    private fun openWriterWithRetry(): java.io.BufferedWriter {
        val deadline = System.nanoTime() + connectTimeoutMillis.coerceAtLeast(0L) * 1_000_000L
        var lastError: IOException? = null
        while (true) {
            try {
                return Files.newBufferedWriter(
                    Paths.get(serverPath),
                    Charsets.UTF_8,
                    StandardOpenOption.WRITE,
                )
            } catch (error: IOException) {
                lastError = error
                if (System.nanoTime() >= deadline) throw error
                Thread.sleep(retryDelayMillis.coerceAtLeast(1L))
            }
        }
        throw lastError ?: IOException("Unable to open mpv IPC server: $serverPath")
    }

    private fun openReaderWithRetry(): java.io.BufferedReader {
        val deadline = System.nanoTime() + connectTimeoutMillis.coerceAtLeast(0L) * 1_000_000L
        var lastError: IOException? = null
        while (true) {
            try {
                return Files.newBufferedReader(
                    Paths.get(serverPath),
                    Charsets.UTF_8,
                )
            } catch (error: IOException) {
                lastError = error
                if (System.nanoTime() >= deadline) throw error
                Thread.sleep(retryDelayMillis.coerceAtLeast(1L))
            }
        }
        throw lastError ?: IOException("Unable to open mpv IPC server: $serverPath")
    }
}

enum class MpvSeekMode(val mpvValue: String) {
    RELATIVE("relative"),
    RELATIVE_EXACT("relative+exact"),
    ABSOLUTE("absolute"),
    ABSOLUTE_EXACT("absolute+exact"),
}
