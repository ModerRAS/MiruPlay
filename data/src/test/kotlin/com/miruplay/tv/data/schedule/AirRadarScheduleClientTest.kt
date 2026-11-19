package com.miruplay.tv.data.schedule

import com.miruplay.tv.model.ScheduleFetchError
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AirRadarScheduleClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: AirRadarScheduleClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = AirRadarScheduleClient(
            OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    @Test
    fun `success parses snapshot and requests latest endpoint`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(FIXTURE))

        val result = client.fetchLatest(baseUrl())

        assertTrue(result is ScheduleFetchResult.Success)
        val snapshot = (result as ScheduleFetchResult.Success).snapshot
        assertEquals("2026-fall", snapshot.season)
        assertEquals(2, snapshot.items.size)
        assertEquals("示例动画", snapshot.items[0].displayName)

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/schedule/latest", recorded.path)
        assertEquals("GET", recorded.method)
    }

    @Test
    fun `404 maps to no data`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"no_schedule_data"}"""))

        val result = client.fetchLatest(baseUrl())

        assertEquals(ScheduleFetchResult.NoData, result)
    }

    @Test
    fun `5xx maps to retryable http error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("busy"))

        val result = client.fetchLatest(baseUrl())

        assertEquals(ScheduleFetchResult.Failure(ScheduleFetchError.Http(503)), result)
    }

    @Test
    fun `malformed json maps to invalid data`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))

        val result = client.fetchLatest(baseUrl())

        assertEquals(ScheduleFetchResult.Failure(ScheduleFetchError.InvalidData), result)
    }

    @Test
    fun `unreachable host maps to network error`() = runBlocking {
        val unusedUrl = server.url("/").toString().trimEnd('/')
        server.shutdown()

        val result = client.fetchLatest(unusedUrl)

        assertTrue(result is ScheduleFetchResult.Failure)
    }

    @Test
    fun `invalid base url fails fast without network`() = runBlocking {
        val result = client.fetchLatest("ftp://example.com")
        assertEquals(ScheduleFetchResult.Failure(ScheduleFetchError.InvalidUrl), result)
    }

    companion object {
        val FIXTURE = """{
  "schemaVersion": 1,
  "generatedAt": "2026-10-07T03:04:05Z",
  "season": "2026-fall",
  "seasonStart": "2026-10-01",
  "seasonEnd": "2026-12-31",
  "timezone": "Asia/Tokyo",
  "itemCount": 2,
  "items": [
    {"subjectId": 393010, "name": "サンプル", "nameCn": "示例动画", "airDate": "2026-10-07", "image": "https://example.com/a.jpg", "weekday": 3, "time": "23:00", "timezone": "Asia/Tokyo", "confidence": "high", "sources": [{"url": "https://example.com/s", "domain": "example.com", "provider": "official_site"}]},
    {"subjectId": 393011, "name": "夜中アニメ", "airDate": "2026-10-09", "weekday": 5, "time": "01:28", "timezone": "Asia/Tokyo", "confidence": "medium", "sources": []}
  ]
}"""
    }
}
