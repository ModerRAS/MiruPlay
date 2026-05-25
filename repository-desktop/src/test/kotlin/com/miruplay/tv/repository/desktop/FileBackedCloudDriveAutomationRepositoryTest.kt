package com.miruplay.tv.repository.desktop

import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.RssSubscriptionInfo
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class FileBackedCloudDriveAutomationRepositoryTest {
    @Test
    fun `observe config emits saved config updates`() = runBlocking {
        val storePath = Files.createTempDirectory("miruplay-cloud-config-observe").resolve("store.json")
        try {
            val repository = FileBackedCloudDriveAutomationRepository(
                DesktopRepositoryStore(storePath),
            )
            val initial = repository.observeConfig().first()
            assertEquals(CloudDriveAutomationConfig(), initial)

            val observedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(2_000L) {
                    repository.observeConfig()
                        .drop(1)
                        .first()
                }
            }

            val savedConfig = CloudDriveAutomationConfig(
                endpointUrl = "http://cloud.test",
                username = "miru",
                inboxPath = "/Downloads",
                libraryPath = "/Library",
                intervalMinutes = 45,
                enabled = true,
            )
            repository.saveConfig(savedConfig)

            val observed = observedDeferred.await()
            assertEquals(savedConfig, observed)
        } finally {
            withContext(Dispatchers.IO) {
                storePath.parent.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `observe subscriptions emits saved subscription updates`() = runBlocking {
        val storePath = Files.createTempDirectory("miruplay-cloud-subscriptions-observe").resolve("store.json")
        try {
            val repository = FileBackedCloudDriveAutomationRepository(
                DesktopRepositoryStore(storePath),
            )
            val initial = repository.observeSubscriptions().first()
            assertEquals(emptyList<RssSubscriptionInfo>(), initial)

            val observedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(2_000L) {
                    repository.observeSubscriptions()
                        .drop(1)
                        .first()
                }
            }

            repository.saveSubscription(
                RssSubscriptionInfo(
                    name = "Anime",
                    url = "https://example.test/rss.xml",
                    enabled = true,
                ),
            )

            val observed = observedDeferred.await()
            assertEquals(1, observed.size)
            assertEquals("Anime", observed.single().name)
            assertEquals("https://example.test/rss.xml", observed.single().url)
            assertEquals(true, observed.single().enabled)
        } finally {
            withContext(Dispatchers.IO) {
                storePath.parent.toFile().deleteRecursively()
            }
        }
    }
}
