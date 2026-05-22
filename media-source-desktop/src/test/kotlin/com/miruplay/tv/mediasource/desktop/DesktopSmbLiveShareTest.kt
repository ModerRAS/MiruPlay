package com.miruplay.tv.mediasource.desktop

import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopSmbLiveShareTest {
    @Test
    fun `listFiles reads configured live SMB share`() = runBlocking {
        val url = System.getProperty("miruplay.smbLiveUrl").orEmpty()
        assumeTrue(
            "Set -Dmiruplay.smbLiveUrl=smb://host/share/path to run the live SMB share smoke.",
            url.isNotBlank(),
        )

        val source = DesktopSmbMediaSource.create(
            name = "Live SMB",
            url = url,
            username = System.getProperty("miruplay.smbLiveUsername").orEmpty(),
            password = System.getProperty("miruplay.smbLivePassword").orEmpty(),
            domain = System.getProperty("miruplay.smbLiveDomain").orEmpty(),
        )
        try {
            val result = source.listFiles("")

            when (result) {
                is Result.Success -> {
                    val entries = result.data
                    val expectedName = System.getProperty("miruplay.smbLiveExpectedName").orEmpty()
                    if (expectedName.isNotBlank()) {
                        assertTrue(
                            "Expected live SMB share to contain '$expectedName', got ${entries.map { it.name }}",
                            entries.any { it.name == expectedName },
                        )
                    }
                }
                is Result.Error -> {
                    throw AssertionError("Expected live SMB listFiles to succeed for $url, got $result")
                }
            }
        } finally {
            source.close()
        }
    }
}
