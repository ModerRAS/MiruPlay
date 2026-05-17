package com.miruplay.tv.repository.desktop

import java.nio.file.Path
import java.nio.file.Paths

object DesktopRepositoryPaths {
    fun defaultStorePath(): Path =
        Paths.get(System.getProperty("user.home"), ".miruplay", "desktop-store.json")
}
