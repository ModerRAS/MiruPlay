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

    @Test
    fun `checkLatestUpdate uses configured proxy for GitHub release request`() = runBlocking {
        MockWebServer().use { proxy ->
            proxy.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "tag_name": "nightly-2026.05.26",
                      "name": "Nightly 2026.05.26",
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
                latestReleaseApiUrl = "http://api.github.example/repos/ModerRAS/MiruPlay/releases/latest",
                proxy = proxy,
            )

            val result = repository.checkLatestUpdate()

            assertTrue(result is Result.Success)
            val request = proxy.takeRequest()
            assertEquals("api.github.example", request.headers["Host"])
        }
    }

    @Test
    fun `checkLatestUpdate returns response body when GitHub rejects request`() = runBlocking {
        MockWebServer().use { proxy ->
            proxy.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setBody(
                        """
                        {
                          "message": "API rate limit exceeded for 203.0.113.10.",
                          "documentation_url": "https://docs.github.com/rest/overview/resources-in-the-rest-api#rate-limiting"
                        }
                        """.trimIndent()
                    )
            )
            val repository = appUpdateRepository(
                latestReleaseApiUrl = "http://api.github.example/repos/ModerRAS/MiruPlay/releases/latest",
                proxy = proxy,
            )

            val result = repository.checkLatestUpdate()

            assertTrue(result is Result.Error)
            val error = (result as Result.Error).error
            assertTrue(error is AppError.NetworkError.HttpError)
            val httpError = error as AppError.NetworkError.HttpError
            assertEquals(403, httpError.code)
            assertTrue(httpError.message.contains("API rate limit exceeded"))
            assertTrue(httpError.message.contains("documentation_url"))
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
        latestReleaseApiUrl: String = "http://api.github.example/latest",
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
