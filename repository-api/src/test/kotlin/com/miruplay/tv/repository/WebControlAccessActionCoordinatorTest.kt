package com.miruplay.tv.repository

import java.io.Closeable
import org.junit.Assert.assertEquals
import org.junit.Test

class WebControlAccessActionCoordinatorTest {
    @Test
    fun `current snapshot hides urls while disabled`() {
        val manager = FakeWebControlAccessManager(enabled = false, token = "token")
        val coordinator = coordinator(manager)

        val snapshot = coordinator.current()

        assertEquals(WebControlAccessSnapshot(enabled = false, accessToken = "token", urls = emptyList()), snapshot)
    }

    @Test
    fun `enable snapshot includes current token urls`() {
        val manager = FakeWebControlAccessManager(enabled = false, token = "token")
        val coordinator = coordinator(manager)

        val snapshot = coordinator.setEnabled(true)

        assertEquals(true, manager.webControlEnabled)
        assertEquals(
            WebControlAccessSnapshot(
                enabled = true,
                accessToken = "token",
                urls = listOf("url-token"),
            ),
            snapshot,
        )
    }

    @Test
    fun `disable snapshot keeps token and clears urls`() {
        val manager = FakeWebControlAccessManager(enabled = true, token = "token")
        val coordinator = coordinator(manager)

        val snapshot = coordinator.setEnabled(false)

        assertEquals(false, manager.webControlEnabled)
        assertEquals(WebControlAccessSnapshot(enabled = false, accessToken = "token", urls = emptyList()), snapshot)
    }

    @Test
    fun `rotate token returns rotated enabled snapshot`() {
        val manager = FakeWebControlAccessManager(enabled = true, token = "old")
        val coordinator = coordinator(manager)

        val snapshot = coordinator.rotateAccessToken()

        assertEquals("rotated-1", manager.accessToken)
        assertEquals(
            WebControlAccessSnapshot(
                enabled = true,
                accessToken = "rotated-1",
                urls = listOf("url-rotated-1"),
            ),
            snapshot,
        )
    }

    @Test
    fun `refresh urls rebuilds urls without mutating enabled state`() {
        val manager = FakeWebControlAccessManager(enabled = true, token = "token")
        val coordinator = coordinator(manager)

        val snapshot = coordinator.refreshUrls()

        assertEquals(true, manager.webControlEnabled)
        assertEquals(WebControlAccessSnapshot(true, "token", listOf("url-token")), snapshot)
    }

    private fun coordinator(manager: FakeWebControlAccessManager): WebControlAccessActionCoordinator =
        WebControlAccessActionCoordinator(
            accessManager = manager,
            accessUrls = { token -> listOf("url-$token") },
        )

    private class FakeWebControlAccessManager(
        enabled: Boolean,
        private var token: String,
    ) : WebControlAccessManager {
        override var webControlEnabled: Boolean = enabled

        override val accessToken: String
            get() = token

        private var rotations = 0

        override fun rotateAccessToken(): String {
            rotations += 1
            token = "rotated-$rotations"
            return token
        }

        override fun addEnabledChangeListener(onChanged: (Boolean) -> Unit): Closeable =
            Closeable { }
    }
}
