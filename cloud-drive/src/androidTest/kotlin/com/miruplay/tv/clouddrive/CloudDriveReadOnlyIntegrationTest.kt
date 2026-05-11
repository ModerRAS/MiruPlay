package com.miruplay.tv.clouddrive

import androidx.test.platform.app.InstrumentationRegistry
import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class CloudDriveReadOnlyIntegrationTest {
    @Test
    fun apiTokenCanReadScopedRoot() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val endpoint = args.getString("endpoint").orEmpty()
        val token = args.getString("token").orEmpty()
        assumeTrue("endpoint/token instrumentation args are required", endpoint.isNotBlank() && token.isNotBlank())

        val client = GrpcCloudDriveClient()
        val tokenInfoResult = client.getApiTokenInfo(endpoint, token)
        assertTrue("GetApiTokenInfo failed: $tokenInfoResult", tokenInfoResult is Result.Success)
        val tokenInfo = (tokenInfoResult as Result.Success).data
        assertTrue("Token must allow listing for read-only verification", tokenInfo.allowList)

        val readPath = tokenInfo.rootDir.ifBlank { "/" }
        val listResult = client.listFolder(
            CloudDriveEndpoint(url = endpoint, token = token),
            path = readPath,
            forceRefresh = false
        )
        assertTrue("GetSubFiles failed: $listResult", listResult is Result.Success)
        val files = (listResult as Result.Success).data

        println(
            "CLOUDDRIVE_READONLY_OK root=${readPath} " +
                "friendlyName=${tokenInfo.friendlyName.ifBlank { "<empty>" }} " +
                "allowOffline=${tokenInfo.allowAddOfflineDownload} " +
                "allowMove=${tokenInfo.allowMove} " +
                "entries=${files.size}"
        )
        assertFalse("Read path must not be blank", readPath.isBlank())
    }
}
