package com.miruplay.tv.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSourceActionCoordinatorTest {
    @Test
    fun `add source persists connection test result on saved source`() = runBlocking {
        val repository = FakeMediaSourceRepository(addResult = Result.success(42L))
        val coordinator = MediaSourceActionCoordinator(repository)
        val source = MediaSourceInfoConventions.webDav(
            url = "https://dav.example.test/root",
            username = "miru",
            password = "secret",
        )
        val testedSources = mutableListOf<MediaSourceInfo>()

        val result = coordinator.addSource(source) { tested ->
            testedSources += tested
            Result.success(true)
        }

        val saved = requireNotNull(repository.updatedSource)
        assertEquals(42L, testedSources.single().id)
        assertEquals(false, testedSources.single().isConnected)
        assertEquals(42L, saved.id)
        assertEquals(true, saved.isConnected)
        assertEquals(MediaSourceAddActionResult.Saved(saved), result)
    }

    @Test
    fun `add source treats connection test failure as disconnected saved source`() = runBlocking {
        val repository = FakeMediaSourceRepository(addResult = Result.success(7L))
        val coordinator = MediaSourceActionCoordinator(repository)

        val result = coordinator.addSource(MediaSourceInfoConventions.local(name = "Local", rootPath = "D:/Anime")) {
            Result.failure(AppError.MediaSourceError.PermissionDenied("D:/Anime"))
        }

        val saved = requireNotNull(repository.updatedSource)
        assertEquals(false, saved.isConnected)
        assertEquals(MediaSourceAddActionResult.Saved(saved), result)
    }

    @Test
    fun `add source returns add failure phase`() = runBlocking {
        val failure = AppError.MediaSourceError.PermissionDenied("D:/Anime")
        val repository = FakeMediaSourceRepository(addResult = Result.failure(failure))
        val coordinator = MediaSourceActionCoordinator(repository)

        val result = coordinator.addSource(MediaSourceInfoConventions.local(name = "Local", rootPath = "D:/Anime")) {
            Result.success(true)
        }

        assertEquals(MediaSourceAddActionResult.Failed(failure, MediaSourceAddFailurePhase.AddSource), result)
        assertEquals(null, repository.updatedSource)
    }

    @Test
    fun `add source returns update failure phase when connected state cannot be persisted`() = runBlocking {
        val failure = AppError.MediaSourceError.PermissionDenied("42")
        val repository = FakeMediaSourceRepository(
            addResult = Result.success(42L),
            updateResult = Result.failure(failure),
        )
        val coordinator = MediaSourceActionCoordinator(repository)

        val result = coordinator.addSource(MediaSourceInfoConventions.local(name = "Local", rootPath = "D:/Anime")) {
            Result.success(true)
        }

        assertEquals(
            MediaSourceAddActionResult.Failed(failure, MediaSourceAddFailurePhase.UpdateConnectionState),
            result,
        )
    }

    @Test
    fun `update source preserves old password connection state and scan timestamp`() = runBlocking {
        val existing = MediaSourceInfoConventions.webDav(
            name = "Old",
            url = "https://old.example.test/root",
            username = "old-user",
            password = "old-secret",
            isConnected = true,
        ).copy(id = 5L, lastScanned = 123L)
        val repository = FakeMediaSourceRepository(existing = existing)
        val coordinator = MediaSourceActionCoordinator(repository)
        val source = MediaSourceInfoConventions.webDav(
            name = "New",
            url = "https://new.example.test/root",
            username = "new-user",
            password = "",
        ).copy(id = 5L)

        val result = coordinator.updateSource(source)

        val saved = requireNotNull(repository.updatedSource)
        assertEquals("New", saved.name)
        assertEquals(true, saved.isConnected)
        assertEquals(123L, saved.lastScanned)
        assertEquals("new-user", saved.connectionInfo[MediaSourceInfoConventions.CONNECTION_USERNAME])
        assertEquals("old-secret", saved.connectionInfo[MediaSourceInfoConventions.CONNECTION_PASSWORD])
        assertEquals(saved, result.getOrNull())
    }

    @Test
    fun `update source uses new password when provided`() = runBlocking {
        val existing = MediaSourceInfoConventions.webDav(
            url = "https://old.example.test/root",
            password = "old-secret",
            isConnected = true,
        ).copy(id = 5L)
        val repository = FakeMediaSourceRepository(existing = existing)
        val coordinator = MediaSourceActionCoordinator(repository)
        val source = MediaSourceInfoConventions.webDav(
            url = "https://new.example.test/root",
            password = "new-secret",
        ).copy(id = 5L)

        val result = coordinator.updateSource(source)

        assertEquals("new-secret", result.getOrNull()?.connectionInfo?.get(MediaSourceInfoConventions.CONNECTION_PASSWORD))
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
