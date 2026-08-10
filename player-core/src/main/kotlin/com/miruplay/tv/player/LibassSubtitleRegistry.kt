package com.miruplay.tv.player

import androidx.media3.common.Player
import java.util.WeakHashMap

internal object LibassSubtitleRegistry {
    private val sessions = WeakHashMap<Player, LibassSubtitleSession>()

    @Synchronized
    fun register(player: Player, session: LibassSubtitleSession) {
        sessions.put(player, session)?.takeUnless { it === session }?.close()
    }

    @Synchronized
    fun sessionFor(player: Player?): LibassSubtitleSession? = sessions[player]

    @Synchronized
    fun release(player: Player): LibassSubtitleSession? = sessions.remove(player)
}
