package com.miruplay.tv.sync

import com.miruplay.tv.model.metadataBangumiTokenClearedMessage
import com.miruplay.tv.model.saveBangumiTokenFormResult
import com.miruplay.tv.repository.AppCredentialStore

sealed class BangumiTokenActionResult {
    data class Saved(
        val token: String?,
        val configured: Boolean,
        val status: String,
        val shouldClearInput: Boolean,
    ) : BangumiTokenActionResult()

    data class Cleared(val status: String) : BangumiTokenActionResult()
}

class BangumiCredentialActionCoordinator(
    private val credentials: AppCredentialStore,
) {
    fun saveToken(input: String): BangumiTokenActionResult.Saved {
        val result = saveBangumiTokenFormResult(
            input = input,
            existingToken = credentials.bangumiAccessToken,
        )
        if (result.shouldPersistTokenInput) {
            credentials.bangumiAccessToken = result.token
        }
        return BangumiTokenActionResult.Saved(
            token = credentials.bangumiAccessToken,
            configured = !credentials.bangumiAccessToken.isNullOrBlank(),
            status = result.status,
            shouldClearInput = result.shouldPersistTokenInput,
        )
    }

    fun clearToken(): BangumiTokenActionResult.Cleared {
        credentials.clearBangumiToken()
        return BangumiTokenActionResult.Cleared(metadataBangumiTokenClearedMessage())
    }
}
