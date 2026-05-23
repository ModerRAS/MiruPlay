package com.miruplay.tv.data.logging

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.core.common.logging.MiruLogLevel
import com.miruplay.tv.core.common.logging.MiruLogRecord
import com.miruplay.tv.repository.AppCredentialStore
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogUploadRepositoryImplTest {
    private lateinit var context: Context
    private lateinit var localLogStore: LocalLogStore
    private lateinit var preferences: LogUploadPreferencesManager
    private lateinit var credentials: FakeCredentialStore
    private lateinit var server: MockWebServer
    private lateinit var repository: LogUploadRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "logs").deleteRecursively()
        context.getSharedPreferences("miruplay_log_upload_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        MiruLog.setSink(null)

        localLogStore = LocalLogStore(context)
        preferences = LogUploadPreferencesManager(context)
        credentials = FakeCredentialStore()
        server = MockWebServer()
        server.start()
        repository = LogUploadRepositoryImpl(
            preferences = preferences,
            credentials = credentials,
            localLogStore = localLogStore,
            uploader = OtlpLogUploader(OkHttpClient())
        )
    }

    @After
    fun teardown() {
        MiruLog.setSink(null)
        server.shutdown()
    }

    @Test
    fun `upload pending logs drains every local batch in one run`() = runBlocking {
        repeat(450) { index -> localLogStore.log(logRecord(index)) }
        repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) }
        repository.saveConfig(enabled = true, endpoint = server.url("/api/acme").toString(), streamName = "default")
        repository.saveToken("user:password")

        val status = repository.uploadPendingLogs()

        assertEquals(0, status.pendingCount)
        assertEquals("已上报 450 条日志", status.lastUploadStatus)
        assertEquals(0, localLogStore.pendingCount())
        assertRequestPayloadSizes(200, 200, 50)
    }

    @Test
    fun `upload pending logs keeps failed batch and remaining records queued`() = runBlocking {
        repeat(450) { index -> localLogStore.log(logRecord(index)) }
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        repository.saveConfig(enabled = true, endpoint = server.url("/api/acme").toString(), streamName = "default")
        repository.saveToken("user:password")

        val status = repository.uploadPendingLogs()

        assertEquals(251, status.pendingCount)
        assertTrue(status.lastUploadStatus.orEmpty().contains("已上报 200 条日志，后续上报失败"))
        assertEquals(251, localLogStore.pendingCount())
        assertRequestPayloadSizes(200, 200)
    }

    private fun assertRequestPayloadSizes(vararg sizes: Int) {
        sizes.forEach { expectedSize ->
            val request = server.takeRequest(1, TimeUnit.SECONDS)
            requireNotNull(request) { "Expected OpenObserve upload request" }
            assertEquals("/api/acme/default/_json", request.path)
            val payload = Json.parseToJsonElement(request.body.readUtf8()).jsonArray
            assertEquals(expectedSize, payload.size)
        }
        assertEquals(sizes.size, server.requestCount)
    }

    private fun logRecord(index: Int): MiruLogRecord =
        MiruLogRecord(
            id = "record-$index",
            timestampMs = 1_700_000_000_000L + index,
            level = MiruLogLevel.INFO,
            tag = "Test",
            message = "message $index"
        )

    private class FakeCredentialStore : AppCredentialStore {
        override var bangumiAccessToken: String? = null
        override var otlpAccessToken: String? = null
        override var cloudDriveToken: String? = null
        override var cloudDrivePassword: String? = null

        override fun clearBangumiToken() {
            bangumiAccessToken = null
        }

        override fun clearOtlpAccessToken() {
            otlpAccessToken = null
        }

        override fun clearCloudDriveCredentials() {
            cloudDriveToken = null
            cloudDrivePassword = null
        }
    }
}
