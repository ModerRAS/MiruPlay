package com.miruplay.tv.mediasource

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.mediaSourceConnectionFailedMessage

sealed class MediaSourceConnectionTestResult {
    data object Success : MediaSourceConnectionTestResult()
    data class Failed(val message: String) : MediaSourceConnectionTestResult()
}

suspend fun MediaSourceFactory.testConnection(info: MediaSourceInfo): MediaSourceConnectionTestResult =
    when (val sourceResult = create(info)) {
        is Result.Error -> MediaSourceConnectionTestResult.Failed(sourceResult.error.toUserMessage())
        is Result.Success -> {
            try {
                sourceResult.data.testConnection().toConnectionTestResult()
            } finally {
                sourceResult.data.close()
            }
        }
    }

suspend fun MediaSourceFactory.testConnection(
    type: MediaSourceType,
    location: String,
    username: String = "",
    password: String = "",
    domain: String = "",
    displayName: String = "",
): MediaSourceConnectionTestResult =
    testConnection(
        MediaSourceInfo(
            name = "test",
            type = type,
            connectionInfo = MediaSourceInfoConventions.sourceConnectionInfo(
                type = type,
                location = location,
                displayName = displayName,
                username = username,
                password = password,
                domain = domain,
            ),
        )
    )

suspend fun MediaSourceFactory.testConnectionState(info: MediaSourceInfo): Boolean =
    testConnection(info) is MediaSourceConnectionTestResult.Success

private fun Result<Boolean>.toConnectionTestResult(): MediaSourceConnectionTestResult =
    when (this) {
        is Result.Success -> if (data) {
            MediaSourceConnectionTestResult.Success
        } else {
            MediaSourceConnectionTestResult.Failed(mediaSourceConnectionFailedMessage())
        }
        is Result.Error -> MediaSourceConnectionTestResult.Failed(error.toUserMessage())
    }
