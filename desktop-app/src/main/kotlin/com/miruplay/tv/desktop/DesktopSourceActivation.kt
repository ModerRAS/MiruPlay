package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.desktopSourceFromInfo
import com.miruplay.tv.mediasource.testConnectionState
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.connectionDomain
import com.miruplay.tv.model.connectionPassword
import com.miruplay.tv.model.connectionUsername
import com.miruplay.tv.model.localRootPath
import com.miruplay.tv.model.remoteUrl
import com.miruplay.tv.repository.MediaSourceActionCoordinator
import com.miruplay.tv.repository.MediaSourceAddActionResult
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.loadedStatus
import com.miruplay.tv.repository.readyStatus

internal data class DesktopSourceFormState(
    val libraryRoot: String = "",
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val smbUrl: String = "",
    val smbDomain: String = "",
    val smbUsername: String = "",
    val smbPassword: String = "",
)

internal fun desktopSourceFormStateFromInitialValues(
    libraryRoot: String? = null,
    webDavUrl: String? = null,
    webDavUsername: String? = null,
    webDavPassword: String? = null,
    smbUrl: String? = null,
    smbDomain: String? = null,
    smbUsername: String? = null,
    smbPassword: String? = null,
): DesktopSourceFormState =
    DesktopSourceFormState(
        libraryRoot = libraryRoot?.trim().orEmpty(),
        webDavUrl = webDavUrl?.trim().orEmpty(),
        webDavUsername = webDavUsername?.trim().orEmpty(),
        webDavPassword = webDavPassword.orEmpty(),
        smbUrl = smbUrl?.trim().orEmpty(),
        smbDomain = smbDomain?.trim().orEmpty(),
        smbUsername = smbUsername?.trim().orEmpty(),
        smbPassword = smbPassword.orEmpty(),
    )

internal fun DesktopSourceFormState.hasAnyValue(): Boolean =
    libraryRoot.isNotBlank() ||
        webDavUrl.isNotBlank() ||
        webDavUsername.isNotBlank() ||
        webDavPassword.isNotBlank() ||
        smbUrl.isNotBlank() ||
        smbDomain.isNotBlank() ||
        smbUsername.isNotBlank() ||
        smbPassword.isNotBlank()

internal data class DesktopSourceActivationState(
    val sourceInfo: MediaSourceInfo,
    val formState: DesktopSourceFormState,
    val libraryStatus: String? = null,
    val remoteStatus: String? = null,
    val clearsRemoteBrowser: Boolean = false,
    val loadsRemoteRoot: Boolean = false,
    val indexedEmptyStatus: String,
)

internal data class DesktopSourceOpenResult(
    val sourceInfo: MediaSourceInfo,
    val source: DesktopMediaSource,
    val formState: DesktopSourceFormState,
    val status: String,
    val opensRemoteRoot: Boolean,
)

internal fun List<MediaSourceInfo>.desktopSourceFormState(): DesktopSourceFormState =
    DesktopSourceFormState()
        .withFirstSourceOfType(this, MediaSourceType.LOCAL)
        .withFirstSourceOfType(this, MediaSourceType.WEBDAV)
        .withFirstSourceOfType(this, MediaSourceType.SMB)

internal fun List<MediaSourceInfo>.preferredDesktopStartupSource(): MediaSourceInfo? =
    firstOrNull { it.type == MediaSourceType.LOCAL }
        ?: firstOrNull { it.type == MediaSourceType.WEBDAV }
        ?: firstOrNull { it.type == MediaSourceType.SMB }

internal fun MediaSourceInfo.desktopSourceActivationState(saved: Boolean = false): DesktopSourceActivationState {
    val loaded = loadedStatus(saved)
    return when (type) {
        MediaSourceType.LOCAL -> DesktopSourceActivationState(
            sourceInfo = this,
            formState = DesktopSourceFormState().withSource(this),
            libraryStatus = loaded,
            clearsRemoteBrowser = true,
            indexedEmptyStatus = loaded,
        )
        MediaSourceType.WEBDAV,
        MediaSourceType.SMB -> DesktopSourceActivationState(
            sourceInfo = this,
            formState = DesktopSourceFormState().withSource(this),
            remoteStatus = loaded,
            loadsRemoteRoot = true,
            indexedEmptyStatus = loaded,
        )
    }
}

internal suspend fun openDesktopSource(
    repository: MediaSourceRepository,
    mediaSourceFactory: MediaSourceFactory,
    sourceInfo: MediaSourceInfo,
    testConnection: suspend (MediaSourceInfo) -> Result<Boolean> = { persisted ->
        Result.success(mediaSourceFactory.testConnectionState(persisted))
    },
): Result<DesktopSourceOpenResult> =
    when (
        val result = MediaSourceActionCoordinator(repository).addSource(
            source = sourceInfo.copy(isConnected = false),
            testConnection = testConnection,
        )
    ) {
        is MediaSourceAddActionResult.Saved -> {
            val stored = result.source
            Result.success(
                DesktopSourceOpenResult(
                    sourceInfo = stored,
                    source = mediaSourceFactory.create(stored).getOrNull() ?: desktopSourceFromInfo(stored),
                    formState = DesktopSourceFormState().withSource(stored),
                    status = stored.readyStatus(),
                    opensRemoteRoot = stored.type != MediaSourceType.LOCAL,
                )
            )
        }
        is MediaSourceAddActionResult.Failed -> Result.failure(result.error)
    }

private fun DesktopSourceFormState.withSource(sourceInfo: MediaSourceInfo): DesktopSourceFormState =
    when (sourceInfo.type) {
        MediaSourceType.LOCAL -> copy(
            libraryRoot = sourceInfo.localRootPath().orEmpty(),
        )
        MediaSourceType.WEBDAV -> copy(
            webDavUrl = sourceInfo.remoteUrl().orEmpty(),
            webDavUsername = sourceInfo.connectionUsername(),
            webDavPassword = sourceInfo.connectionPassword(),
        )
        MediaSourceType.SMB -> copy(
            smbUrl = sourceInfo.remoteUrl().orEmpty(),
            smbDomain = sourceInfo.connectionDomain(),
            smbUsername = sourceInfo.connectionUsername(),
            smbPassword = sourceInfo.connectionPassword(),
        )
    }

private fun DesktopSourceFormState.withFirstSourceOfType(
    sources: List<MediaSourceInfo>,
    type: MediaSourceType,
): DesktopSourceFormState =
    sources.firstOrNull { it.type == type }?.let(::withSource) ?: this
