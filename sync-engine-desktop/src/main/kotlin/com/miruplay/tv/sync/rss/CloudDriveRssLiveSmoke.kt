package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.clouddrive.CloudDrivePaths
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.clouddrive.GrpcCloudDriveClient
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.time.Instant

data class CloudDriveRssLiveSmokeOptions(
    val endpoint: String,
    val token: String,
    val rssUrl: String,
    val inboxPath: String,
    val libraryPath: String,
    val filterRegex: String? = null,
    val maxPreviewItems: Int = 20,
    val proxyEnabled: Boolean = false,
    val proxyHost: String = "",
    val proxyPort: Int = 1080,
    val reportPath: String? = null,
    val submit: Boolean = false,
    val submitConfirmation: String? = null,
    val submitLimit: Int = 1,
    val organize: Boolean = false,
    val organizeConfirmation: String? = null,
)

data class CloudDriveRssLiveSmokeReport(
    val friendlyName: String,
    val rootDir: String,
    val permissions: CloudDriveRssLiveSmokePermissions,
    val inboxPath: String,
    val inboxItemCount: Int,
    val libraryPath: String,
    val libraryItemCount: Int,
    val rssUrl: String,
    val feedItemCount: Int,
    val candidateCount: Int,
    val skippedByFilterCount: Int,
    val missingSubmissionCount: Int,
    val magnetCandidateCount: Int,
    val torrentCandidateCount: Int,
    val otherCandidateCount: Int,
    val submitMode: Boolean,
    val submitAttemptedCount: Int,
    val submitSucceededCount: Int,
    val submitPreparedTorrentCount: Int,
    val postSubmitInboxItemCount: Int?,
    val organizeMode: Boolean,
    val organizeMovedCount: Int,
    val postOrganizeInboxItemCount: Int?,
    val postOrganizeLibraryItemCount: Int?,
    val previewItems: List<CloudDriveRssLiveSmokeItem>,
)

data class CloudDriveRssLiveSmokePermissions(
    val allowList: Boolean,
    val allowCreateFolder: Boolean,
    val allowCreateFile: Boolean,
    val allowWrite: Boolean,
    val allowMove: Boolean,
    val allowAddOfflineDownload: Boolean,
)

data class CloudDriveRssLiveSmokeItem(
    val title: String,
    val guid: String?,
    val submissionUrl: String?,
    val status: CloudDriveRssLiveSmokeItemStatus,
    val submissionType: CloudDriveRssLiveSmokeSubmissionType,
)

enum class CloudDriveRssLiveSmokeItemStatus {
    WOULD_SUBMIT,
    SKIPPED_FILTER,
    MISSING_SUBMISSION,
}

enum class CloudDriveRssLiveSmokeSubmissionType {
    MAGNET,
    TORRENT,
    OTHER,
    NONE,
}

private const val LIVE_SUBMIT_CONFIRMATION = "I_UNDERSTAND_THIS_SUBMITS_REAL_CLOUDDRIVE_DOWNLOADS"
private const val LIVE_ORGANIZE_CONFIRMATION = "I_UNDERSTAND_THIS_MOVES_REAL_CLOUDDRIVE_FILES"
private val RSS_URL_SCHEME_REDACTIONS = mapOf(
    "file" to "file:///<redacted>",
    "magnet" to "magnet:?<redacted>",
)

fun parseCloudDriveRssLiveSmokeOptions(args: Array<String>): CloudDriveRssLiveSmokeOptions {
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

    return CloudDriveRssLiveSmokeOptions(
        endpoint = values.required("endpoint"),
        token = values.required("token"),
        rssUrl = values.required("rss-url"),
        inboxPath = values.required("inbox"),
        libraryPath = values.required("library"),
        filterRegex = values["filter"]?.takeIf { it.isNotBlank() },
        maxPreviewItems = values["max-preview"]
            ?.toIntOrNull()
            ?.coerceIn(1, 100)
            ?: 20,
        proxyEnabled = values["proxy-enabled"]?.toBooleanStrictOrNull() ?: false,
        proxyHost = values["proxy-host"].orEmpty(),
        proxyPort = values["proxy-port"]?.toIntOrNull()?.coerceIn(1, 65535) ?: 1080,
        reportPath = values["report-path"]?.takeIf { it.isNotBlank() },
        submit = values["submit"]?.toBooleanStrictOrNull() ?: false,
        submitConfirmation = values["submit-confirmation"]?.takeIf { it.isNotBlank() },
        submitLimit = values["submit-limit"]
            ?.toIntOrNull()
            ?.coerceIn(1, 10)
            ?: 1,
        organize = values["organize"]?.toBooleanStrictOrNull() ?: false,
        organizeConfirmation = values["organize-confirmation"]?.takeIf { it.isNotBlank() },
    )
}

suspend fun runCloudDriveRssLiveSmoke(
    options: CloudDriveRssLiveSmokeOptions,
    cloudDriveClient: CloudDriveClient = GrpcCloudDriveClient(),
    feedReader: RssFeedReader = RssFeedFetcher(),
    submissionPreparer: CloudDriveRssSubmissionPreparer = CloudDriveRssSubmissionPreparer(cloudDriveClient),
    organizer: CloudDriveLibraryOrganizer = CloudDriveLibraryOrganizer(cloudDriveClient),
): Result<CloudDriveRssLiveSmokeReport> {
    val normalizedInbox = CloudDrivePaths.normalizeScoped(options.inboxPath)
    val normalizedLibrary = CloudDrivePaths.normalizeScoped(options.libraryPath)
    val pathValidation = validateSmokePaths(normalizedInbox, normalizedLibrary)
    if (pathValidation is Result.Error) return pathValidation

    val tokenInfo = when (val result = cloudDriveClient.getApiTokenInfo(options.endpoint, options.token)) {
        is Result.Success -> result.data
        is Result.Error -> return result
    }

    val endpoint = CloudDriveEndpoint(options.endpoint, options.token)
    val inboxFiles = when (val result = cloudDriveClient.listFolder(endpoint, normalizedInbox, forceRefresh = false)) {
        is Result.Success -> result.data
        is Result.Error -> return result
    }
    val libraryFiles = when (val result = cloudDriveClient.listFolder(endpoint, normalizedLibrary, forceRefresh = false)) {
        is Result.Success -> result.data
        is Result.Error -> return result
    }

    feedReader.configureProxy(options.proxyEnabled, options.proxyHost, options.proxyPort)
    val feedItems = when (val result = feedReader.fetch(options.rssUrl)) {
        is Result.Success -> result.data
        is Result.Error -> return result
    }

    val decisions = when (val result = RssSubmissionPlanner.plan(feedItems, options.filterRegex)) {
        is Result.Success -> result.data.map { it.toLiveSmokeItem() }
        is Result.Error -> return result
    }

    val submitResult = if (options.submit) {
        submitLiveSmokeCandidates(
            options = options,
            cloudDriveClient = cloudDriveClient,
            endpoint = endpoint,
            inboxPath = normalizedInbox,
            decisions = decisions,
            submissionPreparer = submissionPreparer,
        )
    } else {
        Result.success(CloudDriveRssSubmitSmokeResult())
    }
    if (submitResult is Result.Error) return submitResult
    val submitted = (submitResult as Result.Success).data

    val postSubmitInboxItemCount = if (options.submit && submitted.succeededCount > 0) {
        when (val result = cloudDriveClient.listFolder(endpoint, normalizedInbox, forceRefresh = true)) {
            is Result.Success -> result.data.size
            is Result.Error -> return result
        }
    } else {
        null
    }

    val organizeResult = if (options.organize) {
        organizeLiveSmokeFiles(
            options = options,
            endpoint = endpoint,
            organizer = organizer,
            inboxPath = normalizedInbox,
            libraryPath = normalizedLibrary,
        )
    } else {
        Result.success(0)
    }
    if (organizeResult is Result.Error) return organizeResult
    val organizedCount = (organizeResult as Result.Success).data

    val postOrganizeInboxFiles: List<CloudDriveFileInfo>?
    val postOrganizeLibraryFiles: List<CloudDriveFileInfo>?
    if (options.organize) {
        postOrganizeInboxFiles = when (val result = cloudDriveClient.listFolder(endpoint, normalizedInbox, forceRefresh = true)) {
            is Result.Success -> result.data
            is Result.Error -> return result
        }
        postOrganizeLibraryFiles = when (val result = cloudDriveClient.listFolder(endpoint, normalizedLibrary, forceRefresh = true)) {
            is Result.Success -> result.data
            is Result.Error -> return result
        }
    } else {
        postOrganizeInboxFiles = null
        postOrganizeLibraryFiles = null
    }

    return Result.success(
        CloudDriveRssLiveSmokeReport(
            friendlyName = tokenInfo.friendlyName,
            rootDir = tokenInfo.rootDir,
            permissions = tokenInfo.toSmokePermissions(),
            inboxPath = normalizedInbox,
            inboxItemCount = inboxFiles.size,
            libraryPath = normalizedLibrary,
            libraryItemCount = libraryFiles.size,
            rssUrl = options.rssUrl,
            feedItemCount = feedItems.size,
            candidateCount = decisions.count { it.status == CloudDriveRssLiveSmokeItemStatus.WOULD_SUBMIT },
            skippedByFilterCount = decisions.count { it.status == CloudDriveRssLiveSmokeItemStatus.SKIPPED_FILTER },
            missingSubmissionCount = decisions.count { it.status == CloudDriveRssLiveSmokeItemStatus.MISSING_SUBMISSION },
            magnetCandidateCount = decisions.count {
                it.status == CloudDriveRssLiveSmokeItemStatus.WOULD_SUBMIT &&
                    it.submissionType == CloudDriveRssLiveSmokeSubmissionType.MAGNET
            },
            torrentCandidateCount = decisions.count {
                it.status == CloudDriveRssLiveSmokeItemStatus.WOULD_SUBMIT &&
                    it.submissionType == CloudDriveRssLiveSmokeSubmissionType.TORRENT
            },
            otherCandidateCount = decisions.count {
                it.status == CloudDriveRssLiveSmokeItemStatus.WOULD_SUBMIT &&
                    it.submissionType == CloudDriveRssLiveSmokeSubmissionType.OTHER
            },
            submitMode = options.submit,
            submitAttemptedCount = submitted.attemptedCount,
            submitSucceededCount = submitted.succeededCount,
            submitPreparedTorrentCount = submitted.preparedTorrentCount,
            postSubmitInboxItemCount = postSubmitInboxItemCount,
            organizeMode = options.organize,
            organizeMovedCount = organizedCount,
            postOrganizeInboxItemCount = postOrganizeInboxFiles?.size,
            postOrganizeLibraryItemCount = postOrganizeLibraryFiles?.size,
            previewItems = decisions.take(options.maxPreviewItems),
        )
    )
}

fun main(args: Array<String>) {
    runBlocking {
        val options = parseCloudDriveRssLiveSmokeOptions(args)
        when (val result = runCloudDriveRssLiveSmoke(options)) {
            is Result.Success -> {
                printCloudDriveRssLiveSmokeReport(result.data)
                options.reportPath?.let { reportPath ->
                    writeCloudDriveRssLiveSmokeReport(reportPath, options, result.data)
                }
            }
            is Result.Error -> error("CloudDrive RSS smoke failed: ${result.error.toUserMessage()}")
        }
    }
}

private data class CloudDriveRssSubmitSmokeResult(
    val attemptedCount: Int = 0,
    val succeededCount: Int = 0,
    val preparedTorrentCount: Int = 0,
)

private suspend fun submitLiveSmokeCandidates(
    options: CloudDriveRssLiveSmokeOptions,
    cloudDriveClient: CloudDriveClient,
    endpoint: CloudDriveEndpoint,
    inboxPath: String,
    decisions: List<CloudDriveRssLiveSmokeItem>,
    submissionPreparer: CloudDriveRssSubmissionPreparer,
): Result<CloudDriveRssSubmitSmokeResult> {
    if (options.submitConfirmation != LIVE_SUBMIT_CONFIRMATION) {
        return Result.failure(
            AppError.SyncError.WriteFailed(
                "CloudDrive RSS smoke",
                "live submit requires --submit-confirmation $LIVE_SUBMIT_CONFIRMATION",
            )
        )
    }

    val candidates = decisions
        .filter { it.status == CloudDriveRssLiveSmokeItemStatus.WOULD_SUBMIT }
        .take(options.submitLimit)
    if (candidates.isEmpty()) {
        return Result.failure(
            AppError.SyncError.WriteFailed(
                "CloudDrive RSS smoke",
                "no RSS candidate is eligible for live offline submission",
            )
        )
    }

    submissionPreparer.configureProxy(options.proxyEnabled, options.proxyHost, options.proxyPort)
    val prepared = mutableListOf<PreparedRssSubmission>()
    for (candidate in candidates) {
        val submissionUrl = candidate.submissionUrl?.takeIf(String::isNotBlank)
            ?: return Result.failure(
                AppError.SyncError.WriteFailed(
                    "CloudDrive RSS smoke",
                    "candidate is missing a submission URL",
                )
            )
        val item = RssFeedItem(
            title = candidate.title,
            guid = candidate.guid,
            link = if (candidate.submissionType == CloudDriveRssLiveSmokeSubmissionType.TORRENT) null else submissionUrl,
            enclosureUrl = if (candidate.submissionType == CloudDriveRssLiveSmokeSubmissionType.TORRENT) submissionUrl else null,
        )
        val itemKey = RssSubmissionPlanner.stableItemKey(item, submissionUrl)
        val preparedSubmission = submissionPreparer.prepare(endpoint, item, itemKey, submissionUrl, inboxPath)
        if (preparedSubmission is Result.Error) return preparedSubmission
        prepared += (preparedSubmission as Result.Success).data
    }

    return when (val result = cloudDriveClient.addOfflineFiles(endpoint, prepared.map { it.submissionUrl }, inboxPath)) {
        is Result.Success -> Result.success(
            CloudDriveRssSubmitSmokeResult(
                attemptedCount = prepared.size,
                succeededCount = prepared.size,
                preparedTorrentCount = prepared.count { it.stagedTorrentPath != null },
            )
        )
        is Result.Error -> result
    }
}

private suspend fun organizeLiveSmokeFiles(
    options: CloudDriveRssLiveSmokeOptions,
    endpoint: CloudDriveEndpoint,
    organizer: CloudDriveLibraryOrganizer,
    inboxPath: String,
    libraryPath: String,
): Result<Int> {
    if (options.organizeConfirmation != LIVE_ORGANIZE_CONFIRMATION) {
        return Result.failure(
            AppError.SyncError.WriteFailed(
                "CloudDrive RSS smoke",
                "live organize requires --organize-confirmation $LIVE_ORGANIZE_CONFIRMATION",
            )
        )
    }
    return organizer.organize(endpoint, inboxPath, libraryPath)
}

private fun validateSmokePaths(inboxPath: String, libraryPath: String): Result<Unit> {
    if (!CloudDrivePaths.isScopedDirectory(inboxPath)) {
        return Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "请设置非根目录作为下载目录 A"))
    }
    if (!CloudDrivePaths.isScopedDirectory(libraryPath)) {
        return Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "请设置非根目录作为整理目录 B"))
    }
    if (CloudDrivePaths.isSameOrChild(libraryPath, inboxPath)) {
        return Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "整理目录 B 不能放在下载目录 A 内部"))
    }
    return Result.success(Unit)
}

private fun CloudDriveTokenInfo.toSmokePermissions(): CloudDriveRssLiveSmokePermissions =
    CloudDriveRssLiveSmokePermissions(
        allowList = allowList,
        allowCreateFolder = allowCreateFolder,
        allowCreateFile = allowCreateFile,
        allowWrite = allowWrite,
        allowMove = allowMove,
        allowAddOfflineDownload = allowAddOfflineDownload,
    )

private fun RssSubmissionDecision.toLiveSmokeItem(): CloudDriveRssLiveSmokeItem =
    CloudDriveRssLiveSmokeItem(
        title = item.title,
        guid = item.guid,
        submissionUrl = submissionUrl,
        status = when (status) {
            RssSubmissionDecisionStatus.WOULD_SUBMIT -> CloudDriveRssLiveSmokeItemStatus.WOULD_SUBMIT
            RssSubmissionDecisionStatus.SKIPPED_FILTER -> CloudDriveRssLiveSmokeItemStatus.SKIPPED_FILTER
            RssSubmissionDecisionStatus.MISSING_SUBMISSION -> CloudDriveRssLiveSmokeItemStatus.MISSING_SUBMISSION
        },
        submissionType = when (submissionType) {
            RssSubmissionUrlType.MAGNET -> CloudDriveRssLiveSmokeSubmissionType.MAGNET
            RssSubmissionUrlType.TORRENT -> CloudDriveRssLiveSmokeSubmissionType.TORRENT
            RssSubmissionUrlType.OTHER -> CloudDriveRssLiveSmokeSubmissionType.OTHER
            RssSubmissionUrlType.NONE -> CloudDriveRssLiveSmokeSubmissionType.NONE
        },
    )

private fun printCloudDriveRssLiveSmokeReport(report: CloudDriveRssLiveSmokeReport) {
    println(if (report.submitMode) "CloudDrive RSS live submit smoke passed." else "CloudDrive RSS dry-run passed.")
    println("Friendly name: ${report.friendlyName.ifBlank { "(none)" }}")
    println("Root dir: ${report.rootDir.ifBlank { "/" }}")
    println(
        "Permissions: list=${report.permissions.allowList}, createFolder=${report.permissions.allowCreateFolder}, " +
            "createFile=${report.permissions.allowCreateFile}, write=${report.permissions.allowWrite}, " +
            "move=${report.permissions.allowMove}, offline=${report.permissions.allowAddOfflineDownload}"
    )
    println("Inbox: ${report.inboxPath} (${report.inboxItemCount} item(s))")
    println("Library: ${report.libraryPath} (${report.libraryItemCount} item(s))")
    println("RSS: ${redactCloudDriveRssEvidenceUrl(report.rssUrl)} sha256=${sha256Hex(report.rssUrl)}")
    println(
        "Feed items=${report.feedItemCount}, wouldSubmit=${report.candidateCount}, " +
            "skippedByFilter=${report.skippedByFilterCount}, missingSubmission=${report.missingSubmissionCount}"
    )
    println(
        "Submission types: magnet=${report.magnetCandidateCount}, torrent=${report.torrentCandidateCount}, " +
            "other=${report.otherCandidateCount}"
    )
    report.previewItems.forEach { item ->
        val guid = item.guid?.let { " guidSha256=${sha256Hex(it)}" }.orEmpty()
        val url = item.submissionUrl?.let { " url=${redactCloudDriveRssEvidenceUrl(it)} sha256=${sha256Hex(it)}" }.orEmpty()
        println(" - [${item.status}/${item.submissionType}] ${item.title}$guid$url")
    }
    if (report.submitMode) {
        println("Submitted ${report.submitSucceededCount}/${report.submitAttemptedCount} offline download candidate(s).")
        println("Prepared torrent submissions: ${report.submitPreparedTorrentCount}.")
        report.postSubmitInboxItemCount?.let { println("Post-submit inbox listing: $it item(s)") }
    } else {
        println("No offline downloads were submitted by this dry-run.")
    }
    if (report.organizeMode) {
        println("Moved ${report.organizeMovedCount} downloaded file(s) into the library.")
        report.postOrganizeInboxItemCount?.let { println("Post-organize inbox listing: $it item(s)") }
        report.postOrganizeLibraryItemCount?.let { println("Post-organize library listing: $it item(s)") }
    } else {
        println("No CloudDrive files were moved by this run.")
    }
}

private fun writeCloudDriveRssLiveSmokeReport(
    reportPath: String,
    options: CloudDriveRssLiveSmokeOptions,
    report: CloudDriveRssLiveSmokeReport,
) {
    val outputFile = File(reportPath).absoluteFile
    outputFile.parentFile?.mkdirs()
    outputFile.writeText(buildCloudDriveRssLiveSmokeReportJson(options, report), Charsets.UTF_8)
    println("Wrote CloudDrive RSS smoke report: ${outputFile.absolutePath}")
}

internal fun buildCloudDriveRssLiveSmokeReportJson(
    options: CloudDriveRssLiveSmokeOptions,
    report: CloudDriveRssLiveSmokeReport,
): String {
    val payload = buildJsonObject {
        put("generatedAtUtc", Instant.now().toString())
        put("endpoint", options.endpoint)
        put("rssUrl", redactCloudDriveRssEvidenceUrl(options.rssUrl))
        putJsonObject("rssUrlEvidence") {
            putUrlEvidenceFields(options.rssUrl)
        }
        put("inboxPath", report.inboxPath)
        put("libraryPath", report.libraryPath)
        put("feedItemCount", report.feedItemCount)
        put("candidateCount", report.candidateCount)
        put("skippedByFilterCount", report.skippedByFilterCount)
        put("missingSubmissionCount", report.missingSubmissionCount)
        put("magnetCandidateCount", report.magnetCandidateCount)
        put("torrentCandidateCount", report.torrentCandidateCount)
        put("otherCandidateCount", report.otherCandidateCount)
        putJsonObject("liveSubmit") {
            put("enabled", report.submitMode)
            put("attemptedCount", report.submitAttemptedCount)
            put("succeededCount", report.submitSucceededCount)
            put("preparedTorrentCount", report.submitPreparedTorrentCount)
            report.postSubmitInboxItemCount?.let { put("postSubmitInboxItemCount", it) }
        }
        putJsonObject("organize") {
            put("enabled", report.organizeMode)
            put("movedCount", report.organizeMovedCount)
            report.postOrganizeInboxItemCount?.let { put("postOrganizeInboxItemCount", it) }
            report.postOrganizeLibraryItemCount?.let { put("postOrganizeLibraryItemCount", it) }
        }
        putJsonObject("tokenInfo") {
            put("friendlyName", report.friendlyName)
            put("rootDir", report.rootDir)
            putJsonObject("permissions") {
                put("allowList", report.permissions.allowList)
                put("allowCreateFolder", report.permissions.allowCreateFolder)
                put("allowCreateFile", report.permissions.allowCreateFile)
                put("allowWrite", report.permissions.allowWrite)
                put("allowMove", report.permissions.allowMove)
                put("allowAddOfflineDownload", report.permissions.allowAddOfflineDownload)
            }
        }
        putJsonArray("previewItems") {
            report.previewItems.forEach { item ->
                add(
                    buildJsonObject {
                        put("title", item.title)
                        val guid = item.guid?.takeIf { it.isNotBlank() }
                        put("hasGuid", guid != null)
                        guid?.let { put("guidSha256", sha256Hex(it)) }
                        val submissionUrl = item.submissionUrl?.takeIf { it.isNotBlank() }
                        put("hasSubmissionUrl", submissionUrl != null)
                        submissionUrl?.let {
                            putJsonObject("submissionUrlEvidence") {
                                putUrlEvidenceFields(it)
                            }
                        }
                        put("status", item.status.name)
                        put("submissionType", item.submissionType.name)
                    }
                )
            }
        }
    }
    return payload.toString()
}

internal fun redactCloudDriveRssEvidenceUrl(value: String): String {
    val trimmed = value.trim()
    val uri = runCatching { URI(trimmed) }.getOrNull()
    val scheme = uri?.scheme?.lowercase()
        ?: trimmed.substringBefore(':', missingDelimiterValue = "").lowercase().ifBlank { null }
    return when (scheme) {
        "http",
        "https",
        -> {
            val authority = uri?.redactedAuthority().orEmpty()
            if (authority.isBlank()) "$scheme://<redacted>/..." else "$scheme://$authority/..."
        }
        "file" -> "file:///<redacted>"
        "magnet" -> "magnet:?<redacted>"
        null -> "<redacted>"
        else -> "$scheme:<redacted>"
    }
}

private fun JsonObjectBuilder.putUrlEvidenceFields(value: String) {
    val trimmed = value.trim()
    val uri = runCatching { URI(trimmed) }.getOrNull()
    val scheme = uri?.scheme?.lowercase()
        ?: trimmed.substringBefore(':', missingDelimiterValue = "").lowercase()
    put("redacted", redactCloudDriveRssEvidenceUrl(trimmed))
    put("scheme", scheme)
    put("host", uri?.redactedHost().orEmpty())
    put("sha256", sha256Hex(trimmed))
}

private fun URI.redactedAuthority(): String =
    rawAuthority
        ?.substringAfterLast("@")
        ?.takeIf { it.isNotBlank() }
        ?: host?.let { host ->
            if (port >= 0) "$host:$port" else host
        }.orEmpty()

private fun URI.redactedHost(): String =
    host
        ?: rawAuthority
            ?.substringAfterLast("@")
            ?.trim('[', ']')
            ?.substringBefore(":")
            .orEmpty()

private fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.trim().toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun Map<String, String>.required(key: String): String =
    this[key]?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Missing --$key")
