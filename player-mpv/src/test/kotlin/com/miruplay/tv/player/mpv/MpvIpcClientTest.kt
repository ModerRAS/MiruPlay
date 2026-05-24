package com.miruplay.tv.player.mpv

import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class MpvIpcClientTest {
    @Test
    fun `setPaused writes mpv json ipc command`() = runBlocking {
        val pipe = Files.createTempFile("miruplay-mpv-ipc", ".json")
        try {
            val result = MpvIpcClient(pipe.toString(), connectTimeoutMillis = 0L).setPaused(true)

            assertTrue(result is Result.Success)
            assertEquals(
                """{"command":["set_property","pause",true]}""",
                Files.readString(pipe).trim(),
            )
        } finally {
            Files.deleteIfExists(pipe)
        }
    }

    @Test
    fun `setSpeed writes mpv speed property command`() = runBlocking {
        val pipe = Files.createTempFile("miruplay-mpv-ipc", ".json")
        try {
            val result = MpvIpcClient(pipe.toString(), connectTimeoutMillis = 0L).setSpeed(1.25)

            assertTrue(result is Result.Success)
            assertEquals(
                """{"command":["set_property","speed",1.25]}""",
                Files.readString(pipe).trim(),
            )
        } finally {
            Files.deleteIfExists(pipe)
        }
    }

    @Test
    fun `seekBy writes relative exact seek command`() = runBlocking {
        val pipe = Files.createTempFile("miruplay-mpv-ipc", ".json")
        try {
            val result = MpvIpcClient(pipe.toString(), connectTimeoutMillis = 0L).seekBy(30.5)

            assertTrue(result is Result.Success)
            assertEquals(
                """{"command":["seek",30.5,"relative+exact"]}""",
                Files.readString(pipe).trim(),
            )
        } finally {
            Files.deleteIfExists(pipe)
        }
    }

    @Test
    fun `getTimePositionSeconds requests time-pos and parses response`() = runBlocking {
        val transport = RecordingTransport("""{"data":123.456,"error":"success"}""")

        val result = MpvIpcClient("pipe", transport = transport).getTimePositionSeconds()

        assertTrue(result is Result.Success)
        assertEquals(123.456, (result as Result.Success).data ?: 0.0, 0.0001)
        assertEquals("""{"command":["get_property","time-pos"]}""", transport.requestPayload)
    }

    @Test
    fun `getDurationSeconds requests duration and parses response`() = runBlocking {
        val transport = RecordingTransport("""{"data":1500.25,"error":"success"}""")

        val result = MpvIpcClient("pipe", transport = transport).getDurationSeconds()

        assertTrue(result is Result.Success)
        assertEquals(1500.25, (result as Result.Success).data ?: 0.0, 0.0001)
        assertEquals("""{"command":["get_property","duration"]}""", transport.requestPayload)
    }

    @Test
    fun `getEofReached requests eof reached and parses response`() = runBlocking {
        val transport = RecordingTransport("""{"data":true,"error":"success"}""")

        val result = MpvIpcClient("pipe", transport = transport).getEofReached()

        assertTrue(result is Result.Success)
        assertEquals(true, (result as Result.Success).data)
        assertEquals("""{"command":["get_property","eof-reached"]}""", transport.requestPayload)
    }

    @Test
    fun `missing ipc server returns playback error`() = runBlocking {
        val directory = Files.createTempDirectory("miruplay-mpv-ipc")
        try {
            val missingPipe = directory.resolve("missing-pipe")

            val result = MpvIpcClient(missingPipe.toString(), connectTimeoutMillis = 0L).cyclePause()

            assertTrue(result is Result.Error)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private class RecordingTransport(
        private val response: String,
    ) : MpvIpcTransport {
        var requestPayload: String = ""

        override suspend fun send(payload: String): Result<Unit> {
            requestPayload = payload
            return Result.success(Unit)
        }

        override suspend fun request(payload: String): Result<String> {
            requestPayload = payload
            return Result.success(response)
        }
    }
}
