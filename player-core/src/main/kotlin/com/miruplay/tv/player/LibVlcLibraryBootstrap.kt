package com.miruplay.tv.player

import android.content.Context
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.util.VLCUtil

object LibVlcLibraryBootstrap {
    fun ensureCompatibleCpu(
        context: Context,
        checkpointWriter: ((String) -> Unit)? = null,
        compatibleCpuCheck: (Context) -> Boolean = VLCUtil::hasCompatibleCPU,
        compatibilityErrorProvider: () -> String? = VLCUtil::getErrorMsg,
    ) {
        check(compatibleCpuCheck(context)) {
            compatibilityErrorProvider().orEmpty().ifBlank { "libVLC CPU/ABI incompatible" }
        }
        checkpointWriter?.invoke("after_compat_cpu_check")
    }

    fun ensureLibrariesLoaded(
        checkpointWriter: ((String) -> Unit)? = null,
        loadLibrary: (String) -> Unit = System::loadLibrary,
        markLoaded: (Boolean) -> Unit = ::setLibVlcLoadedFlag,
    ) {
        checkpointWriter?.invoke("before_load_library_cxx_shared")
        loadLibrary("c++_shared")
        checkpointWriter?.invoke("after_load_library_cxx_shared")
        checkpointWriter?.invoke("before_load_library_vlc")
        loadLibrary("vlc")
        checkpointWriter?.invoke("after_load_library_vlc")
        checkpointWriter?.invoke("before_load_library_vlcjni")
        loadLibrary("vlcjni")
        checkpointWriter?.invoke("after_load_library_vlcjni")
        checkpointWriter?.invoke("before_mark_libvlc_loaded")
        markLoaded(true)
        checkpointWriter?.invoke("after_mark_libvlc_loaded")
    }

    private fun setLibVlcLoadedFlag(value: Boolean) {
        val field = LibVLC::class.java.getDeclaredField("sLoaded")
        field.isAccessible = true
        field.setBoolean(null, value)
    }
}
