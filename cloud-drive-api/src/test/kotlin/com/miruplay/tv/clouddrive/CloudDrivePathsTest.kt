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

    @Test
    fun `normalize scoped keeps blank paths empty`() {
        assertEquals("", CloudDrivePaths.normalizeScoped(""))
        assertEquals("", CloudDrivePaths.normalizeScoped("   "))
    }

    @Test
    fun `scope helpers treat root as unscoped and children as nested`() {
        assertEquals("/Anime", CloudDrivePaths.parentPath("/Anime/Episode 01.mkv"))
        assertEquals("/", CloudDrivePaths.parentPath("/"))
        assertEquals("/", CloudDrivePaths.parentPath(""))
        assertEquals(false, CloudDrivePaths.isScopedDirectory("/"))
        assertEquals(false, CloudDrivePaths.isScopedDirectory("   "))
        assertEquals(true, CloudDrivePaths.isScopedDirectory("/Anime"))
        assertEquals(true, CloudDrivePaths.isSameOrChild("/Anime/Season 1", "/Anime"))
        assertEquals(true, CloudDrivePaths.isChild("/Anime/Season 1", "/Anime"))
        assertEquals(false, CloudDrivePaths.isChild("/Anime", "/Anime"))
    }
}
