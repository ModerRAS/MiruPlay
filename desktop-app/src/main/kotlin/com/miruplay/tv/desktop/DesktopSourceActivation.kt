package com.miruplay.tv.desktop

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.connectionDomain
import com.miruplay.tv.model.connectionPassword
import com.miruplay.tv.model.connectionUsername
import com.miruplay.tv.model.localRootPath
import com.miruplay.tv.model.remoteUrl
import com.miruplay.tv.repository.loadedStatus

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

internal data class DesktopSourceActivationState(
    val sourceInfo: MediaSourceInfo,
    val formState: DesktopSourceFormState,
    val libraryStatus: String? = null,
    val remoteStatus: String? = null,
    val clearsRemoteBrowser: Boolean = false,
    val loadsRemoteRoot: Boolean = false,
    val indexedEmptyStatus: String,
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
