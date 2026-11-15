package com.miruplay.tv.ui.settings

import com.miruplay.tv.scraper.core.BangumiHttpProxyConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BangumiOAuthWebViewProxyTest {
    @Test
    fun `disabled config maps to no webview proxy rule`() {
        val config = BangumiHttpProxyConfig(enabled = false, host = "10.0.0.2", port = 7890)

        assertNull(config.toBangumiOAuthWebViewProxyRule())
    }

    @Test
    fun `blank host maps to no webview proxy rule`() {
        val emptyHost = BangumiHttpProxyConfig(enabled = true, host = "", port = 7890)
        val whitespaceHost = BangumiHttpProxyConfig(enabled = true, host = "   ", port = 7890)

        assertNull(emptyHost.toBangumiOAuthWebViewProxyRule())
        assertNull(whitespaceHost.toBangumiOAuthWebViewProxyRule())
    }

    @Test
    fun `port below valid range maps to no webview proxy rule`() {
        val config = BangumiHttpProxyConfig(enabled = true, host = "10.0.0.2", port = 0)

        assertNull(config.toBangumiOAuthWebViewProxyRule())
    }

    @Test
    fun `port above valid range maps to no webview proxy rule`() {
        val config = BangumiHttpProxyConfig(enabled = true, host = "10.0.0.2", port = 65_536)

        assertNull(config.toBangumiOAuthWebViewProxyRule())
    }

    @Test
    fun `enabled config maps to http proxy rule with loopback bypass`() {
        val config = BangumiHttpProxyConfig(enabled = true, host = "10.0.0.2", port = 7890)

        val rule = config.toBangumiOAuthWebViewProxyRule()

        assertNotNull(rule)
        assertEquals("http://10.0.0.2:7890", rule?.proxyRule)
        assertEquals("10.0.0.2:7890", rule?.displayAddress)
        assertEquals(listOf("localhost", "127.0.0.1"), rule?.bypassRules)
    }

    @Test
    fun `host surrounding whitespace is trimmed`() {
        val config = BangumiHttpProxyConfig(enabled = true, host = "  192.168.1.10  ", port = 8080)

        val rule = config.toBangumiOAuthWebViewProxyRule()

        assertNotNull(rule)
        assertEquals("http://192.168.1.10:8080", rule?.proxyRule)
        assertEquals("192.168.1.10:8080", rule?.displayAddress)
    }

    @Test
    fun `boundary ports are accepted`() {
        val minPort = BangumiHttpProxyConfig(enabled = true, host = "10.0.0.2", port = 1)
        val maxPort = BangumiHttpProxyConfig(enabled = true, host = "10.0.0.2", port = 65_535)

        assertEquals("http://10.0.0.2:1", minPort.toBangumiOAuthWebViewProxyRule()?.proxyRule)
        assertEquals("http://10.0.0.2:65535", maxPort.toBangumiOAuthWebViewProxyRule()?.proxyRule)
    }
}
