package com.miruplay.tv.desktop

import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.confidencePercentLabel
import com.miruplay.tv.model.displayTitle

internal fun ScraperResult.bangumiResultTitle(): String =
    displayTitle()

internal fun ScraperResult.bangumiCandidateSummary(candidateSuffix: String): String =
    "${displayTitle()} / ${confidencePercentLabel()}$candidateSuffix"
