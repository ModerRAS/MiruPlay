package com.miruplay.tv.player

/**
 * Factory for creating PlaybackController instances
 */
interface PlayerFactory {
    fun create(config: PlaybackConfig): PlaybackController
}