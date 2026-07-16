package com.miruplay.tv.webcontrol

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebControlNavigator @Inject constructor() {
    private val _commands = Channel<NavigationCommand>(capacity = 16)
    val commands = _commands.receiveAsFlow()

    fun openPlayer(source: WebPlaybackSource): Boolean =
        _commands.trySend(
            NavigationCommand(
                type = TYPE_OPEN_PLAYER,
                payload = Json.encodeToJsonElement(source)
            )
        ).isSuccess

    fun closePlayer(): Boolean =
        _commands.trySend(NavigationCommand(type = TYPE_CLOSE_PLAYER)).isSuccess

    fun requestAppRestart(): Boolean =
        _commands.trySend(NavigationCommand(type = TYPE_APP_RESTART)).isSuccess

    fun requestAppExit(): Boolean =
        _commands.trySend(NavigationCommand(type = TYPE_APP_EXIT)).isSuccess

    companion object {
        const val TYPE_OPEN_PLAYER = "open_player"
        const val TYPE_CLOSE_PLAYER = "close_player"
        const val TYPE_APP_RESTART = "app_restart"
        const val TYPE_APP_EXIT = "app_exit"
    }
}
