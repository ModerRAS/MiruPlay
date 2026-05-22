package com.miruplay.tv.repository.desktop

import java.nio.file.Path
import java.nio.file.Paths

object DesktopRepositoryPaths {
    fun defaultStorePath(): Path =
        System.getProperty(STORE_PATH_PROPERTY)
            ?.takeIf(String::isNotBlank)
            ?.let(Paths::get)
            ?: System.getenv(STORE_PATH_ENV)
                ?.takeIf(String::isNotBlank)
                ?.let(Paths::get)
            ?: Paths.get(System.getProperty("user.home"), ".miruplay", "desktop-store.json")

    const val STORE_PATH_PROPERTY = "miruplay.desktop.store"
    const val STORE_PATH_ENV = "MIRUPLAY_DESKTOP_STORE"
}
