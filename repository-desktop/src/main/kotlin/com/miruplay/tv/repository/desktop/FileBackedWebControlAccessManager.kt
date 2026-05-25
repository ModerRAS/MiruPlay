package com.miruplay.tv.repository.desktop

import com.miruplay.tv.repository.WebControlAccessManager
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList

internal class FileBackedWebControlAccessManager(
    private val store: DesktopRepositoryStore,
) : WebControlAccessManager {
    private val enabledChangeListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    override var webControlEnabled: Boolean
        get() = storeBlocking { it.webControlEnabled }
        set(value) {
            val changed = updateBlocking { state ->
                val nextState = if (value) {
                    state.copy(
                        webControlEnabled = true,
                        webControlAccessToken = state.webControlAccessToken
                            ?.takeIf { it.isNotBlank() }
                            ?: generateDesktopWebControlAccessToken(),
                    )
                } else {
                    state.copy(webControlEnabled = false)
                }
                nextState to (state.webControlEnabled != nextState.webControlEnabled)
            }
            if (changed) {
                enabledChangeListeners.forEach { listener -> listener(value) }
            }
        }

    override val accessToken: String
        get() {
            val token = storeBlocking { it.webControlAccessToken }
                ?.takeIf { it.isNotBlank() }
            if (token != null) return token
            return rotateAccessToken()
        }

    override fun rotateAccessToken(): String {
        val token = generateDesktopWebControlAccessToken()
        updateBlocking { state -> state.copy(webControlAccessToken = token) to Unit }
        return token
    }

    override fun addEnabledChangeListener(onChanged: (Boolean) -> Unit): Closeable {
        enabledChangeListeners += onChanged
        return Closeable { enabledChangeListeners -= onChanged }
    }

    private fun <T> storeBlocking(block: (DesktopRepositoryState) -> T): T =
        runBlocking { store.read(block) }

    private fun <T> updateBlocking(block: (DesktopRepositoryState) -> Pair<DesktopRepositoryState, T>): T =
        runBlocking { store.update(block) }
}
