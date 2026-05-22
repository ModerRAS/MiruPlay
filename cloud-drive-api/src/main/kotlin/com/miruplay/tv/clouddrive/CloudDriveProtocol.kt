package com.miruplay.tv.clouddrive

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import java.net.URI

data class CloudDriveEndpointAddress(
    val host: String,
    val port: Int,
    val useTransportSecurity: Boolean,
)

object CloudDriveProtocol {
    private const val SERVICE_NAME = "CloudDrive2"

    fun parseEndpointAddress(endpointUrl: String): CloudDriveEndpointAddress {
        val uri = URI(endpointUrl)
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") { "CloudDrive endpoint must be http(s)" }
        val host = uri.host ?: error("CloudDrive endpoint host is missing")
        val port = when {
            uri.port > 0 -> uri.port
            scheme == "https" -> 443
            else -> 80
        }
        return CloudDriveEndpointAddress(
            host = host,
            port = port,
            useTransportSecurity = scheme == "https",
        )
    }

    fun requireToken(endpoint: CloudDriveEndpoint): Result<String> =
        endpoint.token
            ?.takeIf { it.isNotBlank() }
            ?.let { Result.success(it) }
            ?: Result.failure(AppError.MediaSourceError.AuthenticationFailed(SERVICE_NAME))

    fun authorizationCandidates(token: String): List<String> =
        listOf("Bearer $token", token)

    fun shouldRetryWithRawToken(result: Result<*>): Boolean {
        val error = (result as? Result.Error)?.error ?: return false
        val detail = when (error) {
            is AppError.NetworkError.ServerUnreachable -> error.url
            is AppError.MediaSourceError.AuthenticationFailed -> error.source
            else -> error.toString()
        }
        return detail.contains("UNAUTHENTICATED", ignoreCase = true) ||
            detail.contains("Invalid auth token", ignoreCase = true)
    }

    fun operationResult(success: Boolean, errorMessage: String, fallback: String): Result<Unit> =
        if (success) {
            Result.success(Unit)
        } else {
            Result.failure(AppError.SyncError.WriteFailed(SERVICE_NAME, errorMessage.ifBlank { fallback }))
        }
}
