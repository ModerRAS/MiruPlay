package com.miruplay.tv.mediasource

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultMediaSourceFactory @Inject constructor() : MediaSourceFactory {
    override fun create(info: MediaSourceInfo): Result<MediaSource> {
        return try {
            val source = when (info.type) {
                MediaSourceType.LOCAL -> LocalMediaSource(info)
                MediaSourceType.WEBDAV -> WebDavMediaSource(info)
                MediaSourceType.SMB -> SmbMediaSource(info)
            }
            Result.success(source)
        } catch (e: Exception) {
            Result.failure(AppError.MediaSourceError.ConnectionLost(info.name))
        }
    }

    override fun supports(type: MediaSourceType): Boolean = true
}
