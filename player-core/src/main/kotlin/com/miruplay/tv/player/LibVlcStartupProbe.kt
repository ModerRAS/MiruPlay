package com.miruplay.tv.player

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle

interface LibVlcStartupProbe {
    fun canStartLibVlc(options: List<String> = emptyList()): LibVlcStartupProbeResult
}

object LibVlcStartupProbeContract {
    const val AUTHORITY_SUFFIX = ".libvlc_probe"
    const val METHOD_CAN_START_LIBVLC = "canStartLibVlc"
    const val EXTRA_CAN_START = "can_start"
    const val EXTRA_ERROR_MESSAGE = "error_message"
    const val EXTRA_LIBVLC_OPTIONS = "libvlc_options"
}

data class LibVlcStartupProbeResult(
    val canStart: Boolean,
    val errorMessage: String? = null,
)

class ContentResolverLibVlcStartupProbe(
    private val contentResolver: ContentResolver,
    private val authority: String,
) : LibVlcStartupProbe {
    override fun canStartLibVlc(options: List<String>): LibVlcStartupProbeResult {
        val result = runCatching {
            contentResolver.call(
                Uri.parse("content://$authority"),
                LibVlcStartupProbeContract.METHOD_CAN_START_LIBVLC,
                /* arg = */ null,
                /* extras = */ Bundle().apply {
                    putStringArrayList(
                        LibVlcStartupProbeContract.EXTRA_LIBVLC_OPTIONS,
                        ArrayList(options),
                    )
                },
            )
        }.getOrElse { error ->
            return LibVlcStartupProbeResult(
                canStart = false,
                errorMessage = error.message ?: error.javaClass.simpleName,
            )
        }
        if (result == null) {
            return LibVlcStartupProbeResult(
                canStart = false,
                errorMessage = "libVLC startup probe returned no result",
            )
        }
        return LibVlcStartupProbeResult(
            canStart = result.getBoolean(LibVlcStartupProbeContract.EXTRA_CAN_START, false),
            errorMessage = result.getString(LibVlcStartupProbeContract.EXTRA_ERROR_MESSAGE),
        )
    }
}
