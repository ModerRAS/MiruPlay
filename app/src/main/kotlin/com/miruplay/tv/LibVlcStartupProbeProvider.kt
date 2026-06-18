package com.miruplay.tv

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.miruplay.tv.player.LibVlcStartupProbeContract
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCUtil

class LibVlcStartupProbeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        return Bundle().apply {
            when (method) {
                LibVlcStartupProbeContract.METHOD_CAN_START_LIBVLC -> {
                    val providerContext = requireNotNull(context) { "provider context missing" }
                    StartupProbe.writeCheckpoint(providerContext, "libvlc_probe_call_enter")
                    val canStart = runCatching {
                        val options = extras
                            ?.getStringArrayList(LibVlcStartupProbeContract.EXTRA_LIBVLC_OPTIONS)
                            ?.toList()
                            .orEmpty()
                        LibVlcStartupProbeRuntime.run(
                            context = providerContext,
                            options = options,
                            checkpointWriter = { stage ->
                                StartupProbe.writeCheckpoint(providerContext, "libvlc_probe_$stage")
                            },
                        )
                        StartupProbe.writeCheckpoint(providerContext, "libvlc_probe_call_success")
                        true
                    }.getOrElse { error ->
                        StartupProbe.writeFatal(
                            context = providerContext,
                            checkpoint = "libvlc_probe_call_failed",
                            throwable = error,
                        )
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

    fun run(
        context: android.content.Context,
        options: List<String>,
        checkpointWriter: ((String) -> Unit)? = null,
        libVlcFactory: (android.content.Context, List<String>) -> LibVLC = { factoryContext, factoryOptions ->
            LibVLC(factoryContext, factoryOptions)
        },
        mediaPlayerFactory: (LibVLC) -> MediaPlayer = { factoryLibVlc ->
            MediaPlayer(factoryLibVlc)
        },
    ) {
        checkpointWriter?.invoke("call_enter")
        testOverride?.let { override ->
            checkpointWriter?.invoke("test_override_enter")
            override(context, options)
            checkpointWriter?.invoke("test_override_exit")
            return
        }
        var libVlc: LibVLC? = null
        var mediaPlayer: MediaPlayer? = null
        try {
            checkpointWriter?.invoke("before_libvlc_ctor")
            check(VLCUtil.hasCompatibleCPU(context)) {
                VLCUtil.getErrorMsg().orEmpty().ifBlank { "libVLC CPU/ABI incompatible" }
            }
            checkpointWriter?.invoke("after_compat_cpu_check")
            libVlc = libVlcFactory(context, options)
            checkpointWriter?.invoke("after_libvlc_ctor")
            checkpointWriter?.invoke("before_media_player_ctor")
            mediaPlayer = mediaPlayerFactory(libVlc)
            checkpointWriter?.invoke("after_media_player_ctor")
            checkpointWriter?.invoke("call_exit_success")
        } finally {
            runCatching { mediaPlayer?.release() }
            runCatching { libVlc?.release() }
        }
    }
}
