package com.miruplay.tv.repository.desktop

import com.miruplay.tv.repository.AppCredentialStore
import kotlinx.coroutines.runBlocking

internal class FileBackedCredentialStore(
    private val store: DesktopRepositoryStore,
) : AppCredentialStore {
    override var cloudDriveToken: String?
        get() = storeBlocking { it.cloudDriveToken }
        set(value) {
            updateBlocking { state -> state.copy(cloudDriveToken = value) }
        }

    override var cloudDrivePassword: String?
        get() = storeBlocking { it.cloudDrivePassword }
        set(value) {
            updateBlocking { state -> state.copy(cloudDrivePassword = value) }
        }

    override var bangumiAccessToken: String?
        get() = storeBlocking { it.bangumiAccessToken }
        set(value) {
            updateBlocking { state -> state.copy(bangumiAccessToken = value) }
        }

    override var tmdbAccessToken: String?
        get() = storeBlocking { it.tmdbAccessToken }
        set(value) {
            updateBlocking { state -> state.copy(tmdbAccessToken = value) }
        }

    override var tmdbApiBaseUrlOverride: String?
        get() = storeBlocking { it.tmdbApiBaseUrlOverride }
        set(value) {
            updateBlocking { state -> state.copy(tmdbApiBaseUrlOverride = value) }
        }

    override var otlpAccessToken: String?
        get() = storeBlocking { it.otlpAccessToken }
        set(value) {
            updateBlocking { state -> state.copy(otlpAccessToken = value) }
        }

    override fun clearCloudDriveCredentials() {
        updateBlocking { state ->
            state.copy(
                cloudDriveToken = null,
                cloudDrivePassword = null,
            )
        }
    }

    override fun clearBangumiToken() {
        updateBlocking { state -> state.copy(bangumiAccessToken = null) }
    }

    override fun clearTmdbToken() {
        updateBlocking { state ->
            state.copy(
                tmdbAccessToken = null,
                tmdbApiBaseUrlOverride = null,
            )
        }
    }

    override fun clearOtlpAccessToken() {
        updateBlocking { state -> state.copy(otlpAccessToken = null) }
    }

    private fun <T> storeBlocking(block: (DesktopRepositoryState) -> T): T =
        runBlocking { store.read(block) }

    private fun updateBlocking(block: (DesktopRepositoryState) -> DesktopRepositoryState) {
        runBlocking { store.update { state -> block(state) to Unit } }
    }
}
