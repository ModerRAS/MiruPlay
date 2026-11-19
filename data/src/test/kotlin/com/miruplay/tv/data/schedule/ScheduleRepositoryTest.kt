package com.miruplay.tv.data.schedule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleRepositoryTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var preferences: ScheduleDataSourcePreferences
    private lateinit var repository: ScheduleRepository

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(ScheduleDataSourcePreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        File(context.cacheDir, "schedule").deleteRecursively()
        server = MockWebServer()
        server.start()
        preferences = ScheduleDataSourcePreferences(context)
        repository = newRepository()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun newRepository(): ScheduleRepository = ScheduleRepository(
        context = context,
        client = AirRadarScheduleClient(
            OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build(),
        ),
        preferences = preferences,
    )

    private fun configureUrl(url: String = baseUrl()): Boolean = repository.saveDataSourceUrl(url)

    @Test
    fun `not configured returns NotConfigured without network`() = runBlocking {
        val result = repository.load(nowMillis = 1_000L)

        assertEquals(ScheduleLoadResult.NotConfigured, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `invalid configured url is treated as not configured`() = runBlocking {
        preferences.rawDataSourceUrl = "ftp://example.com"

        assertEquals(ScheduleLoadResult.NotConfigured, repository.load(nowMillis = 1_000L))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `fresh load goes to network and caches in memory and on disk`() = runBlocking {
        assertTrue(configureUrl())
        server.enqueue(MockResponse().setResponseCode(200).setBody(FIXTURE))

        val result = repository.load(nowMillis = 1_000L) as ScheduleLoadResult.Success

        assertFalse(result.fromCache)
        assertFalse(result.stale)
        assertEquals("2026-fall", result.snapshot.season)
        assertEquals(1, server.requestCount)
        assertTrue(repository.cacheFileForTest().exists())

        val cached = repository.load(nowMillis = 2_000L) as ScheduleLoadResult.Success
        assertTrue(cached.fromCache)
        assertFalse(cached.stale)
        assertEquals(1, server.requestCount) // 未过期，不再发请求
    }

    @Test
    fun `cache expires after one hour and refetches`() = runBlocking {
        assertTrue(configureUrl())
        server.enqueue(MockResponse().setResponseCode(200).setBody(FIXTURE))
        repository.load(nowMillis = 1_000L)

        server.enqueue(MockResponse().setResponseCode(200).setBody(FIXTURE))
        val expired = repository.load(
            nowMillis = 1_000L + ScheduleRepository.CACHE_TTL_MILLIS,
        ) as ScheduleLoadResult.Success

        assertFalse(expired.fromCache)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `disk cache survives repository recreation`() = runBlocking {
        assertTrue(configureUrl())
        server.enqueue(MockResponse().setResponseCode(200).setBody(FIXTURE))
        repository.load(nowMillis = 1_000L)

        val recreated = newRepository()
        val result = recreated.load(nowMillis = 2_000L) as ScheduleLoadResult.Success

        assertTrue(result.fromCache)
        assertFalse(result.stale)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `network failure falls back to stale cache`() = runBlocking {
        assertTrue(configureUrl())
        server.enqueue(MockResponse().setResponseCode(200).setBody(FIXTURE))
        repository.load(nowMillis = 1_000L)
        server.shutdown()

        val recreated = newRepository()
        val result = recreated.load(
            forceRefresh = true,
            nowMillis = 1_000L + ScheduleRepository.CACHE_TTL_MILLIS * 2,
        ) as ScheduleLoadResult.Success

        assertTrue(result.fromCache)
        assertTrue(result.stale)
        assertNotNull(result.error)
    }

    @Test
    fun `network failure without cache returns retryable failure`() = runBlocking {
        assertTrue(configureUrl())
        server.shutdown()

        val result = repository.load(nowMillis = 1_000L)

        assertTrue(result is ScheduleLoadResult.Failure)
        assertFalse(repository.hasCachedSnapshot())
    }

    @Test
    fun `404 means empty and clears old cache`() = runBlocking {
        assertTrue(configureUrl())
        server.enqueue(MockResponse().setResponseCode(200).setBody(FIXTURE))
        repository.load(nowMillis = 1_000L)
        assertTrue(repository.hasCachedSnapshot())

        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"no_schedule_data"}"""))
        val result = repository.load(forceRefresh = true, nowMillis = 2_000L)

        assertEquals(ScheduleLoadResult.Empty, result)
        assertFalse(repository.hasCachedSnapshot())
    }

    @Test
    fun `force refresh bypasses fresh cache`() = runBlocking {
        assertTrue(configureUrl())
        server.enqueue(MockResponse().setResponseCode(200).setBody(FIXTURE))
        repository.load(nowMillis = 1_000L)

        server.enqueue(MockResponse().setResponseCode(200).setBody(FIXTURE))
        val refreshed = repository.load(forceRefresh = true, nowMillis = 2_000L) as ScheduleLoadResult.Success

        assertFalse(refreshed.fromCache)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `saving url validates and clears cache`() = runBlocking {
        assertTrue(configureUrl())
        assertEquals(baseUrl(), preferences.configuredUrlOrNull())

        assertFalse(repository.saveDataSourceUrl("ftp://example.com"))
        assertEquals(baseUrl(), preferences.configuredUrlOrNull())

        repository.clearDataSourceUrl()
        assertNull(preferences.configuredUrlOrNull())
    }

    companion object {
        val FIXTURE = """{
  "schemaVersion": 1,
  "generatedAt": "2026-10-07T03:04:05Z",
  "season": "2026-fall",
  "seasonStart": "2026-10-01",
  "seasonEnd": "2026-12-31",
  "timezone": "Asia/Tokyo",
  "itemCount": 1,
  "items": [
    {"subjectId": 393010, "name": "サンプル", "nameCn": "示例动画", "airDate": "2026-10-07", "weekday": 3, "time": "23:00", "timezone": "Asia/Tokyo", "confidence": "high", "sources": []}
  ]
}"""
    }
}
