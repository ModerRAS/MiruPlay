package com.miruplay.tv

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.miruplay.tv.player.LibVlcStartupProbeContract
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer

class LibVlcStartupProbeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        return Bundle().apply {
            when (method) {
                LibVlcStartupProbeContract.METHOD_CAN_START_LIBVLC -> {
                    val canStart = runCatching {
                        val providerContext = requireNotNull(context) { "provider context missing" }
                        val options = extras
                            ?.getStringArrayList(LibVlcStartupProbeContract.EXTRA_LIBVLC_OPTIONS)
                            ?.toList()
                            .orEmpty()
                        LibVlcStartupProbeRuntime.run(providerContext, options)
                        true
                    }.getOrElse { error ->
                        putBoolean(LibVlcStartupProbeContract.EXTRA_CAN_START, false)
                        putString(
                            LibVlcStartupProbeContract.EXTRA_ERROR_MESSAGE,
                            error.message ?: error.javaClass.simpleName,
                        )
                        false
                    }
                    if (canStart) {
                        putBoolean(LibVlcStartupProbeContract.EXTRA_CAN_START, true)
                    }
                }
                else -> {
                    putBoolean(LibVlcStartupProbeContract.EXTRA_CAN_START, false)
                    putString(
                        LibVlcStartupProbeContract.EXTRA_ERROR_MESSAGE,
                        "Unsupported probe method: $method",
                    )
                }
            }
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

internal object LibVlcStartupProbeRuntime {
    @Volatile
    var testOverride: ((android.content.Context, List<String>) -> Unit)? = null

    fun run(context: android.content.Context, options: List<String>) {
        testOverride?.let { override ->
            override(context, options)
            return
        }
        var libVlc: LibVLC? = null
        var mediaPlayer: MediaPlayer? = null
        try {
            libVlc = LibVLC(context, options)
            mediaPlayer = MediaPlayer(libVlc)
        } finally {
            runCatching { mediaPlayer?.release() }
            runCatching { libVlc?.release() }
        }
    }
}
