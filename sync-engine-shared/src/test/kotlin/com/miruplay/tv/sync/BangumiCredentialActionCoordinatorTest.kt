package com.miruplay.tv.sync

import com.miruplay.tv.model.metadataBangumiTokenClearedMessage
import com.miruplay.tv.model.metadataBangumiTokenEmptyMessage
import com.miruplay.tv.model.metadataBangumiTokenSavedMessage
import com.miruplay.tv.repository.AppCredentialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BangumiCredentialActionCoordinatorTest {
    @Test
    fun `save token trims and persists nonblank token`() {
        val credentials = FakeAppCredentialStore()
        val coordinator = BangumiCredentialActionCoordinator(credentials)

        val result = coordinator.saveToken(" token ")

        assertEquals("token", credentials.bangumiAccessToken)
        assertEquals(
            BangumiTokenActionResult.Saved(
                token = "token",
                configured = true,
                status = metadataBangumiTokenSavedMessage(),
                shouldClearInput = true,
            ),
            result,
        )
    }

    @Test
    fun `blank token keeps existing token and reports empty status`() {
        val credentials = FakeAppCredentialStore(bangumiToken = "old-token")
        val coordinator = BangumiCredentialActionCoordinator(credentials)

        val result = coordinator.saveToken(" ")

        assertEquals("old-token", credentials.bangumiAccessToken)
        assertEquals(
            BangumiTokenActionResult.Saved(
                token = "old-token",
                configured = true,
                status = metadataBangumiTokenEmptyMessage(),
                shouldClearInput = false,
            ),
            result,
        )
    }

    @Test
    fun `blank token without existing token remains unconfigured`() {
        val credentials = FakeAppCredentialStore()
        val coordinator = BangumiCredentialActionCoordinator(credentials)

        val result = coordinator.saveToken("")

        assertNull(credentials.bangumiAccessToken)
        assertEquals(
            BangumiTokenActionResult.Saved(
                token = null,
                configured = false,
                status = metadataBangumiTokenEmptyMessage(),
                shouldClearInput = false,
            ),
            result,
        )
    }

    @Test
    fun `clear token removes persisted value and reports shared status`() {
        val credentials = FakeAppCredentialStore(bangumiToken = "old-token")
        val coordinator = BangumiCredentialActionCoordinator(credentials)

        val result = coordinator.clearToken()

        assertNull(credentials.bangumiAccessToken)
        assertEquals(BangumiTokenActionResult.Cleared(metadataBangumiTokenClearedMessage()), result)
    }

    private class FakeAppCredentialStore(
        private var cloudToken: String? = null,
        private var cloudPassword: String? = null,
        bangumiToken: String? = null,
    ) : AppCredentialStore {
        override var cloudDriveToken: String?
            get() = cloudToken
            set(value) {
                cloudToken = value
            }

        override var cloudDrivePassword: String?
            get() = cloudPassword
            set(value) {
                cloudPassword = value
            }

        override var bangumiAccessToken: String? = bangumiToken

        override fun clearCloudDriveCredentials() {
            cloudToken = null
            cloudPassword = null
        }

        override fun clearBangumiToken() {
            bangumiAccessToken = null
        }
    }
}
