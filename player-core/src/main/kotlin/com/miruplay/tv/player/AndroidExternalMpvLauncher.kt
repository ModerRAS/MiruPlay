package com.miruplay.tv.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.model.PlaybackSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidExternalMpvLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun launch(source: PlaybackSource): Result<Unit> =
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setClassName(MPV_ANDROID_PACKAGE, MPV_ANDROID_ACTIVITY)
                data = source.uri.toPlaybackIntentUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("position", source.startPosition.toInt())
                putExtra("title", source.mediaSourceId)
            }
            context.startActivity(intent)
            MiruLog.i(
                TAG,
                "Launched external mpv-android playback",
                mapOf(
                    "uri" to source.uri,
                    "media_source_id" to source.mediaSourceId,
                    "start_position_ms" to source.startPosition.toString(),
                ),
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { error ->
                val wrapped = when (error) {
                    is ActivityNotFoundException -> IllegalStateException(
                        "mpv-android is not installed. Install package $MPV_ANDROID_PACKAGE first.",
                        error,
                    )
                    else -> error
                }
                MiruLog.e(
                    TAG,
                    "Failed to launch external mpv-android playback",
                    wrapped,
                    mapOf(
                        "uri" to source.uri,
                        "media_source_id" to source.mediaSourceId,
                    ),
                )
                Result.Error(com.miruplay.tv.core.common.AppError.PlaybackError.StreamError(wrapped.message ?: "Failed to launch mpv-android"))
            },
        )

    private fun String.toPlaybackIntentUri(): Uri =
        if (startsWith("/")) {
            Uri.fromFile(File(this))
        } else {
            Uri.parse(this)
        }

    companion object {
        private const val TAG = "AndroidExternalMpv"
        const val MPV_ANDROID_PACKAGE: String = "is.xyz.mpv"
        const val MPV_ANDROID_ACTIVITY: String = "is.xyz.mpv.MPVActivity"
    }
}
