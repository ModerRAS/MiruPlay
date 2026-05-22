package com.miruplay.tv.repository

import java.io.Closeable

interface WebControlAccessManager {
    var webControlEnabled: Boolean
    val accessToken: String
    fun rotateAccessToken(): String
    fun addEnabledChangeListener(onChanged: (Boolean) -> Unit): Closeable
}
