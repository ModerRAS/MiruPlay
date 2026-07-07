package com.miruplay.tv.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LogUploadAutoSchedulerTest {
    @Test
    fun `auto scheduler starts loops and stops`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = FakeLogUploadRepository()
        try {
            val scheduler = LogUploadAutoScheduler(
                repository = repository,
                scope = scope,
                intervalMillis = 20L,
            )

            assertTrue(scheduler.start())
            withTimeout(500L) {
                while (repository.uploadCalls.get() < 2) {
                    delay(10L)
                }
            }
            assertTrue(scheduler.running)
            assertFalse(scheduler.start())

            scheduler.stop()
            assertFalse(scheduler.running)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `auto scheduler survives upload exceptions`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = FakeLogUploadRepository(throwFirstUpload = true)
        try {
            val scheduler = LogUploadAutoScheduler(
                repository = repository,
                scope = scope,
                intervalMillis = 20L,
            )

            assertTrue(scheduler.start())
            withTimeout(500L) {
                while (repository.uploadCalls.get() < 2) {
                    delay(10L)
                }
            }
            assertTrue(scheduler.running)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `auto scheduler syncWithConfig follows enabled flag`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = FakeLogUploadRepository()
        try {
            val scheduler = LogUploadAutoScheduler(
                repository = repository,
                scope = scope,
                intervalMillis = 20L,
            )
            assertFalse(scheduler.running)

            assertTrue(scheduler.syncWithConfig(OtlpLogUploadConfig(enabled = true)))
            withTimeout(500L) {
                while (repository.uploadCalls.get() < 1) {
                    delay(10L)
                }
            }
            assertTrue(scheduler.running)

            assertFalse(scheduler.syncWithConfig(OtlpLogUploadConfig(enabled = false)))
            assertFalse(scheduler.running)
        } finally {
            scope.cancel()
        }
    }

    private class FakeLogUploadRepository(
        private val throwFirstUpload: Boolean = false,
    ) : LogUploadRepository {
        private val statusState = MutableStateFlow(LogUploadStatus())
        private val configState = MutableStateFlow(OtlpLogUploadConfig())
        val uploadCalls = AtomicInteger(0)

        override val status: Flow<LogUploadStatus> = statusState.asStateFlow()

        override fun observeConfig(): Flow<OtlpLogUploadConfig> = configState.asStateFlow()

        override fun getConfig(): OtlpLogUploadConfig = configState.value

        override fun isTokenConfigured(): Boolean = true

        override suspend fun saveConfig(enabled: Boolean, endpoint: String, streamName: String) {
            configState.value = OtlpLogUploadConfig(enabled = enabled, endpoint = endpoint, streamName = streamName)
        }

        override suspend fun saveToken(token: String) = Unit

        override suspend fun clearToken() = Unit

        override suspend fun uploadPendingLogs(): LogUploadStatus {
            if (throwFirstUpload && uploadCalls.get() == 0) {
                uploadCalls.incrementAndGet()
                error("boom")
            }
            uploadCalls.incrementAndGet()
            val next = statusState.value.copy(
                lastUploadStatus = "uploaded",
                tokenConfigured = true,
            )
            statusState.value = next
            return next
        }
    }
}
