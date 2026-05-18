package com.miruplay.tv.sync.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy

class HttpProxyConfigTest {
    @Test
    fun `normalize trims host and clamps port`() {
        assertEquals(
            HttpProxyConfig(enabled = true, host = "127.0.0.1", port = 1),
            HttpProxyConfig.normalize(enabled = true, host = " 127.0.0.1 ", port = -20)
        )
        assertEquals(
            HttpProxyConfig(enabled = true, host = "127.0.0.1", port = 65535),
            HttpProxyConfig.normalize(enabled = true, host = "127.0.0.1", port = 99999)
        )
    }

    @Test
    fun `blank or disabled proxy resolves to no proxy`() {
        assertSame(Proxy.NO_PROXY, HttpProxyConfig.normalize(enabled = false, host = "127.0.0.1", port = 7890).toJavaProxy())
        assertSame(Proxy.NO_PROXY, HttpProxyConfig.normalize(enabled = true, host = "   ", port = 7890).toJavaProxy())
    }

    @Test
    fun `enabled proxy creates an http proxy address`() {
        val proxy = HttpProxyConfig.normalize(enabled = true, host = "127.0.0.1", port = 7890).toJavaProxy()
        val address = proxy.address() as InetSocketAddress

        assertEquals(Proxy.Type.HTTP, proxy.type())
        assertEquals("127.0.0.1", address.hostString)
        assertEquals(7890, address.port)
    }
}
