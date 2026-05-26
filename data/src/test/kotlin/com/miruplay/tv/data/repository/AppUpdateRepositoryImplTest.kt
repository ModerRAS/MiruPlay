package com.miruplay.tv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryImplTest {
    @Test
    fun `parseLatestRelease picks release apk and nightly date version`() {
        val update = GitHubAppUpdateMapper.parseLatestRelease(
            """
            {
              "tag_name": "nightly-2026.05.26",
              "name": "Nightly 2026.05.26",
              "draft": false,
              "published_at": "2026-05-26T02:26:42Z",
              "html_url": "https://github.com/ModerRAS/MiruPlay/releases/tag/nightly-2026.05.26",
              "assets": [
                {
                  "name": "desktop-app-2026.05.26.zip",
                  "size": 67230778,
                  "browser_download_url": "https://example.test/desktop.zip"
                },
                {
                  "name": "app-release.apk",
                  "size": 59827723,
                  "browser_download_url": "https://example.test/app-release.apk"
                }
              ]
            }
            """.trimIndent()
        )

        assertNotNull(update)
        requireNotNull(update)
        assertEquals("2026.05.26", update.versionName)
        assertEquals(20260526L, update.versionCode)
        assertEquals("app-release.apk", update.assetName)
        assertEquals(59827723L, update.assetSizeBytes)
        assertEquals("https://example.test/app-release.apk", update.downloadUrl)
    }

    @Test
    fun `parseLatestRelease returns null when apk asset is missing`() {
        val update = GitHubAppUpdateMapper.parseLatestRelease(
            """
            {
              "tag_name": "nightly-2026.05.26",
              "name": "Nightly 2026.05.26",
              "draft": false,
              "assets": [
                {
                  "name": "desktop.zip",
                  "browser_download_url": "https://example.test/desktop.zip"
                }
              ]
            }
            """.trimIndent()
        )

        assertNull(update)
    }

    @Test
    fun `isNewerThanCurrent compares release version code when available`() {
        val latest = requireNotNull(
            GitHubAppUpdateMapper.parseLatestRelease(
                """
                {
                  "tag_name": "nightly-2026.05.26",
                  "name": "Nightly 2026.05.26",
                  "draft": false,
                  "assets": [
                    {
                      "name": "app-release.apk",
                      "size": 1,
                      "browser_download_url": "https://example.test/app-release.apk"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(
            GitHubAppUpdateMapper.isNewerThanCurrent(
                latest = latest,
                currentVersionName = "0.1.0",
                currentVersionCode = 1L,
            )
        )
        assertFalse(
            GitHubAppUpdateMapper.isNewerThanCurrent(
                latest = latest,
                currentVersionName = "2026.05.26",
                currentVersionCode = 20260526L,
            )
        )
    }
}
