package com.miruplay.tv.desktop

import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.confidencePercentLabel
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.metadataBangumiTokenEmptyMessage
import com.miruplay.tv.model.metadataBangumiTokenSavedMessage

internal fun ScraperResult.bangumiResultTitle(): String =
    displayTitle()

internal fun ScraperResult.bangumiCandidateSummary(candidateSuffix: String): String =
    "${displayTitle()} / ${confidencePercentLabel()}$candidateSuffix"

internal data class DesktopBangumiTokenSaveResult(
    val token: String?,
    val configured: Boolean,
    val status: String,
)

internal fun desktopBangumiTokenSaveResult(
    input: String,
    existingToken: String?,
): DesktopBangumiTokenSaveResult {
    val normalized = input.trim()
    if (normalized.isBlank()) {
        return DesktopBangumiTokenSaveResult(
            token = existingToken,
            configured = !existingToken.isNullOrBlank(),
            status = metadataBangumiTokenEmptyMessage(),
        )
    }
    return DesktopBangumiTokenSaveResult(
        token = normalized,
        configured = true,
        status = metadataBangumiTokenSavedMessage(),
    )
}
