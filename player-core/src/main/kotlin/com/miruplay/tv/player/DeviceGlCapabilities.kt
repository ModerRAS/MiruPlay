package com.miruplay.tv.player

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ConfigurationInfo

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
