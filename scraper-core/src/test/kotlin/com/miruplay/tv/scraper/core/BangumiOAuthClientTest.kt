package com.miruplay.tv.scraper.core

import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiOAuthClientTest {
    @Test
    fun `authorizeUrl contains client state and loopback redirect`() {
        val url = BangumiOAuthConstants.authorizeUrl(
            clientId = "test-id",
            state = "state-123",
        )

        assertTrue(url.startsWith("https://bgm.tv/oauth/authorize?"))
        assertTrue(url.contains("client_id=test-id"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("state=state-123"))
        assertTrue(url.contains("redirect_uri=" + "http%3A%2F%2F127.0.0.1%3A13712%2Fcallback"))
    }

    @Test
    fun `exchangeCode posts form and parses tokens`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"access_token":"access-1","expires_in":604800,"token_type":"Bearer","refresh_token":"refresh-1","user_id":42}"""
                )
            )
            val client = BangumiOAuthClient(
                clientId = "test-id",
                clientSecret = "test-secret",
                clockMillis = { 1_000L },
                tokenUrl = server.url("/oauth/access_token").toString(),
            )

            val result = client.exchangeCode("auth-code")

            val tokens = (result as Result.Success).data
            assertEquals("access-1", tokens.accessToken)
            assertEquals("refresh-1", tokens.refreshToken)
            assertEquals(1_000L + 604800L * 1000L, tokens.expiresAtMillis)
            assertEquals(42L, tokens.userId)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            val body = recorded.body.readUtf8()
            assertTrue(body.contains("grant_type=authorization_code"))
            assertTrue(body.contains("client_id=test-id"))
            assertTrue(body.contains("client_secret=test-secret"))
            assertTrue(body.contains("code=auth-code"))
            assertTrue(body.contains("redirect_uri="))
        }
    }

    @Test
    fun `exchangeCode maps http error to HttpError`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400).setBody("bad code"))
            val client = BangumiOAuthClient(
                clientId = "test-id",
                clientSecret = "test-secret",
                tokenUrl = server.url("/oauth/access_token").toString(),
            )

            val result = client.exchangeCode("bad-code")

            assertTrue(result is Result.Error)
        }
    }

    @Test
    fun `refresh posts refresh grant and rotates tokens`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"access_token":"access-2","expires_in":604800,"token_type":"Bearer","refresh_token":"refresh-2","user_id":42}"""
                )
            )
            val client = BangumiOAuthClient(
                clientId = "test-id",
                clientSecret = "test-secret",
                clockMillis = { 0L },
                tokenUrl = server.url("/oauth/access_token").toString(),
            )

            val result = client.refresh("refresh-1")

            val tokens = (result as Result.Success).data
            assertEquals("access-2", tokens.accessToken)
            assertEquals("refresh-2", tokens.refreshToken)
            val recorded = server.takeRequest()
            val refreshBody = recorded.body.readUtf8()
            assertTrue(refreshBody.contains("grant_type=refresh_token"))
            assertTrue(refreshBody.contains("refresh_token=refresh-1"))
        }
    }

    @Test
    fun `unconfigured client fails fast without network`() = runBlocking {
        val client = BangumiOAuthClient(clientId = "", clientSecret = "")

        assertTrue(client.exchangeCode("whatever") is Result.Error)
        assertTrue(client.refresh("whatever") is Result.Error)
    }
}
