package com.miruplay.tv.scraper.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiPasswordLoginClientTest {
    @Test
    fun `parsers read the live login form, authorize form, and error notice`() {
        val login = """
            <form id="loginForm" method="post" action="/FollowTheRabbit">
            <input type="hidden" name="formhash" value="4a09722d">
            <input id="email" name="email" type="text" />
            <input id="password" name="password" type="password">
            <input id="captcha" name="captcha_challenge_field" type="text" />
            <input type="submit" name="loginsubmit" value="登录">
            </form>
        """.trimIndent()
        assertEquals("/FollowTheRabbit" to "4a09722d", parseBangumiLoginForm(login))

        // 线上授权页的表单没有 action 属性（浏览器会提交回当前 URL，查询串里带
        // client_id/response_type/redirect_uri/state），fixture 必须保持同样形态。
        val authorize = """
            <form id="authForm" method="post" name="auth">
            <input type="hidden" name="formhash" value="auth-hash">
            <input type="hidden" name="redirect_uri" value="http://127.0.0.1:13712/callback">
            <input type="hidden" name="client_id" value="test-id">
            <input type="submit" name="submit" value="授权">
            </form>
        """.trimIndent()
        val form = parseAuthorizeForm(authorize)!!
        assertEquals("", form.action)
        assertEquals("auth-hash", form.fields.first { it.first == "formhash" }.second)
        assertEquals("授权", form.fields.first { it.first == "submit" }.second)

        val error = """
            <div id="colunmNotice"><div class="message"><h2>呜咕，出错了</h2>
            <p class="text">验证码错误，请返回重试。</p></div></div>
        """.trimIndent()
        assertEquals("验证码错误，请返回重试。", parseBangumiNotice(error))
        assertTrue(isBangumiOAuthCallback("http://127.0.0.1:13712/callback?code=abc&state=s", BangumiOAuthConstants.CALLBACK_URL))
        assertEquals(
            "http://127.0.0.1:13712/callback?code=abc&state=kept",
            withOAuthState("http://127.0.0.1:13712/callback?code=abc&state=kept", "other"),
        )
        assertTrue(withOAuthState("http://127.0.0.1:13712/callback?code=abc", "s 1").contains("state=s+1") ||
            withOAuthState("http://127.0.0.1:13712/callback?code=abc", "s 1").contains("state=s%201"))
        assertEquals("image/png", imageContentType("image/png; charset=binary"))
        assertEquals("image/gif", imageContentType("text/html"))
    }

    @Test
    fun `wrong captcha retries and a later correct captcha returns the callback without storing the password`() = runBlocking {
        val posts = mutableListOf<String>()
        val paths = mutableListOf<String>()
        MockWebServer().use { server ->
            server.dispatcher = scriptedDispatcher(posts, paths, authorizeRedirect = false)
            val client = BangumiPasswordLoginClient(
                baseUrl = server.url("/").toString().trimEnd('/'),
                captchaSuffix = { 1 },
                clockMillis = { 10L },
            )
            val challenge = client.startChallenge()
            assertTrue(challenge is BangumiPasswordLoginStep.Challenge)
            assertEquals("image/gif", (challenge as BangumiPasswordLoginStep.Challenge).contentType)
            assertTrue(challenge.image.contentEquals(TINY_GIF))

            val retry = client.loginAndAuthorize(
                email = "user@example.com",
                password = SECRET,
                captcha = "bad",
                clientId = "test-id",
                redirectUri = BangumiOAuthConstants.CALLBACK_URL,
                state = "state-1",
            )
            assertTrue(retry is BangumiPasswordLoginStep.Retry)
            val retryStep = retry as BangumiPasswordLoginStep.Retry
            assertEquals("验证码错误，请返回重试。", retryStep.message)
            assertFalse(retryStep.message.contains(SECRET))
            assertTrue(retryStep.image.contentEquals(TINY_GIF))

            val authorized = client.loginAndAuthorize(
                email = "user@example.com",
                password = SECRET,
                captcha = "ok",
                clientId = "test-id",
                redirectUri = BangumiOAuthConstants.CALLBACK_URL,
                state = "state-1",
            )
            assertTrue(authorized is BangumiPasswordLoginStep.Authorized)
            assertEquals(
                "http://127.0.0.1:13712/callback?code=auth-code&state=state-1",
                (authorized as BangumiPasswordLoginStep.Authorized).callbackUrl,
            )
            assertTrue(posts.any { it.contains("password=$SECRET") && it.contains("captcha_challenge_field=bad") })
            assertTrue(posts.any { it.contains("password=$SECRET") && it.contains("captcha_challenge_field=ok") })
            assertTrue(posts.any { it.contains("formhash=auth-hash") && it.contains("submit=") })
            // 授权表单没有 action：POST 必须回到带查询串的授权页 URL；裸 /oauth/authorize
            // 会丢 client_id/response_type/state，Bangumi 回 app_nonexistence（回归 #99 的线上故障）。
            val authorizePost = paths.firstOrNull { it.startsWith("/oauth/authorize?") }
            assertNotNull("authorize POST must target the authorize URL with its query string", authorizePost)
            assertTrue(authorizePost!!.contains("client_id=test-id"))
            assertTrue(authorizePost.contains("response_type=code"))
            assertTrue(authorizePost.contains("state=state-1"))
            assertFalse(client.toString().contains(SECRET))
        }
    }

    @Test
    fun `already approved app is a redirect straight to the loopback callback`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = scriptedDispatcher(mutableListOf(), mutableListOf(), authorizeRedirect = true)
            val client = BangumiPasswordLoginClient(baseUrl = server.url("/").toString().trimEnd('/'))
            assertTrue(client.startChallenge() is BangumiPasswordLoginStep.Challenge)
            val authorized = client.loginAndAuthorize(
                email = "user@example.com",
                password = SECRET,
                captcha = "ok",
                clientId = "test-id",
                redirectUri = BangumiOAuthConstants.CALLBACK_URL,
                state = "state-1",
            ) as BangumiPasswordLoginStep.Authorized
            assertEquals(
                "http://127.0.0.1:13712/callback?code=auth-code&state=state-1",
                authorized.callbackUrl,
            )
        }
    }

    @Test
    fun `lockout message is returned without a new captcha`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.startsWith("/login") -> MockResponse().setBody(LOGIN_HTML)
                        path.startsWith("/signup/captcha") -> image()
                        path.startsWith("/FollowTheRabbit") -> MockResponse().setBody(LOCKOUT_HTML)
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            val client = BangumiPasswordLoginClient(baseUrl = server.url("/").toString().trimEnd('/'))
            client.startChallenge()
            val failed = client.loginAndAuthorize(
                email = "user@example.com",
                password = SECRET,
                captcha = "ok",
                clientId = "test-id",
                redirectUri = BangumiOAuthConstants.CALLBACK_URL,
                state = "state-1",
            )
            assertEquals(
                "累计 5 次错误尝试，15 分钟内您将不能登录本站。",
                (failed as BangumiPasswordLoginStep.Failed).message,
            )
            assertFalse(failed.message.contains(SECRET))
        }
    }

    @Test
    fun `unreachable Bangumi becomes a fixed message`() = runBlocking {
        val server = MockWebServer()
        server.start()
        val client = BangumiPasswordLoginClient(baseUrl = server.url("/").toString().trimEnd('/'))
        server.shutdown()
        val failed = client.startChallenge() as BangumiPasswordLoginStep.Failed
        assertEquals("无法连接 Bangumi，请检查电视的出站代理或网络。", failed.message)
    }

    private fun scriptedDispatcher(
        posts: MutableList<String>,
        paths: MutableList<String>,
        authorizeRedirect: Boolean,
    ): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (request.method == "POST") {
                    posts += request.body.readUtf8()
                    paths += path
                }
                return when {
                    path.startsWith("/login") -> MockResponse().setBody(LOGIN_HTML).setHeader("Set-Cookie", "chii_sid=sid; Path=/")
                    path.startsWith("/signup/captcha") -> image()
                    path.startsWith("/FollowTheRabbit") -> {
                        val badCaptcha = posts.last().contains("captcha_challenge_field=bad")
                        if (badCaptcha) {
                            MockResponse().setBody(ERROR_HTML)
                        } else {
                            MockResponse().setResponseCode(302)
                                .setHeader("Set-Cookie", "chii_auth=yes; Path=/")
                                .setHeader("Location", "/")
                        }
                    }
                    request.method == "GET" && path.startsWith("/oauth/authorize") ->
                        if (authorizeRedirect) {
                            MockResponse().setResponseCode(302).setHeader("Location", CALLBACK)
                        } else {
                            MockResponse().setBody(AUTHORIZE_HTML)
                        }
                    request.method == "POST" && path.startsWith("/oauth/authorize") ->
                        MockResponse().setResponseCode(302).setHeader("Location", CALLBACK)
                    path == "/" -> MockResponse().setBody("<html></html>")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

    private fun image(): MockResponse =
        MockResponse()
            .setHeader("Content-Type", "image/gif")
            .setBody(okio.Buffer().write(TINY_GIF))

    private companion object {
        const val SECRET = "s3cret-token-value"
        const val CALLBACK = "http://127.0.0.1:13712/callback?code=auth-code&state=state-1"
        val TINY_GIF = byteArrayOf(
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x3b,
        )
        const val LOGIN_HTML = """
            <form id="loginForm" method="post" action="/FollowTheRabbit">
            <input type="hidden" name="formhash" value="login-hash">
            </form>
        """
        const val ERROR_HTML = """
            <div id="colunmNotice"><div class="message"><p class="text">验证码错误，请返回重试。</p></div></div>
        """
        const val LOCKOUT_HTML = """
            <div id="colunmNotice"><div class="message"><p class="text">累计 5 次错误尝试，15 分钟内您将不能登录本站。</p></div></div>
        """
        const val AUTHORIZE_HTML = """
            <form id="authForm" method="post" name="auth">
            <input type="hidden" name="formhash" value="auth-hash">
            <input type="hidden" name="redirect_uri" value="http://127.0.0.1:13712/callback">
            <input type="hidden" name="client_id" value="test-id">
            <input type="submit" name="submit" value="授权">
            </form>
        """
    }
}
