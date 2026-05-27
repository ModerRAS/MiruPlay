package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopMediaSourceFactory
import com.miruplay.tv.mediasource.desktop.DesktopSmbMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopWebDavMediaSource
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.repository.MediaSourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSourceActivationTest {
    @Test
    fun `source form state keeps one saved source per type`() {
        val local = MediaSourceInfoConventions.local(name = "Library", rootPath = "D:/Anime").copy(id = 1L)
        val webDav = MediaSourceInfoConventions.webDav(
            url = "https://dav.example.test/anime",
            username = "alice",
            password = "secret",
            name = "Cloud",
        ).copy(id = 2L)
        val smb = MediaSourceInfoConventions.smb(
            url = "smb://nas/anime",
            domain = "WORKGROUP",
            username = "bob",
            password = "hidden",
            name = "NAS",
        ).copy(id = 3L)

        val formState = listOf(smb, webDav, local).desktopSourceFormState()

        assertEquals("D:/Anime", formState.libraryRoot)
        assertEquals("https://dav.example.test/anime", formState.webDavUrl)
        assertEquals("alice", formState.webDavUsername)
        assertEquals("secret", formState.webDavPassword)
        assertEquals("smb://nas/anime", formState.smbUrl)
        assertEquals("WORKGROUP", formState.smbDomain)
        assertEquals("bob", formState.smbUsername)
        assertEquals("hidden", formState.smbPassword)
    }

    @Test
    fun `startup source prefers local then webdav then smb`() {
        val webDav = MediaSourceInfoConventions.webDav(url = "https://dav.example.test/anime").copy(id = 2L)
        val smb = MediaSourceInfoConventions.smb(url = "smb://nas/anime").copy(id = 3L)
        val local = MediaSourceInfoConventions.local(name = "Library", rootPath = "D:/Anime").copy(id = 1L)

        assertEquals(local, listOf(smb, webDav, local).preferredDesktopStartupSource())
        assertEquals(webDav, listOf(smb, webDav).preferredDesktopStartupSource())
        assertEquals(smb, listOf(smb).preferredDesktopStartupSource())
        assertNull(emptyList<com.miruplay.tv.model.MediaSourceInfo>().preferredDesktopStartupSource())
    }

    @Test
    fun `initial source form state trims locations but preserves passwords`() {
        val state = desktopSourceFormStateFromInitialValues(
            libraryRoot = "  D:/Anime  ",
            webDavUrl = "  https://dav.example.test/anime  ",
            webDavUsername = "  alice  ",
            webDavPassword = "  secret  ",
            smbUrl = "  smb://nas/anime  ",
            smbDomain = "  WORKGROUP  ",
            smbUsername = "  bob  ",
            smbPassword = "  hidden  ",
        )

        assertEquals("D:/Anime", state.libraryRoot)
        assertEquals("https://dav.example.test/anime", state.webDavUrl)
        assertEquals("alice", state.webDavUsername)
        assertEquals("  secret  ", state.webDavPassword)
        assertEquals("smb://nas/anime", state.smbUrl)
        assertEquals("WORKGROUP", state.smbDomain)
        assertEquals("bob", state.smbUsername)
        assertEquals("  hidden  ", state.smbPassword)
    }

    @Test
    fun `initial source form state detects seeded values`() {
        assertFalse(DesktopSourceFormState().hasAnyValue())
        assertTrue(desktopSourceFormStateFromInitialValues(webDavUrl = "  https://dav.example.test/anime  ").hasAnyValue())
        assertTrue(desktopSourceFormStateFromInitialValues(webDavPassword = "secret").hasAnyValue())
    }

    @Test
    fun `local activation updates library and clears remote browser`() {
        val source = MediaSourceInfoConventions.local(name = "Library", rootPath = "D:/Anime").copy(id = 1L)

        val activation = source.desktopSourceActivationState(saved = true)

        assertEquals(source, activation.sourceInfo)
        assertEquals("D:/Anime", activation.formState.libraryRoot)
        assertEquals("已载入已保存媒体源：Library · 本地", activation.libraryStatus)
        assertNull(activation.remoteStatus)
        assertTrue(activation.clearsRemoteBrowser)
        assertFalse(activation.loadsRemoteRoot)
        assertEquals(activation.libraryStatus, activation.indexedEmptyStatus)
    }

    @Test
    fun `remote activation updates remote form and loads root`() {
        val source = MediaSourceInfoConventions.smb(
            url = "smb://nas/anime",
            domain = "WORKGROUP",
            username = "bob",
            password = "hidden",
            name = "NAS",
        ).copy(id = 3L)

        val activation = source.desktopSourceActivationState(saved = true)

        assertEquals(source, activation.sourceInfo)
        assertEquals("smb://nas/anime", activation.formState.smbUrl)
        assertEquals("WORKGROUP", activation.formState.smbDomain)
        assertEquals("bob", activation.formState.smbUsername)
        assertEquals("hidden", activation.formState.smbPassword)
        assertNull(activation.libraryStatus)
        assertEquals("已载入已保存媒体源：NAS · SMB", activation.remoteStatus)
        assertFalse(activation.clearsRemoteBrowser)
        assertTrue(activation.loadsRemoteRoot)
        assertEquals(activation.remoteStatus, activation.indexedEmptyStatus)
    }

    @Test
    fun `open desktop source persists local source and returns activation model`() = runBlocking {
        val repository = FakeMediaSourceRepository(nextId = 42L)
        val source = MediaSourceInfoConventions.local(name = "Library", rootPath = "D:/Anime")

        val result = openDesktopSource(
            repository = repository,
            mediaSourceFactory = DesktopMediaSourceFactory(),
            sourceInfo = source,
            testConnection = { Result.success(true) },
        )

        assertTrue(result is Result.Success)
        val opened = (result as Result.Success).data
        assertEquals(42L, opened.sourceInfo.id)
        assertEquals(source.copy(id = 42L, isConnected = false), repository.addedSources.single())
        assertEquals(source.copy(id = 42L, isConnected = true), repository.updatedSources.single())
        assertTrue(opened.sourceInfo.isConnected)
        assertTrue(opened.source is DesktopLocalMediaSource)
        assertEquals("D:/Anime", opened.formState.libraryRoot)
        assertEquals("本地媒体源已就绪：Library", opened.status)
        assertFalse(opened.opensRemoteRoot)
    }

    @Test
    fun `open desktop source returns remote source activation model`() = runBlocking {
        val repository = FakeMediaSourceRepository(nextId = 7L)
        val mediaSourceFactory = DesktopMediaSourceFactory()
        val webDav = MediaSourceInfoConventions.webDav(
            url = "https://dav.example.test/anime",
            username = "alice",
            password = "secret",
            name = "Cloud",
        )
        val smb = MediaSourceInfoConventions.smb(
            url = "smb://nas/anime",
            domain = "WORKGROUP",
            username = "bob",
            password = "hidden",
            name = "NAS",
        )

        val openedWebDav = (
            openDesktopSource(repository, mediaSourceFactory, webDav) { Result.success(true) } as Result.Success
        ).data
        val openedSmb = (
            openDesktopSource(repository, mediaSourceFactory, smb) { Result.success(true) } as Result.Success
        ).data

        assertTrue(openedWebDav.source is DesktopWebDavMediaSource)
        assertEquals(7L, openedWebDav.sourceInfo.id)
        assertTrue(openedWebDav.sourceInfo.isConnected)
        assertEquals(webDav.copy(id = 7L, isConnected = false), repository.addedSources[0])
        assertEquals(webDav.copy(id = 7L, isConnected = true), repository.updatedSources[0])
        assertEquals("https://dav.example.test/anime", openedWebDav.formState.webDavUrl)
        assertEquals("alice", openedWebDav.formState.webDavUsername)
        assertEquals("secret", openedWebDav.formState.webDavPassword)
        assertEquals("WebDAV 媒体源已就绪：Cloud", openedWebDav.status)
        assertTrue(openedWebDav.opensRemoteRoot)

        assertTrue(openedSmb.source is DesktopSmbMediaSource)
        assertEquals(8L, openedSmb.sourceInfo.id)
        assertTrue(openedSmb.sourceInfo.isConnected)
        assertEquals(smb.copy(id = 8L, isConnected = false), repository.addedSources[1])
        assertEquals(smb.copy(id = 8L, isConnected = true), repository.updatedSources[1])
        assertEquals("smb://nas/anime", openedSmb.formState.smbUrl)
        assertEquals("WORKGROUP", openedSmb.formState.smbDomain)
        assertEquals("bob", openedSmb.formState.smbUsername)
        assertEquals("hidden", openedSmb.formState.smbPassword)
        assertEquals("SMB 媒体源已就绪：NAS", openedSmb.status)
        assertTrue(openedSmb.opensRemoteRoot)
    }

    @Test
    fun `open desktop source persists disconnected state when connection test fails`() = runBlocking {
        val repository = FakeMediaSourceRepository(nextId = 9L)
        val source = MediaSourceInfoConventions.webDav(
            url = "https://dav.example.test/anime",
            username = "alice",
            password = "secret",
            name = "Cloud",
        )

        val opened = (
            openDesktopSource(
                repository = repository,
                mediaSourceFactory = DesktopMediaSourceFactory(),
                sourceInfo = source,
                testConnection = { Result.success(false) },
            ) as Result.Success
        ).data

        assertEquals(9L, opened.sourceInfo.id)
        assertFalse(opened.sourceInfo.isConnected)
        assertEquals(false, repository.addedSources.single().isConnected)
        assertEquals(false, repository.updatedSources.single().isConnected)
        assertEquals("WebDAV 媒体源已就绪：Cloud", opened.status)
    }

    private class FakeMediaSourceRepository(
        private var nextId: Long,
    ) : MediaSourceRepository {
        val addedSources = mutableListOf<MediaSourceInfo>()
        val updatedSources = mutableListOf<MediaSourceInfo>()

        override suspend fun addSource(source: MediaSourceInfo): Result<Long> {
            addedSources += source.copy(id = nextId)
            return Result.success(nextId++)
        }

        override suspend fun removeSource(sourceId: Long): Result<Unit> =
            Result.success(Unit)

        override suspend fun getSources(): Result<List<MediaSourceInfo>> =
            Result.success(emptyList())

        override suspend fun updateSource(source: MediaSourceInfo): Result<Unit> {
            updatedSources += source
            return Result.success(Unit)
        }

        override suspend fun getSourceById(sourceId: Long): Result<MediaSourceInfo> =
            Result.success(addedSources.first { it.id == sourceId })
    }
}
