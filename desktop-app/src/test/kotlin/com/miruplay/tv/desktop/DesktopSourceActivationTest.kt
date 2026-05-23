package com.miruplay.tv.desktop

import com.miruplay.tv.model.MediaSourceInfoConventions
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
}
