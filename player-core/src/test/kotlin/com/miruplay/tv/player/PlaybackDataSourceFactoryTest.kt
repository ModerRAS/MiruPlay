package com.miruplay.tv.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.miruplay.tv.mediasource.WebDavHttpStatusException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackDataSourceFactoryTest {
    @Test
    fun `canonicalPlaybackUri encodes CloudDrive unicode and brackets`() {
        val uri = canonicalPlaybackUri(
            "http://127.0.0.1:19798/dav/115open/影音/动漫/從 0 位居民開始的邊境領主大人/Season 1/[ANi] 從 0 位居民開始的邊境領主大人 - 03 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4",
        )

        assertTrue(uri.contains("/Season%201/%5BANi%5D%20"))
        assertTrue(uri.endsWith("%5BCHT%5D.mp4"))
        assertFalse(uri.contains("從"))
        assertFalse(uri.contains(' '))
    }

    @Test
    fun `headersFor applies auth when uri stays on same WebDAV origin`() {
        val config = PlaybackHttpRequestConfig(
            baseUrl = "http://10.137.32.158:19798/dav/115open/影音/电视剧",
            headers = mapOf("Authorization" to "Basic YW5vbnltb3VzOg=="),
        )

        val headers = config.headersFor(
            "http://10.137.32.158:19798/dav/115open/%E5%BD%B1%E9%9F%B3/%E7%94%B5%E8%A7%86%E5%89%A7/%E5%8C%BB%E9%A6%86%E7%AC%91%E4%BC%A0/%E5%8C%BB%E9%A6%86%E7%AC%91%E4%BC%A0.S01/%E5%8C%BB%E9%A6%86%E7%AC%91%E4%BC%A0.S01E01.mp4",
        )

        assertEquals("Basic YW5vbnltb3VzOg==", headers["Authorization"])
    }

    @Test
    fun `headersFor leaves unrelated origins unauthenticated`() {
        val config = PlaybackHttpRequestConfig(
            baseUrl = "http://10.137.32.158:19798/dav/115open/影音/电视剧",
            headers = mapOf("Authorization" to "Basic YW5vbnltb3VzOg=="),
        )

        val headers = config.headersFor("https://cdn.example.test/video.mp4")

        assertTrue(headers.isEmpty())
    }

    @Test
    fun `libVlcUriFor embeds empty password user info for anonymous webdav`() {
        val config = PlaybackHttpRequestConfig(
            baseUrl = "http://10.137.32.158:19798/dav/115open/影音/电视剧",
            headers = mapOf("Authorization" to "Basic YW5vbnltb3VzOg=="),
        )

        val uri = config.libVlcUriFor(
            "http://10.137.32.158:19798/dav/115open/%E5%BD%B1%E9%9F%B3/%E7%94%B5%E8%A7%86%E5%89%A7/%E8%89%AF%E9%99%88%E7%BE%8E%E9%94%A6/1.mp4",
        )

        assertEquals(
            "http://anonymous:@10.137.32.158:19798/dav/115open/%E5%BD%B1%E9%9F%B3/%E7%94%B5%E8%A7%86%E5%89%A7/%E8%89%AF%E9%99%88%E7%BE%8E%E9%94%A6/1.mp4",
            uri,
        )
    }

    @Test
    fun `libVlcUriFor canonicalizes raw CloudDrive path before embedding credentials`() {
        val config = PlaybackHttpRequestConfig(
            baseUrl = "http://127.0.0.1:19798/dav",
            headers = mapOf("Authorization" to "Basic YW5vbnltb3VzOg=="),
        )

        val uri = config.libVlcUriFor(
            "http://127.0.0.1:19798/dav/Show Name/[ANi] 03.mp4",
        )

        assertEquals(
            "http://anonymous:@127.0.0.1:19798/dav/Show%20Name/%5BANi%5D%2003.mp4",
            uri,
        )
    }

    @Test
    fun `webdav playback rejects redirects before the redirected authority is requested`() {
        ServerSocket(0).use { redirectServer ->
            ServerSocket(0).use { redirectedServer ->
                redirectedServer.soTimeout = 300
                val redirectTask = thread(start = true) {
                    redirectServer.accept().use { socket ->
                        socket.getInputStream().bufferedReader().apply {
                            readLine()
                            while (readLine()?.isNotEmpty() == true) Unit
                        }
                        socket.getOutputStream().bufferedWriter().use { output ->
                            output.write(
                                "HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${redirectedServer.localPort}/media.mkv\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                            )
                            output.flush()
                        }
                    }
                }
                val config = PlaybackHttpRequestConfig(
                    baseUrl = "http://127.0.0.1:${redirectServer.localPort}/dav",
                    headers = emptyMap(),
                )
                val dataSource = GatedPlaybackDataSource(UnusedDataSource()) { config }

                assertThrows(WebDavHttpStatusException::class.java) {
                    dataSource.open(DataSpec(Uri.parse("http://127.0.0.1:${redirectServer.localPort}/dav/media.mkv")))
                }
                redirectTask.join()
                assertThrows(SocketTimeoutException::class.java) { redirectedServer.accept() }
            }
        }
    }

    @Test
    fun `libVlcUriFor normalizes absolute local path into file uri`() {
        val config = PlaybackHttpRequestConfig.Empty

        val uri = config.libVlcUriFor("/sdcard/Movies/MiruPlayHdrTest/probe_30s_1080p_hdr_h264_high10.mp4")

        assertEquals(
            "file:///sdcard/Movies/MiruPlayHdrTest/probe_30s_1080p_hdr_h264_high10.mp4",
            uri,
        )
    }

    private class UnusedDataSource : DataSource {
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long = error("WebDAV playback must not use Media3 HTTP transport")
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = error("unreachable")
        override fun getUri(): Uri? = null
        override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()
        override fun close() = Unit
    }
}
