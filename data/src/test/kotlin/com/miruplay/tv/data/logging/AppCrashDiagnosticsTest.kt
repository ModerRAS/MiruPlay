package com.miruplay.tv.data.logging

import androidx.test.core.app.ApplicationProvider
import com.miruplay.tv.core.common.logging.MiruLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppCrashDiagnosticsTest {
    private lateinit var localLogStore: LocalLogStore
    private lateinit var diagnostics: AppCrashDiagnostics
    private lateinit var startupDiagnosticsWriter: CapturingStartupDiagnosticsWriter
    private var previousHandlerCalled = false
    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setup() {
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir, "logs").deleteRecursively()
        context.getSharedPreferences("miruplay_crash_diagnostics", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        localLogStore = LocalLogStore(context)
        startupDiagnosticsWriter = CapturingStartupDiagnosticsWriter()
        diagnostics = AppCrashDiagnostics(
            context,
            localLogStore,
            startupDiagnosticsWriter,
        )
        MiruLog.setSink(null)
    }

    @After
    fun teardown() {
        MiruLog.setSink(null)
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    @Test
    fun `uncaught exception is written before previous handler runs`() {
        diagnostics.install { _, _ -> previousHandlerCalled = true }

        val throwable = IllegalStateException("boom")
        Thread.getDefaultUncaughtExceptionHandler()
            ?.uncaughtException(Thread.currentThread(), throwable)

        val records = localLogStore.readBatch(20)
        assertTrue(previousHandlerCalled)
        assertTrue(records.any { record ->
            record.level.severityText == "ERROR" &&
                record.message == "Unhandled exception crashed the app" &&
                record.throwableClass == IllegalStateException::class.java.name &&
                record.stackTrace.orEmpty().contains("boom")
        })
        assertTrue(startupDiagnosticsWriter.content().contains("\"event\":\"fatal\""))
        assertTrue(startupDiagnosticsWriter.content().contains("uncaught_exception"))
        assertTrue(startupDiagnosticsWriter.content().contains(IllegalStateException::class.java.name))
    }

    @Test
    fun `new session reports previous unclean shutdown`() {
        diagnostics.install { _, _ -> previousHandlerCalled = true }
        diagnostics.markStartupCheckpoint("test_checkpoint")

        val secondDiagnostics = AppCrashDiagnostics(
            ApplicationProvider.getApplicationContext(),
            localLogStore,
            startupDiagnosticsWriter,
        )
        secondDiagnostics.install { _, _ -> }

        val warning = localLogStore.readBatch(20)
            .firstOrNull { it.message == "Previous app session ended without a clean shutdown callback" }
        assertEquals("test_checkpoint", warning?.attributes?.get("previous_last_state"))
    }

    @Test
    fun `startup checkpoints are mirrored to public startup diagnostics`() {
        diagnostics.install { _, _ -> previousHandlerCalled = true }

        diagnostics.markStartupCheckpoint(
            checkpoint = "activity_created",
            attributes = mapOf("activity" to "MainActivity"),
        )

        val content = startupDiagnosticsWriter.content()
        assertTrue(content.contains("\"event\":\"checkpoint\""))
        assertTrue(content.contains("activity_created"))
        assertTrue(content.contains("MainActivity"))
    }

    private class CapturingStartupDiagnosticsWriter : StartupDiagnosticsWriter {
        private val lines = mutableListOf<String>()

        override fun append(line: String) {
            lines += line
        }

        fun content(): String = lines.joinToString(separator = "")
    }
}
