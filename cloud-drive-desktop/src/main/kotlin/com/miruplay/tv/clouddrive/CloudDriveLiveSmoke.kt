package com.miruplay.tv.clouddrive

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.redactSensitiveUrl
import com.miruplay.tv.core.common.sensitiveUrlEvidence
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.time.Instant

data class CloudDriveLiveSmokeOptions(
    val endpoint: String,
    val token: String,
    val path: String = "/",
    val reportPath: String? = null,
    val maxPreviewItems: Int = 10,
)

data class CloudDriveLiveSmokeReport(
    val endpoint: String,
    val path: String,
    val friendlyName: String,
    val rootDir: String,
    val permissions: CloudDriveLiveSmokePermissions,
    val itemCount: Int,
    val directoryCount: Int,
    val fileCount: Int,
    val previewItems: List<CloudDriveLiveSmokeItem>,
)

data class CloudDriveLiveSmokePermissions(
    val allowList: Boolean,
    val allowCreateFolder: Boolean,
    val allowCreateFile: Boolean,
    val allowWrite: Boolean,
    val allowMove: Boolean,
    val allowAddOfflineDownload: Boolean,
)

data class CloudDriveLiveSmokeItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
)

fun parseCloudDriveLiveSmokeOptions(args: Array<String>): CloudDriveLiveSmokeOptions {
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
    val endpoint = values["endpoint"]?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Missing --endpoint")
    val token = values["token"]?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Missing --token")
    return CloudDriveLiveSmokeOptions(
        endpoint = endpoint,
        token = token,
        path = values["path"]?.takeIf { it.isNotBlank() } ?: "/",
        reportPath = values["report-path"]?.takeIf { it.isNotBlank() },
        maxPreviewItems = values["max-preview"]
            ?.toIntOrNull()
            ?.coerceIn(1, 100)
            ?: 10,
    )
}

fun main(args: Array<String>) {
    runBlocking {
        val options = parseCloudDriveLiveSmokeOptions(args)
        when (val result = runCloudDriveLiveSmoke(options)) {
            is Result.Success -> {
                printCloudDriveLiveSmokeReport(result.data)
                options.reportPath?.let { reportPath ->
                    writeCloudDriveLiveSmokeReport(reportPath, result.data)
                }
            }
            is Result.Error -> error("CloudDrive2 live smoke failed: ${result.error.toUserMessage()}")
        }
    }
}

suspend fun runCloudDriveLiveSmoke(
    options: CloudDriveLiveSmokeOptions,
    client: CloudDriveClient = GrpcCloudDriveClient(),
): Result<CloudDriveLiveSmokeReport> {
    val tokenInfo = when (val result = client.getApiTokenInfo(options.endpoint, options.token)) {
        is Result.Success -> result.data
        is Result.Error -> return result
    }
    val files = when (val result = client.listFolder(
        endpoint = CloudDriveEndpoint(options.endpoint, options.token),
        path = options.path,
        forceRefresh = false,
    )) {
        is Result.Success -> result.data
        is Result.Error -> return result
    }
    return Result.success(
        CloudDriveLiveSmokeReport(
            endpoint = options.endpoint,
            path = options.path,
            friendlyName = tokenInfo.friendlyName,
            rootDir = tokenInfo.rootDir,
            permissions = tokenInfo.toSmokePermissions(),
            itemCount = files.size,
            directoryCount = files.count { it.isDirectory },
            fileCount = files.count { !it.isDirectory },
            previewItems = files.take(options.maxPreviewItems).map { it.toSmokeItem() },
        )
    )
}

private fun printCloudDriveLiveSmokeReport(report: CloudDriveLiveSmokeReport) {
    val endpointEvidence = sensitiveUrlEvidence(report.endpoint)
    println("CloudDrive2 live smoke passed.")
    println("Endpoint: ${endpointEvidence.redacted} sha256=${endpointEvidence.sha256}")
    println("Friendly name: ${report.friendlyName.ifBlank { "(none)" }}")
    println("Root dir: ${report.rootDir.ifBlank { "/" }}")
    println(
        "Permissions: list=${report.permissions.allowList}, createFolder=${report.permissions.allowCreateFolder}, " +
            "createFile=${report.permissions.allowCreateFile}, write=${report.permissions.allowWrite}, " +
            "move=${report.permissions.allowMove}, offline=${report.permissions.allowAddOfflineDownload}"
    )
    println(
        "List succeeded for ${report.path}: ${report.itemCount} item(s), " +
            "${report.directoryCount} dir(s), ${report.fileCount} file(s)."
    )
    report.previewItems.forEach { file ->
        val kind = if (file.isDirectory) "DIR" else "FILE"
        println(" - [$kind] ${file.path} (${file.size} bytes)")
    }
}

private fun writeCloudDriveLiveSmokeReport(
    reportPath: String,
    report: CloudDriveLiveSmokeReport,
) {
    val outputFile = File(reportPath).absoluteFile
    outputFile.parentFile?.mkdirs()
    outputFile.writeText(buildCloudDriveLiveSmokeReportJson(report), Charsets.UTF_8)
    println("Wrote CloudDrive2 live smoke report: ${outputFile.absolutePath}")
}

internal fun buildCloudDriveLiveSmokeReportJson(report: CloudDriveLiveSmokeReport): String {
    val payload = buildJsonObject {
        put("generatedAtUtc", Instant.now().toString())
        put("endpoint", redactCloudDriveLiveEvidenceUrl(report.endpoint))
        putJsonObject("endpointEvidence") {
            putUrlEvidenceFields(report.endpoint)
        }
        put("path", report.path)
        put("itemCount", report.itemCount)
        put("directoryCount", report.directoryCount)
        put("fileCount", report.fileCount)
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
                        put("name", item.name)
                        put("path", item.path)
                        put("isDirectory", item.isDirectory)
                        put("size", item.size)
                    }
                )
            }
        }
    }
    return payload.toString()
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putUrlEvidenceFields(value: String) {
    val evidence = sensitiveUrlEvidence(value)
    put("redacted", evidence.redacted)
    put("scheme", evidence.scheme)
    put("host", evidence.host)
    put("sha256", evidence.sha256)
}

private fun redactCloudDriveLiveEvidenceUrl(value: String): String =
    redactSensitiveUrl(value)

private fun CloudDriveTokenInfo.toSmokePermissions(): CloudDriveLiveSmokePermissions =
    CloudDriveLiveSmokePermissions(
        allowList = allowList,
        allowCreateFolder = allowCreateFolder,
        allowCreateFile = allowCreateFile,
        allowWrite = allowWrite,
        allowMove = allowMove,
        allowAddOfflineDownload = allowAddOfflineDownload,
    )

private fun CloudDriveFileInfo.toSmokeItem(): CloudDriveLiveSmokeItem =
    CloudDriveLiveSmokeItem(
        name = name,
        path = path,
        isDirectory = isDirectory,
        size = size,
    )
