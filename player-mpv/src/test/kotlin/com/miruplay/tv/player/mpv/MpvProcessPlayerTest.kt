package com.miruplay.tv.player.mpv

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.PlaybackSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class MpvProcessPlayerTest {
    @Test
    fun `isActive reflects launched process state`() = runBlocking {
        val mpv = Files.createTempFile("miruplay-mpv", ".exe")
        val fakeProcess = ControllableProcess(alive = true)
        val player = MpvProcessPlayer(
            config = MpvRuntimeConfig(
                mpvExecutable = mpv,
                rife = null,
            ),
            processLauncher = MpvProcessLauncher { fakeProcess },
        )

        assertFalse(player.isActive())

        val launch = player.play(
            PlaybackSource(
                uri = "D:/Anime/Test.mkv",
                mediaSourceId = "local",
            ),
        )
        assertTrue(launch is Result.Success)
        assertTrue(player.isActive())

        fakeProcess.alive = false

        assertFalse(player.isActive())
    }

    @Test
    fun `stop forcibly destroys process when graceful stop does not exit`() = runBlocking {
        val mpv = Files.createTempFile("miruplay-mpv", ".exe")
        val fakeProcess = StubbornProcess()
        val player = MpvProcessPlayer(
            config = MpvRuntimeConfig(
                mpvExecutable = mpv,
                rife = null,
            ),
            processLauncher = MpvProcessLauncher { fakeProcess },
        )

        val launch = player.play(
            PlaybackSource(
                uri = "D:/Anime/Test.mkv",
                mediaSourceId = "local",
            ),
        )
        assertTrue(launch is Result.Success)

        val stop = player.stop()

        assertTrue(stop is Result.Success)
        assertTrue(fakeProcess.destroyCalled)
        assertTrue(fakeProcess.destroyForciblyCalled)
    }

    @Test
    fun `setSpeed clamps to mpv supported playback speed range`() = runBlocking {
        val pipe = Files.createTempFile("miruplay-mpv-ipc", ".json")
        val mpv = Files.createTempFile("miruplay-mpv", ".exe")
        try {
            val player = MpvProcessPlayer(
                config = MpvRuntimeConfig(
                    mpvExecutable = mpv,
                    ipcServer = pipe.toString(),
                    rife = null,
                ),
            )

            val tooFast = player.setSpeed(4.0)

            assertTrue(tooFast is Result.Success)
            assertEquals(
                """{"command":["set_property","speed",3.0]}""",
                Files.readString(pipe).trim(),
            )
        } finally {
            Files.deleteIfExists(pipe)
            Files.deleteIfExists(mpv)
        }
    }

    @Test
    fun `queryPaused reads mpv pause property`() = runBlocking {
        val mpv = Files.createTempFile("miruplay-mpv", ".exe")
        try {
            val player = MpvProcessPlayer(
                config = MpvRuntimeConfig(mpvExecutable = mpv, rife = null),
                ipcClient = FakeMpvIpcController(paused = true),
            )

            assertEquals(Result.success(true), player.queryPaused())
        } finally {
            Files.deleteIfExists(mpv)
        }
    }

    private class ControllableProcess(
        var alive: Boolean,
    ) : Process() {
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int {
            alive = false
            return 0
        }

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            alive = false
            return true
        }

        override fun exitValue(): Int {
            if (alive) throw IllegalThreadStateException("still running")
            return 0
        }

        override fun destroy() {
            alive = false
        }

        override fun isAlive(): Boolean = alive

        override fun pid(): Long = 43L
    }

    private class StubbornProcess : Process() {
        var destroyCalled = false
        var destroyForciblyCalled = false
        private var alive = true

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int {
            alive = false
            return 0
        }

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean =
            !alive

        override fun exitValue(): Int {
            if (alive) throw IllegalThreadStateException("still running")
            return 0
        }

        override fun destroy() {
            destroyCalled = true
        }

        override fun destroyForcibly(): Process {
            destroyForciblyCalled = true
            alive = false
            return this
        }

        override fun isAlive(): Boolean = alive

        override fun pid(): Long = 42L
    }

    private class FakeMpvIpcController(
        private val paused: Boolean? = null,
    ) : MpvIpcController {
        override suspend fun cyclePause(): Result<Unit> =
            Result.success(Unit)

        override suspend fun setPaused(paused: Boolean): Result<Unit> =
            Result.success(Unit)

        override suspend fun setSpeed(speed: Double): Result<Unit> =
            Result.success(Unit)

        override suspend fun seekBy(seconds: Double, mode: MpvSeekMode): Result<Unit> =
            Result.success(Unit)

        override suspend fun quit(): Result<Unit> =
            Result.success(Unit)

        override suspend fun getTimePositionSeconds(): Result<Double?> =
            Result.success(null)

        override suspend fun getDurationSeconds(): Result<Double?> =
            Result.success(null)

        override suspend fun getPaused(): Result<Boolean?> =
            Result.success(paused)

        override suspend fun getEofReached(): Result<Boolean?> =
            Result.success(false)
    }
}
