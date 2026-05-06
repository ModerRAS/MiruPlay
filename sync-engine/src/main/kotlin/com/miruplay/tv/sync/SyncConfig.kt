package com.miruplay.tv.sync

/**
 * Configuration for sync behavior
 */
data class SyncConfig(
    val autoSyncInterval: Long = 5 * 60 * 1000L,  // 5 minutes
    val nfoWriteDelay: Long = 2000,                 // 2 seconds debounce
    val conflictResolution: ConflictResolution = ConflictResolution.TIMESTAMP_WINS
)