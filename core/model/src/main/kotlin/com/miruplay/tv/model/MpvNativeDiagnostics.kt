package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class MpvNativePropertySample(
    val name: String,
    val value: String? = null,
)

@Serializable
data class MpvNativeLogMessage(
    val observedAtElapsedRealtimeMs: Long,
    val prefix: String,
    val level: Int,
    val text: String,
)

@Serializable
data class MpvNativeDiagnostics(
    val collectedAtElapsedRealtimeMs: Long,
    val surfaceAttached: Boolean,
    val pendingStartPositionMs: Long? = null,
    val properties: List<MpvNativePropertySample> = emptyList(),
    val recentLogMessages: List<MpvNativeLogMessage> = emptyList(),
)
