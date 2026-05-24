package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSourceConnectionTestResult
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.repository.MediaSourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebControlSourceRequestsTest {
    @Test
    fun `source request builds local source with shared connection keys`() {
        val source = SourceRequest(
            id = 7L,
            name = "  ",
            type = " local ",
            location = " D:/Anime ",
            displayName = " Anime Drive ",
        ).toMediaSourceInfo(isConnected = true, lastScanned = 123L)

        assertEquals(7L, source.id)
        assertEquals("本地媒体库", source.name)
        assertEquals(MediaSourceType.LOCAL, source.type)
        assertEquals(true, source.isConnected)
        assertEquals(123L, source.lastScanned)
        assertEquals("D:/Anime", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_URL])
        assertEquals("D:/Anime", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_PATH])
        assertEquals("Anime Drive", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_DISPLAY_NAME])
    }

    @Test
    fun `source request preserves fallback password when password is blank`() {
        val source = SourceRequest(
            name = "Remote",
            type = "WEBDAV",
            location = " https://dav.example.test/root ",
            username = " miru ",
            password = " ",
        ).toMediaSourceInfo(fallbackPassword = "old-secret")

        assertEquals("https://dav.example.test/root", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_URL])
        assertEquals("miru", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_USERNAME])
        assertEquals("old-secret", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_PASSWORD])
    }

    @Test
    fun `source request uses new password and default remote names`() {
        val webDav = SourceRequest(
            name = "",
            type = "webdav",
            location = "https://dav.example.test",
            password = "new-secret",
        ).toMediaSourceInfo(fallbackPassword = "old-secret")
        val smb = SourceRequest(
            name = "",
            type = "smb",
            location = "\\\\NAS\\Anime",
        ).toMediaSourceInfo()

        assertEquals("WebDAV 媒体库", webDav.name)
        assertEquals("new-secret", webDav.connectionInfo[MediaSourceInfoConventions.CONNECTION_PASSWORD])
        assertEquals("SMB 共享", smb.name)
        assertEquals("smb://NAS/Anime", smb.connectionInfo[MediaSourceInfoConventions.CONNECTION_URL])
    }

    @Test
    fun `source test request builds sanitized connection info`() {
        val source = SourceTestRequest(
            type = "local",
            location = " content://tree/anime ",
            displayName = "Tree",
        ).toMediaSourceInfo()

        assertEquals("content://tree/anime", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_URL])
        assertEquals("content://tree/anime", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_URI])
        assertEquals("Tree", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_DISPLAY_NAME])
    }

    @Test
    fun `repository add WebUI source tests persisted source and redacts response`() = runBlocking {
        val repository = FakeMediaSourceRepository(addResult = Result.success(42L))
        val testedSources = mutableListOf<MediaSourceInfo>()

        val response = repository.addWebControlSource(
            request = SourceRequest(
                name = " Remote ",
                type = "webdav",
                location = " https://dav.example.test/root ",
                username = " miru ",
                password = "secret",
            ),
            testConnection = { source ->
                testedSources += source
                SourceTestResponse(connected = true, message = "连接正常")
            },
        )

        val tested = testedSources.single()
        val saved = requireNotNull(repository.updatedSource)
        assertEquals(42L, tested.id)
        assertEquals("Remote", tested.name)
        assertEquals("secret", tested.connectionInfo[MediaSourceInfoConventions.CONNECTION_PASSWORD])
        assertEquals(false, tested.isConnected)
        assertEquals(42L, saved.id)
        assertEquals(true, saved.isConnected)
        assertEquals("secret", saved.connectionInfo[MediaSourceInfoConventions.CONNECTION_PASSWORD])
        assertEquals(42L, response.id)
        assertEquals(true, response.isConnected)
        assertEquals(null, response.connectionInfo[MediaSourceInfoConventions.CONNECTION_PASSWORD])
    }

    @Test
    fun `repository add WebUI source persists disconnected test result`() = runBlocking {
        val repository = FakeMediaSourceRepository(addResult = Result.success(43L))

        val response = repository.addWebControlSource(
            request = SourceRequest(name = "Local", type = "local", location = "D:/Anime"),
            testConnection = { SourceTestResponse(connected = false, message = "无法连接到服务器") },
        )

        assertEquals(false, requireNotNull(repository.updatedSource).isConnected)
        assertEquals(false, response.isConnected)
    }

    @Test
    fun `repository add WebUI source maps add failures`() = runBlocking {
        val repository = FakeMediaSourceRepository(
            addResult = Result.failure(AppError.MediaSourceError.PermissionDenied("D:/Anime")),
        )

        val failure = runCatching {
            repository.addWebControlSource(
                request = SourceRequest(name = "Local", type = "local", location = "D:/Anime"),
                testConnection = { SourceTestResponse(connected = true, message = "连接正常") },
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("添加媒体源失败: 无权限访问：D:/Anime", failure?.message)
        assertEquals(null, repository.updatedSource)
    }

    @Test
    fun `repository add WebUI source maps connected-state update failures`() = runBlocking {
        val repository = FakeMediaSourceRepository(
            addResult = Result.success(42L),
            updateResult = Result.failure(AppError.MediaSourceError.PermissionDenied("42")),
        )

        val failure = runCatching {
            repository.addWebControlSource(
                request = SourceRequest(name = "Local", type = "local", location = "D:/Anime"),
                testConnection = { SourceTestResponse(connected = true, message = "连接正常") },
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("更新媒体源失败: 无权限访问：42", failure?.message)
    }

    @Test
    fun `repository update WebUI source preserves existing password and state and redacts response`() = runBlocking {
        val existing = MediaSourceInfoConventions.webDav(
            name = "Old",
            url = "https://old.example.test/dav",
            username = "old-user",
            password = "old-secret",
            isConnected = true,
        ).copy(id = 7L, lastScanned = 123L)
        val repository = FakeMediaSourceRepository(existing = existing)

        val response = repository.updateWebControlSource(
            sourceId = 7L,
            request = SourceRequest(
                name = " New ",
                type = "webdav",
                location = " https://new.example.test/dav ",
                username = " new-user ",
                password = " ",
            ),
        )

        val saved = requireNotNull(repository.updatedSource)
        assertEquals(7L, saved.id)
        assertEquals("New", saved.name)
        assertEquals(true, saved.isConnected)
        assertEquals(123L, saved.lastScanned)
        assertEquals("new-user", saved.connectionInfo[MediaSourceInfoConventions.CONNECTION_USERNAME])
        assertEquals("old-secret", saved.connectionInfo[MediaSourceInfoConventions.CONNECTION_PASSWORD])
        assertEquals(null, response.connectionInfo[MediaSourceInfoConventions.CONNECTION_PASSWORD])
    }

    @Test
    fun `repository update WebUI source maps missing source`() = runBlocking {
        val repository = FakeMediaSourceRepository(
            getResult = Result.failure(AppError.MediaSourceError.NotFound("7")),
        )

        val failure = runCatching {
            repository.updateWebControlSource(
                sourceId = 7L,
                request = SourceRequest(name = "New", type = "local", location = "D:/Anime"),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("媒体源不存在: 找不到文件或目录：7", failure?.message)
    }

    @Test
    fun `repository remove WebUI source maps repository errors`() = runBlocking {
        val repository = FakeMediaSourceRepository(
            removeResult = Result.failure(AppError.MediaSourceError.PermissionDenied("7")),
        )

        val failure = runCatching {
            repository.removeWebControlSource(7L)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("删除媒体源失败: 无权限访问：7", failure?.message)
    }

    @Test
    fun `safe api source removes connection password case insensitively`() {
        val source = SourceRequest(
            name = "Remote",
            type = "webdav",
            location = "https://dav.example.test",
            password = "secret",
        ).toMediaSourceInfo()

        val safe = source.copy(
            connectionInfo = source.connectionInfo + ("Password" to "other-secret"),
        ).safeForApi()

        assertFalse(MediaSourceInfoConventions.CONNECTION_PASSWORD in safe.connectionInfo)
        assertFalse("Password" in safe.connectionInfo)
    }

    @Test
    fun `source test result maps success and disconnected messages`() {
        val connected = Result.success(true).toWebControlSourceTestResponse()
        val disconnected = Result.success(false).toWebControlSourceTestResponse()

        assertEquals(true, connected.connected)
        assertEquals("连接正常", connected.message)
        assertEquals(false, disconnected.connected)
        assertEquals("无法连接到服务器", disconnected.message)
    }

    @Test
    fun `source test result maps shared media source connection results`() {
        val connected = MediaSourceConnectionTestResult.Success.toWebControlSourceTestResponse()
        val failed = MediaSourceConnectionTestResult.Failed("认证失败").toWebControlSourceTestResponse()

        assertEquals(true, connected.connected)
        assertEquals("连接正常", connected.message)
        assertEquals(false, failed.connected)
        assertEquals("认证失败", failed.message)
    }

    @Test
    fun `source test result uses user facing error messages`() {
        val response = Result.failure(
            AppError.MediaSourceError.AuthenticationFailed("WebDAV"),
        ).toWebControlSourceTestResponse()

        assertEquals(false, response.connected)
        assertEquals("WebDAV 认证失败，请检查用户名和密码", response.message)
    }

    @Test
    fun `scan result maps to WebUI scan response`() {
        val response = ScanResult(
            animeName = "Frieren",
            episodesFound = 3,
            newEpisodes = 2,
            updatedEpisodes = 1,
        ).toWebControlSourceScanResponse(sourceId = 7L)

        assertEquals(7L, response.sourceId)
        assertEquals("Frieren", response.animeName)
        assertEquals(3, response.episodesFound)
        assertEquals(2, response.newEpisodes)
        assertEquals(1, response.updatedEpisodes)
    }

    @Test
    fun `scan response builder normalizes blank name and negative counts`() {
        val response = toWebControlSourceScanResponse(
            sourceId = 8L,
            animeName = "",
            episodesFound = -1,
            newEpisodes = -2,
            updatedEpisodes = -3,
        )

        assertEquals(8L, response.sourceId)
        assertEquals("Unknown", response.animeName)
        assertEquals(0, response.episodesFound)
        assertEquals(0, response.newEpisodes)
        assertEquals(0, response.updatedEpisodes)
    }

    private class FakeMediaSourceRepository(
        existing: MediaSourceInfo = MediaSourceInfoConventions.local(name = "Local", rootPath = "D:/Anime"),
        private val addResult: Result<Long> = Result.success(1L),
        private val getResult: Result<MediaSourceInfo> = Result.success(existing),
        private val removeResult: Result<Unit> = Result.success(Unit),
        private val updateResult: Result<Unit> = Result.success(Unit),
    ) : MediaSourceRepository {
        var updatedSource: MediaSourceInfo? = null

        override suspend fun addSource(source: MediaSourceInfo): Result<Long> =
            addResult

        override suspend fun removeSource(sourceId: Long): Result<Unit> =
            removeResult

        override suspend fun getSources(): Result<List<MediaSourceInfo>> =
            Result.success(emptyList())

        override suspend fun updateSource(source: MediaSourceInfo): Result<Unit> {
            updatedSource = source
            return updateResult
        }

        override suspend fun getSourceById(sourceId: Long): Result<MediaSourceInfo> =
            getResult
    }
}
