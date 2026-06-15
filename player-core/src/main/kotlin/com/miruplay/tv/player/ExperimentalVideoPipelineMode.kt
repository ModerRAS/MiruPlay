package com.miruplay.tv.player

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ConfigurationInfo
import com.miruplay.tv.model.PlaybackRenderBackend

enum class ExperimentalVideoPipelineMode {
    MEDIA3_EFFECTS,
    DEDICATED_GL_SURFACE,
}

fun resolveGlEsMajorVersion(reqGlEsVersion: Int): Int =
    when {
        reqGlEsVersion == ConfigurationInfo.GL_ES_VERSION_UNDEFINED -> 2
        reqGlEsVersion <= 0 -> 2
        else -> reqGlEsVersion shr 16
    }

fun resolveDeviceGlEsMajorVersion(context: Context): Int {
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val reqGlEsVersion = activityManager?.deviceConfigurationInfo?.reqGlEsVersion
        ?: ConfigurationInfo.GL_ES_VERSION_UNDEFINED
    return resolveGlEsMajorVersion(reqGlEsVersion)
}

fun resolveExperimentalVideoPipelineMode(glEsMajorVersion: Int): ExperimentalVideoPipelineMode =
    if (glEsMajorVersion >= 3) {
        ExperimentalVideoPipelineMode.MEDIA3_EFFECTS
    } else {
        ExperimentalVideoPipelineMode.DEDICATED_GL_SURFACE
    }

fun shouldUseDedicatedExperimentalGlSurface(glEsMajorVersion: Int): Boolean =
    resolveExperimentalVideoPipelineMode(glEsMajorVersion) ==
        ExperimentalVideoPipelineMode.DEDICATED_GL_SURFACE

fun shouldUseExperimentalVideoEffectsPipeline(
    activeBackend: PlaybackRenderBackend,
    glEsMajorVersion: Int,
): Boolean =
    activeBackend == PlaybackRenderBackend.EXPERIMENTAL_GL &&
        !shouldUseDedicatedExperimentalGlSurface(glEsMajorVersion)

fun shouldBypassExoVideoEffectsDispatch(
    activeBackend: PlaybackRenderBackend,
    glEsMajorVersion: Int,
): Boolean =
    activeBackend == PlaybackRenderBackend.EXPERIMENTAL_GL &&
        shouldUseDedicatedExperimentalGlSurface(glEsMajorVersion)
