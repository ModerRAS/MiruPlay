package com.miruplay.tv.clouddrive

import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking

data class CloudDriveLiveSmokeOptions(
    val endpoint: String,
    val token: String,
    val path: String = "/",
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
    )
}

fun main(args: Array<String>) = runBlocking {
    val options = parseCloudDriveLiveSmokeOptions(args)
    val client = GrpcCloudDriveClient()

    when (val tokenInfo = client.getApiTokenInfo(options.endpoint, options.token)) {
        is Result.Success -> {
            val info = tokenInfo.data
            println("CloudDrive2 token verified.")
            println("Friendly name: ${info.friendlyName.ifBlank { "(none)" }}")
            println("Root dir: ${info.rootDir.ifBlank { "/" }}")
            println(
                "Permissions: list=${info.allowList}, createFolder=${info.allowCreateFolder}, " +
                    "createFile=${info.allowCreateFile}, write=${info.allowWrite}, move=${info.allowMove}, " +
                    "offline=${info.allowAddOfflineDownload}"
            )
        }
        is Result.Error -> error("Token verification failed: ${tokenInfo.error.toUserMessage()}")
    }

    val endpoint = CloudDriveEndpoint(options.endpoint, options.token)
    when (val listing = client.listFolder(endpoint, options.path, forceRefresh = false)) {
        is Result.Success -> {
            println("List succeeded for ${options.path}: ${listing.data.size} item(s).")
            listing.data.take(10).forEach { file ->
                val kind = if (file.isDirectory) "DIR" else "FILE"
                println(" - [$kind] ${file.path} (${file.size} bytes)")
            }
        }
        is Result.Error -> error("Folder list failed: ${listing.error.toUserMessage()}")
    }
}
