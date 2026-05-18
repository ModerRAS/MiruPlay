package com.miruplay.tv.clouddrive

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudDrivePathsTest {
    @Test
    fun `normalize returns root for blank paths`() {
        assertEquals("/", CloudDrivePaths.normalize(""))
        assertEquals("/", CloudDrivePaths.normalize("   "))
    }

    @Test
    fun `normalize keeps a single leading slash and removes trailing slashes`() {
        assertEquals("/Anime", CloudDrivePaths.normalize("Anime/"))
        assertEquals("/Anime/Season 1", CloudDrivePaths.normalize("/Anime/Season 1///"))
    }

    @Test
    fun `normalize converts windows separators`() {
        assertEquals("/Anime/Season 1", CloudDrivePaths.normalize("\\Anime\\Season 1\\"))
    }

    @Test
    fun `join appends a file below normalized parent`() {
        assertEquals("/Anime/Episode 01.mkv", CloudDrivePaths.join("Anime/", "Episode 01.mkv"))
        assertEquals("/Episode 01.mkv", CloudDrivePaths.join("/", "Episode 01.mkv"))
    }
}
