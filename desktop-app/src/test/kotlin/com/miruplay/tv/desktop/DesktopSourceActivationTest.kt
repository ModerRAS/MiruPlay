package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
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

        val result = openDesktopSource(repository, source)

        assertTrue(result is Result.Success)
        val opened = (result as Result.Success).data
        assertEquals(42L, opened.sourceInfo.id)
        assertEquals(source.copy(id = 42L), repository.addedSources.single())
        assertTrue(opened.source is DesktopLocalMediaSource)
        assertEquals("D:/Anime", opened.formState.libraryRoot)
        assertEquals("本地媒体源已就绪：Library", opened.status)
        assertFalse(opened.opensRemoteRoot)
    }

    @Test
    fun `open desktop source returns remote source activation model`() = runBlocking {
        val repository = FakeMediaSourceRepository(nextId = 7L)
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

        val openedWebDav = (openDesktopSource(repository, webDav) as Result.Success).data
        val openedSmb = (openDesktopSource(repository, smb) as Result.Success).data

        assertTrue(openedWebDav.source is DesktopWebDavMediaSource)
        assertEquals(7L, openedWebDav.sourceInfo.id)
        assertEquals("https://dav.example.test/anime", openedWebDav.formState.webDavUrl)
        assertEquals("alice", openedWebDav.formState.webDavUsername)
        assertEquals("secret", openedWebDav.formState.webDavPassword)
        assertEquals("WebDAV 媒体源已就绪：Cloud", openedWebDav.status)
        assertTrue(openedWebDav.opensRemoteRoot)

        assertTrue(openedSmb.source is DesktopSmbMediaSource)
        assertEquals(8L, openedSmb.sourceInfo.id)
        assertEquals("smb://nas/anime", openedSmb.formState.smbUrl)
        assertEquals("WORKGROUP", openedSmb.formState.smbDomain)
        assertEquals("bob", openedSmb.formState.smbUsername)
        assertEquals("hidden", openedSmb.formState.smbPassword)
        assertEquals("SMB 媒体源已就绪：NAS", openedSmb.status)
        assertTrue(openedSmb.opensRemoteRoot)
    }

    private class FakeMediaSourceRepository(
        private var nextId: Long,
    ) : MediaSourceRepository {
        val addedSources = mutableListOf<MediaSourceInfo>()

        override suspend fun addSource(source: MediaSourceInfo): Result<Long> {
            addedSources += source.copy(id = nextId)
            return Result.success(nextId++)
        }

        override suspend fun removeSource(sourceId: Long): Result<Unit> =
            Result.success(Unit)

        override suspend fun getSources(): Result<List<MediaSourceInfo>> =
            Result.success(emptyList())

        override suspend fun updateSource(source: MediaSourceInfo): Result<Unit> =
            Result.success(Unit)

        override suspend fun getSourceById(sourceId: Long): Result<MediaSourceInfo> =
            Result.success(addedSources.first { it.id == sourceId })
    }
}
