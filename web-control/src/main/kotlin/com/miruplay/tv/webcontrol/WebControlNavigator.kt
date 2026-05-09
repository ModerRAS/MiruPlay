package com.miruplay.tv.webcontrol

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebControlNavigator @Inject constructor() {
    private val _commands = MutableSharedFlow<NavigationCommand>(
        replay = 0,
        extraBufferCapacity = 16
    )
    val commands = _commands.asSharedFlow()

    fun openPlayer(source: WebPlaybackSource) {
        _commands.tryEmit(
            NavigationCommand(
                type = TYPE_OPEN_PLAYER,
                payload = Json.encodeToJsonElement(source)
            )
        )
    }

    companion object {
        const val TYPE_OPEN_PLAYER = "open_player"
    }
}
