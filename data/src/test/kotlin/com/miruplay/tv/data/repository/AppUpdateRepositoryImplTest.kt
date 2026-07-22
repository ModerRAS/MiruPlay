package com.miruplay.tv.data.repository

import androidx.test.core.app.ApplicationProvider
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.AppUpdateInfo
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppUpdateRepositoryImplTest {
    @Test
    fun `parseLatestRelease picks release apk and explicit version code`() {
        val update = GitHubAppUpdateMapper.parseLatestRelease(
            """
            {
              "tag_name": "v2.1.604",
              "name": "v2.1.604",
              "draft": false,
              "published_at": "2026-07-22T13:20:00Z",
              "html_url": "https://github.com/ModerRAS/MiruPlay/releases/tag/v2.1.604",
              "version_code": 604,
              "assets": [
                {
                  "name": "miruplay-source.zip",
                  "size": 67230778,
                  "browser_download_url": "https://example.test/source.zip"
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
        assertEquals("2.1.604", update.versionName)
        assertEquals(604L, update.versionCode)
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
                  "name": "miruplay-source.zip",
                  "browser_download_url": "https://example.test/source.zip"
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
                  "tag_name": "v2.1.604",
                  "name": "v2.1.604",
                  "draft": false,
                  "version_code": 604,
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
                currentVersionName = "2.1.603",
                currentVersionCode = 603L,
            )
        )
        assertFalse(
            GitHubAppUpdateMapper.isNewerThanCurrent(
                latest = latest,
                currentVersionName = "2.1.604",
                currentVersionCode = 604L,
            )
        )
    }

    @Test
    fun `checkLatestUpdate uses configured proxy for release manifest request`() = runBlocking {
        MockWebServer().use { proxy ->
            proxy.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "tag_name": "v2.1.604",
                      "name": "v2.1.604",
                      "draft": false,
                      "version_code": 604,
                      "assets": [
                        {
                          "name": "app-release.apk",
                          "size": 1,
                          "browser_download_url": "http://github.example/app-release.apk"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val repository = appUpdateRepository(
                updateManifestUrl = "http://github.example/ModerRAS/MiruPlay/releases/latest/download/latest.json",
                proxy = proxy,
            )

            val result = repository.checkLatestUpdate()

            assertTrue(result is Result.Success)
            val request = proxy.takeRequest()
            assertEquals("github.example", request.headers["Host"])
            assertEquals("application/json", request.headers["Accept"])
            assertEquals("no-cache", request.headers["Cache-Control"])
            assertNull(request.headers["X-GitHub-Api-Version"])
            assertEquals(1, proxy.requestCount)
        }
    }

    @Test
    fun `checkLatestUpdate falls back to GitHub API when manifest request fails`() = runBlocking {
        MockWebServer().use { proxy ->
            proxy.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setBody("release manifest blocked by proxy")
            )
            proxy.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "tag_name": "v2.1.604",
                      "name": "v2.1.604",
                      "draft": false,
                      "assets": [
                        {
                          "name": "app-release.apk",
                          "size": 1,
                          "browser_download_url": "http://github.example/app-release.apk"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val repository = appUpdateRepository(
                updateManifestUrl = "http://github.example/ModerRAS/MiruPlay/releases/latest/download/latest.json",
                latestReleaseApiUrl = "http://api.github.example/repos/ModerRAS/MiruPlay/releases/latest",
                proxy = proxy,
            )

            val result = repository.checkLatestUpdate()

            assertTrue(result is Result.Success)
            assertEquals("github.example", proxy.takeRequest().headers["Host"])
            val fallbackRequest = proxy.takeRequest()
            assertEquals("api.github.example", fallbackRequest.headers["Host"])
            assertEquals("application/vnd.github+json", fallbackRequest.headers["Accept"])
            assertEquals("2022-11-28", fallbackRequest.headers["X-GitHub-Api-Version"])
        }
    }

    @Test
    fun `checkLatestUpdate returns API error when both update sources fail`() = runBlocking {
        MockWebServer().use { proxy ->
            proxy.enqueue(MockResponse().setResponseCode(404).setBody("manifest missing"))
            proxy.enqueue(MockResponse().setResponseCode(403).setBody("API rate limit exceeded"))
            val repository = appUpdateRepository(proxy = proxy)

            val result = repository.checkLatestUpdate()

            assertTrue(result is Result.Error)
            val error = (result as Result.Error).error as AppError.NetworkError.HttpError
            assertEquals(403, error.code)
            assertTrue(error.message.contains("API rate limit exceeded"))
        }
    }

    @Test
    fun `downloadAndLaunchInstaller uses configured proxy for APK download`() = runBlocking {
        MockWebServer().use { proxy ->
            proxy.enqueue(MockResponse().setBody("apk-bytes"))
            val repository = appUpdateRepository(proxy = proxy)
            val update = AppUpdateInfo(
                versionName = "2026.05.26",
                versionCode = 20260526L,
                releaseName = "Nightly",
                tagName = "nightly-2026.05.26",
                publishedAt = "",
                releaseUrl = "http://github.example/release",
                assetName = "app-release.apk",
                assetSizeBytes = 9,
                downloadUrl = "http://github.example/app-release.apk",
            )

            runCatching {
                repository.downloadAndLaunchInstaller(update) { }
            }

            val request = proxy.takeRequest()
            assertEquals("github.example", request.headers["Host"])
        }
    }

    @Test
    fun `downloadAndLaunchInstaller includes response body when APK download fails`() = runBlocking {
        MockWebServer().use { proxy ->
            proxy.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setBody("release asset blocked by proxy")
            )
            val repository = appUpdateRepository(proxy = proxy)
            val update = AppUpdateInfo(
                versionName = "2026.05.26",
                versionCode = 20260526L,
                releaseName = "Nightly",
                tagName = "nightly-2026.05.26",
                publishedAt = "",
                releaseUrl = "http://github.example/release",
                assetName = "app-release.apk",
                assetSizeBytes = 9,
                downloadUrl = "http://github.example/app-release.apk",
            )

            val result = repository.downloadAndLaunchInstaller(update) { }

            assertTrue(result is Result.Error)
            val error = (result as Result.Error).error
            assertTrue(error is AppError.AppUpdateError.DownloadFailed)
            val updateError = error as AppError.AppUpdateError.DownloadFailed
            assertTrue(updateError.cause.contains("HTTP 403"))
            assertTrue(updateError.cause.contains("release asset blocked by proxy"))
        }
    }

    private fun appUpdateRepository(
        updateManifestUrl: String = "http://github.example/latest.json",
        latestReleaseApiUrl: String = "http://api.github.example/repos/ModerRAS/MiruPlay/releases/latest",
        proxy: MockWebServer,
    ): AppUpdateRepositoryImpl =
        AppUpdateRepositoryImpl(
            context = ApplicationProvider.getApplicationContext(),
            okHttpClient = OkHttpClient(),
            cloudDriveRepository = FakeCloudDriveAutomationRepository(
                CloudDriveAutomationConfig(
                    rssProxyEnabled = true,
                    rssProxyHost = proxy.hostName,
                    rssProxyPort = proxy.port,
                )
            ),
            updateManifestUrl = updateManifestUrl,
            latestReleaseApiUrl = latestReleaseApiUrl,
        )

    private class FakeCloudDriveAutomationRepository(
        private val config: CloudDriveAutomationConfig
    ) : CloudDriveAutomationRepository {
        override fun observeConfig(): Flow<CloudDriveAutomationConfig> = flowOf(config)
        override suspend fun getConfig(): Result<CloudDriveAutomationConfig> = Result.success(config)
        override suspend fun saveConfig(config: CloudDriveAutomationConfig): Result<Unit> = Result.success(Unit)
        override suspend fun updateLastRunAt(timestamp: Long): Result<Unit> = Result.success(Unit)
        override fun observeSubscriptions(): Flow<List<RssSubscriptionInfo>> = flowOf(emptyList())
        override suspend fun listEnabledSubscriptions(): Result<List<RssSubscriptionInfo>> = Result.success(emptyList())
        override suspend fun saveSubscription(subscription: RssSubscriptionInfo): Result<Long> = Result.success(0L)
        override suspend fun deleteSubscription(id: Long): Result<Unit> = Result.success(Unit)
        override suspend fun markSubscriptionChecked(id: Long, timestamp: Long): Result<Unit> = Result.success(Unit)
        override suspend fun isItemProcessed(subscriptionId: Long, itemKey: String): Result<Boolean> = Result.success(false)
        override suspend fun markItemProcessed(item: RssProcessedItemInfo): Result<Unit> = Result.success(Unit)
        override suspend fun saveDownloadTask(task: RssDownloadTaskInfo): Result<Long> = Result.success(0L)
    }
}
