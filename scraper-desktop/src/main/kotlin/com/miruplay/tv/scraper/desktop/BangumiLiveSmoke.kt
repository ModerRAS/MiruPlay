package com.miruplay.tv.scraper.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.ScraperResult
import kotlinx.serialization.json.add
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.time.Instant

data class BangumiLiveSmokeOptions(
    val query: String = "葬送的芙莉莲",
    val subjectId: String? = null,
    val expectedTitle: String? = null,
    val minResults: Int = 1,
    val reportPath: String? = null,
)

data class BangumiLiveSmokeReport(
    val query: String,
    val searchElapsedMs: Long,
    val resultCount: Int,
    val topResults: List<BangumiLiveSmokeResult>,
    val subjectId: String?,
    val detailsElapsedMs: Long?,
    val detailTitle: String?,
    val detailTitleCn: String?,
    val detailEpisodeCount: Int?,
    val detailRating: Float?,
    val episodeElapsedMs: Long?,
    val regularEpisodeCount: Int?,
)

data class BangumiLiveSmokeResult(
    val animeId: String,
    val title: String,
    val titleCn: String?,
    val matchedTitle: String,
    val confidence: Float,
)

fun parseBangumiLiveSmokeOptions(args: Array<String>): BangumiLiveSmokeOptions {
    val values = mutableMapOf<String, String>()
    var index = 0
    while (index < args.size) {
        val key = args[index]
        if (!key.startsWith("--")) {
            throw IllegalArgumentException("Unexpected argument: $key")
        }
        val value = args.getOrNull(index + 1)
            ?: throw IllegalArgumentException("Missing value for $key")
        values[key.removePrefix("--")] = value
        index += 2
    }

    return BangumiLiveSmokeOptions(
        query = values["query"]?.takeIf { it.isNotBlank() } ?: "葬送的芙莉莲",
        subjectId = values["subject-id"]?.takeIf { it.isNotBlank() },
        expectedTitle = values["expected-title"]?.takeIf { it.isNotBlank() },
        minResults = values["min-results"]?.toIntOrNull()?.coerceIn(1, 10) ?: 1,
        reportPath = values["report-path"]?.takeIf { it.isNotBlank() },
    )
}

suspend fun runBangumiLiveSmoke(
    options: BangumiLiveSmokeOptions,
    scraper: DesktopBangumiScraper = DesktopBangumiScraper(),
): Result<BangumiLiveSmokeReport> {
    val searchStartedAt = System.currentTimeMillis()
    val searchResults = when (val result = scraper.searchAnime(options.query)) {
        is Result.Success -> result.data
        is Result.Error -> return result
    }
    val searchElapsedMs = System.currentTimeMillis() - searchStartedAt

    if (searchResults.size < options.minResults) {
        return Result.failure(
            com.miruplay.tv.core.common.AppError.ScrapingError.NoMatchFound(options.query)
        )
    }

    val expectedTitle = options.expectedTitle
    if (!expectedTitle.isNullOrBlank()) {
        val matched = searchResults.any { result ->
            listOf(result.title, result.titleCn.orEmpty(), result.matchedTitle)
                .any { it.contains(expectedTitle, ignoreCase = true) }
        }
        if (!matched) {
            return Result.failure(
                com.miruplay.tv.core.common.AppError.ScrapingError.NoMatchFound(expectedTitle)
            )
        }
    }

    val subjectId = options.subjectId ?: searchResults.first().animeId
    val detailsStartedAt = System.currentTimeMillis()
    val detail: Anime = when (val result = scraper.getAnimeDetails(subjectId)) {
        is Result.Success -> result.data
        is Result.Error -> return result
    }
    val detailsElapsedMs = System.currentTimeMillis() - detailsStartedAt

    val episodesStartedAt = System.currentTimeMillis()
    val episodes = when (val result = scraper.getEpisodes(subjectId)) {
        is Result.Success -> result.data
        is Result.Error -> return result
    }
    val episodeElapsedMs = System.currentTimeMillis() - episodesStartedAt

    return Result.success(
        BangumiLiveSmokeReport(
            query = options.query,
            searchElapsedMs = searchElapsedMs,
            resultCount = searchResults.size,
            topResults = searchResults.take(5).map { it.toSmokeResult() },
            subjectId = subjectId,
            detailsElapsedMs = detailsElapsedMs,
            detailTitle = detail.title,
            detailTitleCn = detail.titleCn,
            detailEpisodeCount = detail.episodeCount,
            detailRating = detail.rating,
            episodeElapsedMs = episodeElapsedMs,
            regularEpisodeCount = episodes.size,
        )
    )
}

fun main(args: Array<String>) {
    runBlocking {
        val options = parseBangumiLiveSmokeOptions(args)
        when (val result = runBangumiLiveSmoke(options)) {
            is Result.Success -> {
                printBangumiLiveSmokeReport(result.data)
                options.reportPath?.let { reportPath ->
                    writeBangumiLiveSmokeReport(reportPath, result.data)
                }
            }
            is Result.Error -> error("Bangumi live smoke failed: ${result.error.toUserMessage()}")
        }
    }
}

private fun ScraperResult.toSmokeResult(): BangumiLiveSmokeResult =
    BangumiLiveSmokeResult(
        animeId = animeId,
        title = title,
        titleCn = titleCn,
        matchedTitle = matchedTitle,
        confidence = confidence,
    )

private fun printBangumiLiveSmokeReport(report: BangumiLiveSmokeReport) {
    println("Bangumi live smoke passed.")
    println("Query: ${report.query}")
    println("Search: ${report.resultCount} result(s) in ${report.searchElapsedMs} ms")
    report.topResults.forEach { result ->
        val title = result.titleCn?.takeIf { it.isNotBlank() } ?: result.title
        println(" - ${result.animeId}: $title (${(result.confidence * 100).toInt()}%)")
    }
    println(
        "Details: ${report.detailTitleCn ?: report.detailTitle.orEmpty()} " +
            "subject=${report.subjectId} eps=${report.detailEpisodeCount ?: 0} " +
            "rating=${report.detailRating ?: 0f} in ${report.detailsElapsedMs ?: 0L} ms"
    )
    println("Episodes: ${report.regularEpisodeCount ?: 0} regular episode(s) in ${report.episodeElapsedMs ?: 0L} ms")
}

private fun writeBangumiLiveSmokeReport(
    reportPath: String,
    report: BangumiLiveSmokeReport,
) {
    val outputFile = File(reportPath).absoluteFile
    outputFile.parentFile?.mkdirs()
    outputFile.writeText(buildBangumiLiveSmokeReportJson(report), Charsets.UTF_8)
    println("Wrote Bangumi live smoke report: ${outputFile.absolutePath}")
}

internal fun buildBangumiLiveSmokeReportJson(report: BangumiLiveSmokeReport): String =
    buildJsonObject {
        put("generatedAtUtc", Instant.now().toString())
        put("query", report.query)
        put("searchElapsedMs", report.searchElapsedMs)
        put("resultCount", report.resultCount)
        putJsonArray("topResults") {
            report.topResults.forEach { result ->
                add(
                    buildJsonObject {
                        put("animeId", result.animeId)
                        put("title", result.title)
                        result.titleCn?.let { put("titleCn", it) }
                        put("displayTitle", result.titleCn?.takeIf { it.isNotBlank() } ?: result.title)
                        put("matchedTitle", result.matchedTitle)
                        put("confidence", result.confidence)
                    }
                )
            }
        }
        report.subjectId?.let { put("subjectId", it) }
        report.detailsElapsedMs?.let { put("detailsElapsedMs", it) }
        report.detailTitle?.let { put("detailTitle", it) }
        report.detailTitleCn?.let { put("detailTitleCn", it) }
        report.detailEpisodeCount?.let { put("detailEpisodeCount", it) }
        report.detailRating?.let { put("detailRating", it) }
        report.episodeElapsedMs?.let { put("episodeElapsedMs", it) }
        report.regularEpisodeCount?.let { put("regularEpisodeCount", it) }
    }.toString()
