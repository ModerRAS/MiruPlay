package com.miruplay.tv.repository

import com.miruplay.tv.core.common.buildWebControlAccessUrls

data class WebControlAccessSnapshot(
    val enabled: Boolean,
    val accessToken: String,
    val urls: List<String>,
)

class WebControlAccessActionCoordinator(
    private val accessManager: WebControlAccessManager,
    private val accessUrls: (String) -> List<String> = ::buildWebControlAccessUrls,
) {
    fun current(): WebControlAccessSnapshot =
        accessManager.snapshot()

    fun setEnabled(enabled: Boolean): WebControlAccessSnapshot {
        accessManager.webControlEnabled = enabled
        return accessManager.snapshot()
    }

    fun rotateAccessToken(): WebControlAccessSnapshot {
        accessManager.rotateAccessToken()
        return accessManager.snapshot()
    }

    fun refreshUrls(): WebControlAccessSnapshot =
        accessManager.snapshot()

    private fun WebControlAccessManager.snapshot(): WebControlAccessSnapshot {
        val enabled = webControlEnabled
        val token = accessToken
        return WebControlAccessSnapshot(
            enabled = enabled,
            accessToken = token,
            urls = if (enabled) accessUrls(token) else emptyList(),
        )
    }
}
