package com.miruplay.tv.translation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.SubtitleFormat
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.player.PlaybackDataSourceFactory
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.scraper.core.BangumiProxyAwareOkHttpClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class TranslationProviderTransportTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `google sends encoded request and parses segmented response`() = runBlocking {
        server.enqueue(MockResponse().setBody("[[[\"你好\",\"hello\"],[\"世界\",\"world\"]],null,\"en\"]"))

        val translated = GoogleTranslator(clientRedirectedTo(server)).translate("hello world", "zh-CN")

        assertEquals("你好世界", translated)
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("client=gtx"))
        assertTrue(request.path!!.contains("tl=zh-CN"))
        assertTrue(request.path!!.contains("q=hello+world"))
    }

    @Test
    fun `google exposes HTTP failures`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))

        val error = runCatching {
            GoogleTranslator(clientRedirectedTo(server)).translate("hello", "zh-CN")
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(error!!.message!!.contains("HTTP 429"))
    }

    @Test
    fun `bing fetches credentials sends form and parses response`() = runBlocking {
        server.enqueue(MockResponse().setBody("<script>var IG=\"AB12CD\";</script>"))
        server.enqueue(
            MockResponse().setBody(
                "<script>var params_AbusePreventionHelper = [12345, \"token-value\", 0];</script>",
            ),
        )
        server.enqueue(MockResponse().setBody("[{\"translations\":[{\"text\":\"你好\"}]}]"))

        val translated = BingTranslator(clientRedirectedTo(server)).translate("hello world", "zh-Hans")

        assertEquals("你好", translated)
        server.takeRequest()
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("IG=AB12CD"))
        assertEquals("https://www.bing.com", request.getHeader("Origin"))
        val form = request.body.readUtf8()
        assertTrue(form.contains("fromLang=auto-detect"))
        assertTrue(form.contains("to=zh-Hans"))
        assertTrue(form.contains("text=hello+world"))
        assertTrue(form.contains("token=token-value"))
        assertTrue(form.contains("key=12345"))
    }

    @Test
    fun `bing rejects provider error status`() = runBlocking {
        server.enqueue(MockResponse().setBody("<script>var IG=\"AB12CD\";</script>"))
        server.enqueue(
            MockResponse().setBody(
                "<script>var params_AbusePreventionHelper = [12345, \"token-value\", 0];</script>",
            ),
        )
        server.enqueue(MockResponse().setBody("[{\"statusCode\":400,\"translations\":[]}]"))

        val error = runCatching {
            BingTranslator(clientRedirectedTo(server)).translate("hello", "zh-Hans")
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(error!!.message!!.contains("statusCode=400"))
    }

    @Test
    fun `deepseek sends bearer payload and parses response`() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"你好\"}}]}"))

        val translated = DeepSeekTranslator(clientRedirectedTo(server)).translate(
            text = "hello",
            apiKey = "secret-key",
            targetInstruction = "简体中文",
        )

        assertEquals("你好", translated)
        val request = server.takeRequest()
        assertEquals("Bearer secret-key", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("Translate to 简体中文."))
    }

    @Test
    fun `deepseek rejects a missing API key before transport`() = runBlocking {
        val error = runCatching {
            DeepSeekTranslator(clientRedirectedTo(server)).translate("hello", "", "简体中文")
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(error!!.message!!.contains("API Key"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `service routes configured proxy through okhttp and writes translated SRT`() = runBlocking {
        server.enqueue(MockResponse().setBody("[[[\"你好\",\"hello\"]],null,\"en\"]"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceFile = File.createTempFile("translation", ".srt", context.cacheDir).apply {
            writeText("1\n00:00:01,000 --> 00:00:02,000\nhello\n")
        }
        val repository = mockk<CloudDriveAutomationRepository>()
        coEvery { repository.getConfig() } returns Result.success(
            CloudDriveAutomationConfig(
                rssProxyEnabled = true,
                rssProxyHost = server.hostName,
                rssProxyPort = server.port,
            ),
        )
        val service = SubtitleTranslationService(
            context = context,
            preferences = TestTranslationPreferences(),
            cloudDriveRepository = repository,
            dataSourceFactory = mockk<PlaybackDataSourceFactory>(relaxed = true),
            proxyClient = clientRedirectedToCleartextTarget(),
        )

        val result = service.translateTrack(
            track = SubtitleTrack(
                language = "en",
                title = "source.srt",
                isExternal = true,
                path = sourceFile.absolutePath,
                format = SubtitleFormat.SRT,
            ),
            targetLanguageCode = "zh-Hans",
            provider = TranslationProvider.GOOGLE,
        )

        assertTrue(result is Result.Success)
        val translated = (result as Result.Success).data
        assertTrue(translated.isExternal)
        assertEquals(SubtitleFormat.SRT, translated.format)
        assertTrue(File(translated.path).readText().contains("你好"))
        assertFalse(File(translated.path).readText().contains("hello"))
        val proxiedRequest = server.takeRequest()
        assertTrue(proxiedRequest.requestLine.startsWith("GET http://translation.test/translate_a/single?"))
    }

    private fun clientRedirectedTo(server: MockWebServer): BangumiProxyAwareOkHttpClient =
        BangumiProxyAwareOkHttpClient(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val redirected = server.url(original.url.encodedPath)
                        .newBuilder()
                        .encodedQuery(original.url.encodedQuery)
                        .build()
                    chain.proceed(original.newBuilder().url(redirected).build())
                }
                .build(),
        )

    private fun clientRedirectedToCleartextTarget(): BangumiProxyAwareOkHttpClient =
        BangumiProxyAwareOkHttpClient(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val redirected = original.url.newBuilder()
                        .scheme("http")
                        .host("translation.test")
                        .port(80)
                        .build()
                    chain.proceed(original.newBuilder().url(redirected).build())
                }
                .build(),
        )

    private class TestTranslationPreferences : TranslationPreferencesRepository {
        override var deepSeekApiKey: String = ""
        override var defaultTargetLanguage: String = "zh-Hans"
    }
}
