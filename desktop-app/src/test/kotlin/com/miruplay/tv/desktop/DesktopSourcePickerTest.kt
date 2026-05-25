package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.libraryRescanCompleteStatus
import com.miruplay.tv.model.libraryScanCompleteStatus
import com.miruplay.tv.model.libraryScanningStatus
import com.miruplay.tv.model.localizedLibraryRescanCompleteStatus
import com.miruplay.tv.model.localizedLibraryScanCompleteStatus
import com.miruplay.tv.model.localizedLibraryScanningStatus
import com.miruplay.tv.model.localizedMediaSourceStatusText
import com.miruplay.tv.model.librarySourceLabels
import com.miruplay.tv.model.mediaSourceStatusText
import com.miruplay.tv.model.mediaSourceClearIndexActionLabel
import com.miruplay.tv.model.mediaSourceIndexQueryFieldLabel
import com.miruplay.tv.model.mediaSourceLocationMissingLabel
import com.miruplay.tv.model.mediaSourceLocalLibraryRootFieldLabel
import com.miruplay.tv.model.mediaSourceRemoteBrowserEmptyMessage
import com.miruplay.tv.model.mediaSourceRemoteBrowserPageUnitLabel
import com.miruplay.tv.model.mediaSourceRemoteBrowserTitleLabel
import com.miruplay.tv.model.mediaSourceRemoveActionLabel
import com.miruplay.tv.model.mediaSourceScanActionLabel
import com.miruplay.tv.model.mediaSourceScanSourceActionLabel
import com.miruplay.tv.model.mediaSourceSearchActionLabel
import com.miruplay.tv.model.mediaSourceSmbDomainFieldLabel
import com.miruplay.tv.model.mediaSourceUpActionLabel
import com.miruplay.tv.model.openSourceActionLabel
import com.miruplay.tv.model.remoteBrowserCoercedPageStart
import com.miruplay.tv.model.remoteBrowserPathPreview
import com.miruplay.tv.model.remoteBrowserPageStartForIndex
import com.miruplay.tv.model.remoteBrowserPageSummary
import com.miruplay.tv.model.remoteSourcePreview
import com.miruplay.tv.model.sourcePasswordFieldLabel
import com.miruplay.tv.model.sourcePickerSubtitle
import com.miruplay.tv.model.sourcePickerTitle
import com.miruplay.tv.model.sourceUsernameFieldLabel
import com.miruplay.tv.model.tvBadgeLabel
import com.miruplay.tv.model.tvLabel
import com.miruplay.tv.model.tvLocationLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSourcePickerTest {
    @Test
    fun `source picker title keeps source name and type visible`() {
        val source = MediaSourceInfoConventions.local(
            name = "Living Room Anime",
            rootPath = "D:/Anime",
        )

        assertEquals("Living Room Anime · 本地", source.sourcePickerTitle())
    }

    @Test
    fun `source picker title localizes missing source names`() {
        val local = MediaSourceInfo(
            id = 1L,
            name = "",
            type = MediaSourceType.LOCAL,
        )

        assertEquals("本地媒体源 · 本地", local.sourcePickerTitle())
        assertEquals(
            "WebDAV 媒体源 · WebDAV",
            local.copy(type = MediaSourceType.WEBDAV).sourcePickerTitle(),
        )
        assertEquals(
            "SMB 媒体源 · SMB",
            local.copy(type = MediaSourceType.SMB).sourcePickerTitle(),
        )
    }

    @Test
    fun `source management controls use TV facing labels`() {
        val labels = librarySourceLabels()

        assertEquals(mediaSourceLocalLibraryRootFieldLabel(), labels.localLibraryRoot)
        assertEquals(mediaSourceIndexQueryFieldLabel(), labels.indexQuery)
        assertEquals(MediaSourceType.LOCAL.openSourceActionLabel(), labels.openLocal)
        assertEquals(mediaSourceScanActionLabel(), labels.scan)
        assertEquals(mediaSourceSearchActionLabel(), labels.search)
        assertEquals(mediaSourceClearIndexActionLabel(), labels.clearIndex)
        assertEquals(mediaSourceRemoveActionLabel(), labels.removeSource)
        assertEquals(MediaSourceType.WEBDAV.tvLocationLabel(), labels.webDavUrl)
        assertEquals(MediaSourceType.WEBDAV.sourceUsernameFieldLabel(), labels.webDavUser)
        assertEquals(MediaSourceType.WEBDAV.sourcePasswordFieldLabel(), labels.webDavPassword)
        assertEquals(MediaSourceType.WEBDAV.openSourceActionLabel(), labels.openWebDav)
        assertEquals(MediaSourceType.SMB.tvLocationLabel(), labels.smbUrl)
        assertEquals(mediaSourceSmbDomainFieldLabel(), labels.smbDomain)
        assertEquals(MediaSourceType.SMB.sourceUsernameFieldLabel(), labels.smbUser)
        assertEquals(MediaSourceType.SMB.sourcePasswordFieldLabel(), labels.smbPassword)
        assertEquals(MediaSourceType.SMB.openSourceActionLabel(), labels.openSmb)
        assertEquals(mediaSourceScanSourceActionLabel(), labels.scanSource)
        assertEquals(mediaSourceRemoteBrowserTitleLabel(), labels.remoteBrowser)
        assertEquals(mediaSourceUpActionLabel(), labels.up)
        assertEquals(mediaSourceRemoteBrowserEmptyMessage(), labels.remoteEmpty)
    }

    @Test
    fun `source management statuses use TV facing text`() {
        assertEquals(
            localizedMediaSourceStatusText("Add a local library source or load an existing one."),
            mediaSourceStatusText("Add a local library source or load an existing one."),
        )
        assertEquals(
            localizedMediaSourceStatusText("Open a WebDAV or SMB source to browse it."),
            mediaSourceStatusText("Open a WebDAV or SMB source to browse it."),
        )
        assertEquals(localizedMediaSourceStatusText("Enter a local library root first."), mediaSourceStatusText("Enter a local library root first."))
        assertEquals(localizedMediaSourceStatusText("Enter a WebDAV URL first."), mediaSourceStatusText("Enter a WebDAV URL first."))
        assertEquals(localizedMediaSourceStatusText("Enter an SMB URL first."), mediaSourceStatusText("Enter an SMB URL first."))
        assertEquals(localizedMediaSourceStatusText("Open a source before scanning."), mediaSourceStatusText("Open a source before scanning."))
        assertEquals(localizedMediaSourceStatusText("Loaded local source: Library"), mediaSourceStatusText("Loaded local source: Library"))
        assertEquals(localizedMediaSourceStatusText("Loaded saved local source: Library"), mediaSourceStatusText("Loaded saved local source: Library"))
        assertEquals(localizedMediaSourceStatusText("WebDAV source ready: Cloud"), mediaSourceStatusText("WebDAV source ready: Cloud"))
        assertEquals(localizedLibraryScanningStatus("Library"), mediaSourceStatusText(libraryScanningStatus("Library")))
        assertEquals(localizedLibraryScanCompleteStatus(12, 3), mediaSourceStatusText(libraryScanCompleteStatus(12, 3)))
        assertEquals(localizedLibraryRescanCompleteStatus(12, 3), mediaSourceStatusText(libraryRescanCompleteStatus(12, 3)))
        assertEquals(localizedMediaSourceStatusText("Open or scan a source before searching."), mediaSourceStatusText("Open or scan a source before searching."))
        assertEquals(
            localizedMediaSourceStatusText("Open or scan a source before clearing its index."),
            mediaSourceStatusText("Open or scan a source before clearing its index."),
        )
        assertEquals(localizedMediaSourceStatusText("Index cleared for source id: 42."), mediaSourceStatusText("Index cleared for source id: 42."))
        assertEquals(localizedMediaSourceStatusText("Open a source before removing it."), mediaSourceStatusText("Open a source before removing it."))
        assertEquals(
            localizedMediaSourceStatusText("Source removed. Associated index entries were cleared."),
            mediaSourceStatusText("Source removed. Associated index entries were cleared."),
        )
        assertEquals(localizedMediaSourceStatusText("Already at the source root."), mediaSourceStatusText("Already at the source root."))
        assertEquals(localizedMediaSourceStatusText("Open a remote source before browsing."), mediaSourceStatusText("Open a remote source before browsing."))
        assertEquals(localizedMediaSourceStatusText("Loading WEBDAV /Anime..."), mediaSourceStatusText("Loading WEBDAV /Anime..."))
        assertEquals(localizedMediaSourceStatusText("Showing 1 item(s) from Cloud."), mediaSourceStatusText("Showing 1 item(s) from Cloud."))
        assertEquals(localizedMediaSourceStatusText("Selected Frieren EP1 for playback."), mediaSourceStatusText("Selected Frieren EP1 for playback."))
        assertEquals(
            localizedMediaSourceStatusText("Selected remote media: Episode.mkv. mpv will stream through the local bridge."),
            mediaSourceStatusText("Selected remote media: Episode.mkv. mpv will stream through the local bridge."),
        )
        assertEquals(localizedMediaSourceStatusText("No indexed media matched \"frieren\"."), mediaSourceStatusText("No indexed media matched \"frieren\"."))
        assertEquals(localizedMediaSourceStatusText("Showing 24 indexed video result(s)."), mediaSourceStatusText("Showing 24 indexed video result(s)."))
        assertEquals("custom status", mediaSourceStatusText("custom status"))
    }

    @Test
    fun `source picker subtitle compacts long paths from the middle`() {
        val source = MediaSourceInfoConventions.local(
            name = "Long Library",
            rootPath = "D:/Software/dufs/anime/very-long-library-name/season-one/subfolder/episodes",
        )

        val subtitle = source.sourcePickerSubtitle(maxLength = 38)

        assertTrue(subtitle.length <= 38)
        assertTrue(subtitle.startsWith("D:/Software"))
        assertTrue(subtitle.endsWith("/episodes"))
        assertTrue(subtitle.contains("..."))
    }

    @Test
    fun `source picker subtitle shows configured remote url`() {
        val source = MediaSourceInfoConventions.smb(
            url = "smb://smb.ynz.local/share/temporary/test",
            username = "ynsz",
            password = "ynsz",
        )

        assertEquals("smb://smb.ynz.local/share/temporary/test", source.sourcePickerSubtitle(maxLength = 80))
    }

    @Test
    fun `source picker subtitle handles missing location`() {
        val source = MediaSourceInfo(
            id = 1L,
            name = "Broken",
            type = MediaSourceType.LOCAL,
        )

        assertEquals(mediaSourceLocationMissingLabel(), source.sourcePickerSubtitle())
    }

    @Test
    fun `source picker directional keys move between saved sources`() {
        val sources = listOf(
            MediaSourceInfoConventions.local(name = "A", rootPath = "D:/A").copy(id = 10L),
            MediaSourceInfoConventions.webDav(url = "https://dav.example.test/anime").copy(id = 11L),
            MediaSourceInfoConventions.smb(url = "smb://nas.local/anime").copy(id = 12L),
        )

        assertEquals(11L, sources.savedSourcePickerNavigationTarget(activeSourceId = 10L, key = Key.DirectionDown)?.id)
        assertEquals(11L, sources.savedSourcePickerNavigationTarget(activeSourceId = 10L, key = Key.DirectionRight)?.id)
        assertEquals(10L, sources.savedSourcePickerNavigationTarget(activeSourceId = 11L, key = Key.DirectionUp)?.id)
        assertEquals(10L, sources.savedSourcePickerNavigationTarget(activeSourceId = 11L, key = Key.DirectionLeft)?.id)
        assertEquals(10L, sources.savedSourcePickerNavigationTarget(activeSourceId = null, key = Key.DirectionDown)?.id)
        assertEquals(12L, sources.savedSourcePickerNavigationTarget(activeSourceId = null, key = Key.DirectionUp)?.id)
        assertNull(sources.savedSourcePickerNavigationTarget(activeSourceId = 12L, key = Key.DirectionDown))
        assertNull(sources.savedSourcePickerNavigationTarget(activeSourceId = 10L, key = Key.DirectionUp))
    }

    @Test
    fun `source picker navigation also accepts shared direction intents`() {
        val sources = listOf(
            MediaSourceInfoConventions.local(name = "A", rootPath = "D:/A").copy(id = 10L),
            MediaSourceInfoConventions.webDav(url = "https://dav.example.test/anime").copy(id = 11L),
            MediaSourceInfoConventions.smb(url = "smb://nas.local/anime").copy(id = 12L),
        )

        assertEquals(
            11L,
            sources.savedSourcePickerNavigationTarget(
                activeSourceId = 10L,
                intent = MiruPlayInputIntent.DirectionDown,
            )?.id,
        )
        assertEquals(
            11L,
            sources.savedSourcePickerNavigationTarget(
                activeSourceId = 10L,
                intent = MiruPlayInputIntent.DirectionRight,
            )?.id,
        )
        assertEquals(
            10L,
            sources.savedSourcePickerNavigationTarget(
                activeSourceId = 11L,
                intent = MiruPlayInputIntent.DirectionUp,
            )?.id,
        )
        assertEquals(
            10L,
            sources.savedSourcePickerNavigationTarget(
                activeSourceId = 11L,
                intent = MiruPlayInputIntent.DirectionLeft,
            )?.id,
        )
        assertEquals(
            10L,
            sources.savedSourcePickerNavigationTarget(
                activeSourceId = null,
                intent = MiruPlayInputIntent.DirectionDown,
            )?.id,
        )
        assertEquals(
            12L,
            sources.savedSourcePickerNavigationTarget(
                activeSourceId = null,
                intent = MiruPlayInputIntent.DirectionUp,
            )?.id,
        )
        assertNull(
            sources.savedSourcePickerNavigationTarget(
                activeSourceId = 12L,
                intent = MiruPlayInputIntent.DirectionDown,
            ),
        )
        assertNull(
            sources.savedSourcePickerNavigationTarget(
                activeSourceId = 10L,
                intent = MiruPlayInputIntent.Activate,
            ),
        )
    }

    @Test
    fun `source picker key event opens and navigates with TV keys`() {
        val sources = listOf(
            MediaSourceInfoConventions.local(name = "A", rootPath = "D:/A").copy(id = 10L),
            MediaSourceInfoConventions.webDav(url = "https://dav.example.test/anime").copy(id = 11L),
        )
        var opens = 0
        var selectedId: Long? = null

        assertTrue(
            savedSourcePickerKeyEvent(
                sources = sources,
                activeSourceId = 10L,
                key = Key.DirectionCenter,
                type = KeyEventType.KeyDown,
                onOpen = { opens += 1 },
                onSelected = { selectedId = it.id },
            ),
        )
        assertEquals(1, opens)
        assertNull(selectedId)

        assertTrue(
            savedSourcePickerKeyEvent(
                sources = sources,
                activeSourceId = 10L,
                key = Key.DirectionDown,
                type = KeyEventType.KeyDown,
                onOpen = { opens += 1 },
                onSelected = { selectedId = it.id },
            ),
        )
        assertEquals(11L, selectedId)
        assertEquals(1, opens)

        assertFalse(
            savedSourcePickerKeyEvent(
                sources = sources,
                activeSourceId = 10L,
                key = Key.DirectionCenter,
                type = KeyEventType.KeyUp,
                onOpen = { opens += 1 },
                onSelected = { selectedId = it.id },
            ),
        )
        assertFalse(
            savedSourcePickerKeyEvent(
                sources = sources,
                activeSourceId = 10L,
                key = Key.DirectionUp,
                type = KeyEventType.KeyDown,
                onOpen = { opens += 1 },
                onSelected = { selectedId = it.id },
            ),
        )
        assertEquals(1, opens)
        assertEquals(11L, selectedId)
    }

    @Test
    fun `source management action focus stays within visible button groups`() {
        assertEquals(
            LibrarySourceAction.Scan,
            librarySourceActionNavigationTarget(LibrarySourceAction.OpenLocal, Key.DirectionRight),
        )
        assertEquals(
            LibrarySourceAction.Search,
            librarySourceActionNavigationTarget(LibrarySourceAction.Scan, Key.DirectionRight),
        )
        assertEquals(
            LibrarySourceAction.ClearIndex,
            librarySourceActionNavigationTarget(LibrarySourceAction.Search, Key.DirectionRight),
        )
        assertEquals(
            LibrarySourceAction.Search,
            librarySourceActionNavigationTarget(LibrarySourceAction.ClearIndex, Key.DirectionLeft),
        )
        assertEquals(
            LibrarySourceAction.RemoveSource,
            librarySourceActionNavigationTarget(LibrarySourceAction.ClearIndex, Key.DirectionDown),
        )
        assertEquals(
            LibrarySourceAction.ClearIndex,
            librarySourceActionNavigationTarget(LibrarySourceAction.RemoveSource, Key.DirectionUp),
        )
        assertNull(librarySourceActionNavigationTarget(LibrarySourceAction.OpenLocal, Key.DirectionLeft))
        assertNull(librarySourceActionNavigationTarget(LibrarySourceAction.RemoveSource, Key.DirectionRight))
        assertNull(librarySourceActionNavigationTarget(LibrarySourceAction.RemoveSource, Key.DirectionDown))
    }

    @Test
    fun `source management action navigation also accepts shared direction intents`() {
        assertEquals(
            LibrarySourceAction.Scan,
            librarySourceActionNavigationTarget(
                LibrarySourceAction.OpenLocal,
                MiruPlayInputIntent.DirectionRight,
            ),
        )
        assertEquals(
            LibrarySourceAction.Search,
            librarySourceActionNavigationTarget(
                LibrarySourceAction.ClearIndex,
                MiruPlayInputIntent.DirectionLeft,
            ),
        )
        assertEquals(
            LibrarySourceAction.RemoveSource,
            librarySourceActionNavigationTarget(
                LibrarySourceAction.ClearIndex,
                MiruPlayInputIntent.DirectionDown,
            ),
        )
        assertEquals(
            LibrarySourceAction.ClearIndex,
            librarySourceActionNavigationTarget(
                LibrarySourceAction.RemoveSource,
                MiruPlayInputIntent.DirectionUp,
            ),
        )
        assertNull(
            librarySourceActionNavigationTarget(
                LibrarySourceAction.OpenLocal,
                MiruPlayInputIntent.DirectionLeft,
            ),
        )
        assertNull(
            librarySourceActionNavigationTarget(
                LibrarySourceAction.RemoveSource,
                MiruPlayInputIntent.Activate,
            ),
        )
    }

    @Test
    fun `source management fields bridge into action rows`() {
        assertEquals(
            LibrarySourceFocusTarget.Field(LibrarySourceField.IndexQuery),
            librarySourceFieldFocusTarget(LibrarySourceField.LocalRoot, Key.DirectionDown),
        )
        assertEquals(
            LibrarySourceFocusTarget.Field(LibrarySourceField.LocalRoot),
            librarySourceFieldFocusTarget(LibrarySourceField.IndexQuery, Key.DirectionUp),
        )
        assertEquals(
            LibrarySourceFocusTarget.PreviousPanel,
            librarySourceFieldFocusTarget(LibrarySourceField.LocalRoot, Key.DirectionUp),
        )
        assertEquals(
            LibrarySourceFocusTarget.Action(LibrarySourceAction.OpenLocal),
            librarySourceFieldFocusTarget(LibrarySourceField.IndexQuery, Key.DirectionRight),
        )
        assertEquals(
            LibrarySourceFocusTarget.Field(LibrarySourceField.IndexQuery),
            librarySourceActionFocusTarget(LibrarySourceAction.OpenLocal, Key.DirectionLeft),
        )
        assertEquals(
            LibrarySourceFocusTarget.Action(LibrarySourceAction.Scan),
            librarySourceActionFocusTarget(LibrarySourceAction.OpenLocal, Key.DirectionRight),
        )
        assertNull(librarySourceFieldFocusTarget(LibrarySourceField.IndexQuery, Key.DirectionLeft))
        assertNull(librarySourceFieldFocusTarget(LibrarySourceField.IndexQuery, Key.DirectionDown))
        assertNull(librarySourceActionFocusTarget(LibrarySourceAction.RemoveSource, Key.DirectionDown))
    }

    @Test
    fun `source management focus also accepts shared direction intents`() {
        assertEquals(
            LibrarySourceFocusTarget.Field(LibrarySourceField.IndexQuery),
            librarySourceFieldFocusTarget(LibrarySourceField.LocalRoot, MiruPlayInputIntent.DirectionDown),
        )
        assertEquals(
            LibrarySourceFocusTarget.PreviousPanel,
            librarySourceFieldFocusTarget(LibrarySourceField.LocalRoot, MiruPlayInputIntent.DirectionUp),
        )
        assertEquals(
            LibrarySourceFocusTarget.Action(LibrarySourceAction.OpenLocal),
            librarySourceFieldFocusTarget(LibrarySourceField.IndexQuery, MiruPlayInputIntent.DirectionRight),
        )
        assertEquals(
            LibrarySourceFocusTarget.Field(LibrarySourceField.IndexQuery),
            librarySourceActionFocusTarget(LibrarySourceAction.OpenLocal, MiruPlayInputIntent.DirectionLeft),
        )
        assertEquals(
            LibrarySourceFocusTarget.EmptyMedia,
            librarySourceActionFocusTarget(
                current = LibrarySourceAction.Search,
                intent = MiruPlayInputIntent.DirectionDown,
                hasEmptyMedia = true,
            ),
        )
        assertEquals(
            LibrarySourceFocusTarget.Field(LibrarySourceField.LocalRoot),
            libraryEmptyMediaFocusTarget(MiruPlayInputIntent.DirectionUp),
        )
        assertNull(librarySourceFieldFocusTarget(LibrarySourceField.IndexQuery, MiruPlayInputIntent.Activate))
        assertNull(libraryEmptyMediaFocusTarget(MiruPlayInputIntent.DirectionDown))
    }

    @Test
    fun `source management can bridge into empty media state`() {
        assertEquals(
            LibrarySourceFocusTarget.EmptyMedia,
            librarySourceFieldFocusTarget(
                LibrarySourceField.IndexQuery,
                Key.DirectionDown,
                hasEmptyMedia = true,
            ),
        )
        assertEquals(
            LibrarySourceFocusTarget.EmptyMedia,
            librarySourceActionFocusTarget(
                LibrarySourceAction.Search,
                Key.DirectionDown,
                hasEmptyMedia = true,
            ),
        )
        assertEquals(
            LibrarySourceFocusTarget.Action(LibrarySourceAction.RemoveSource),
            librarySourceActionFocusTarget(
                LibrarySourceAction.ClearIndex,
                Key.DirectionDown,
                hasEmptyMedia = true,
            ),
        )
        assertEquals(
            LibrarySourceFocusTarget.EmptyMedia,
            librarySourceActionFocusTarget(
                LibrarySourceAction.RemoveSource,
                Key.DirectionDown,
                hasEmptyMedia = true,
            ),
        )
        assertEquals(
            LibrarySourceFocusTarget.Field(LibrarySourceField.LocalRoot),
            libraryEmptyMediaFocusTarget(Key.DirectionUp),
        )
        assertNull(libraryEmptyMediaFocusTarget(Key.DirectionDown))
    }

    @Test
    fun `remote source action focus follows editor card layout`() {
        assertEquals(
            RemoteSourceAction.OpenSmb,
            remoteSourceActionNavigationTarget(RemoteSourceAction.OpenWebDav, Key.DirectionDown),
        )
        assertEquals(
            RemoteSourceAction.ScanSource,
            remoteSourceActionNavigationTarget(RemoteSourceAction.OpenSmb, Key.DirectionRight),
        )
        assertEquals(
            RemoteSourceAction.OpenSmb,
            remoteSourceActionNavigationTarget(RemoteSourceAction.ScanSource, Key.DirectionLeft),
        )
        assertEquals(
            RemoteSourceAction.OpenWebDav,
            remoteSourceActionNavigationTarget(RemoteSourceAction.ScanSource, Key.DirectionUp),
        )
        assertNull(remoteSourceActionNavigationTarget(RemoteSourceAction.OpenWebDav, Key.DirectionUp))
        assertNull(remoteSourceActionNavigationTarget(RemoteSourceAction.ScanSource, Key.DirectionDown))
        assertNull(remoteSourceActionNavigationTarget(RemoteSourceAction.OpenSmb, Key.DirectionLeft))
    }

    @Test
    fun `remote source action navigation also accepts shared direction intents`() {
        assertEquals(
            RemoteSourceAction.OpenSmb,
            remoteSourceActionNavigationTarget(
                RemoteSourceAction.OpenWebDav,
                MiruPlayInputIntent.DirectionDown,
            ),
        )
        assertEquals(
            RemoteSourceAction.ScanSource,
            remoteSourceActionNavigationTarget(
                RemoteSourceAction.OpenSmb,
                MiruPlayInputIntent.DirectionRight,
            ),
        )
        assertEquals(
            RemoteSourceAction.OpenSmb,
            remoteSourceActionNavigationTarget(
                RemoteSourceAction.ScanSource,
                MiruPlayInputIntent.DirectionLeft,
            ),
        )
        assertEquals(
            RemoteSourceAction.OpenWebDav,
            remoteSourceActionNavigationTarget(
                RemoteSourceAction.ScanSource,
                MiruPlayInputIntent.DirectionUp,
            ),
        )
        assertNull(
            remoteSourceActionNavigationTarget(
                RemoteSourceAction.OpenWebDav,
                MiruPlayInputIntent.DirectionUp,
            ),
        )
        assertNull(
            remoteSourceActionNavigationTarget(
                RemoteSourceAction.OpenSmb,
                MiruPlayInputIntent.Activate,
            ),
        )
    }

    @Test
    fun `remote source editor can bridge focus into browser column`() {
        assertEquals(
            RemoteSourceFocusTarget.NextPanel,
            remoteSourceFieldFocusTarget(RemoteSourceField.WebDavUrl, Key.DirectionRight),
        )
        assertEquals(
            RemoteSourceFocusTarget.NextPanel,
            remoteSourceFieldFocusTarget(RemoteSourceField.WebDavPassword, Key.DirectionRight),
        )
        assertEquals(
            RemoteSourceFocusTarget.NextPanel,
            remoteSourceFieldFocusTarget(RemoteSourceField.SmbUrl, Key.DirectionRight),
        )
        assertEquals(
            RemoteSourceFocusTarget.NextPanel,
            remoteSourceFieldFocusTarget(RemoteSourceField.SmbPassword, Key.DirectionRight),
        )
        assertEquals(
            RemoteSourceFocusTarget.NextPanel,
            remoteSourceActionFocusTarget(RemoteSourceAction.OpenWebDav, Key.DirectionRight),
        )
        assertEquals(
            RemoteSourceFocusTarget.NextPanel,
            remoteSourceActionFocusTarget(RemoteSourceAction.ScanSource, Key.DirectionRight),
        )
        assertEquals(
            RemoteSourceFocusTarget.Action(RemoteSourceAction.ScanSource),
            remoteSourceActionFocusTarget(RemoteSourceAction.OpenSmb, Key.DirectionRight),
        )
    }

    @Test
    fun `remote source fields bridge into editor actions`() {
        assertEquals(
            RemoteSourceFocusTarget.Field(RemoteSourceField.WebDavUsername),
            remoteSourceFieldFocusTarget(RemoteSourceField.WebDavUrl, Key.DirectionDown),
        )
        assertEquals(
            RemoteSourceFocusTarget.Field(RemoteSourceField.WebDavPassword),
            remoteSourceFieldFocusTarget(RemoteSourceField.WebDavUsername, Key.DirectionRight),
        )
        assertEquals(
            RemoteSourceFocusTarget.Action(RemoteSourceAction.OpenWebDav),
            remoteSourceFieldFocusTarget(RemoteSourceField.WebDavPassword, Key.DirectionDown),
        )
        assertEquals(
            RemoteSourceFocusTarget.Field(RemoteSourceField.WebDavPassword),
            remoteSourceActionFocusTarget(RemoteSourceAction.OpenWebDav, Key.DirectionUp),
        )
        assertEquals(
            RemoteSourceFocusTarget.Field(RemoteSourceField.SmbDomain),
            remoteSourceFieldFocusTarget(RemoteSourceField.SmbUrl, Key.DirectionDown),
        )
        assertEquals(
            RemoteSourceFocusTarget.Field(RemoteSourceField.SmbPassword),
            remoteSourceFieldFocusTarget(RemoteSourceField.SmbUsername, Key.DirectionRight),
        )
        assertEquals(
            RemoteSourceFocusTarget.Action(RemoteSourceAction.ScanSource),
            remoteSourceFieldFocusTarget(RemoteSourceField.SmbPassword, Key.DirectionDown),
        )
        assertEquals(
            RemoteSourceFocusTarget.Field(RemoteSourceField.SmbDomain),
            remoteSourceActionFocusTarget(RemoteSourceAction.OpenSmb, Key.DirectionUp),
        )
        assertEquals(
            RemoteSourceFocusTarget.Action(RemoteSourceAction.ScanSource),
            remoteSourceActionFocusTarget(RemoteSourceAction.OpenSmb, Key.DirectionRight),
        )
        assertNull(remoteSourceFieldFocusTarget(RemoteSourceField.WebDavUrl, Key.DirectionUp))
        assertEquals(
            RemoteSourceFocusTarget.NextPanel,
            remoteSourceFieldFocusTarget(RemoteSourceField.SmbPassword, Key.DirectionRight),
        )
    }

    @Test
    fun `remote source focus also accepts shared direction intents`() {
        assertEquals(
            RemoteSourceFocusTarget.NextPanel,
            remoteSourceFieldFocusTarget(RemoteSourceField.WebDavUrl, MiruPlayInputIntent.DirectionRight),
        )
        assertEquals(
            RemoteSourceFocusTarget.Field(RemoteSourceField.WebDavUsername),
            remoteSourceFieldFocusTarget(RemoteSourceField.WebDavUrl, MiruPlayInputIntent.DirectionDown),
        )
        assertEquals(
            RemoteSourceFocusTarget.Field(RemoteSourceField.WebDavPassword),
            remoteSourceFieldFocusTarget(RemoteSourceField.WebDavUsername, MiruPlayInputIntent.DirectionRight),
        )
        assertEquals(
            RemoteSourceFocusTarget.Action(RemoteSourceAction.OpenWebDav),
            remoteSourceFieldFocusTarget(RemoteSourceField.WebDavPassword, MiruPlayInputIntent.DirectionDown),
        )
        assertEquals(
            RemoteSourceFocusTarget.NextPanel,
            remoteSourceActionFocusTarget(RemoteSourceAction.ScanSource, MiruPlayInputIntent.DirectionRight),
        )
        assertEquals(
            RemoteSourceFocusTarget.Field(RemoteSourceField.SmbDomain),
            remoteSourceActionFocusTarget(RemoteSourceAction.OpenSmb, MiruPlayInputIntent.DirectionUp),
        )
        assertEquals(
            RemoteSourceFocusTarget.Action(RemoteSourceAction.ScanSource),
            remoteSourceActionFocusTarget(RemoteSourceAction.OpenSmb, MiruPlayInputIntent.DirectionRight),
        )
        assertNull(remoteSourceFieldFocusTarget(RemoteSourceField.WebDavUrl, MiruPlayInputIntent.Activate))
        assertNull(remoteSourceActionFocusTarget(RemoteSourceAction.OpenSmb, MiruPlayInputIntent.DirectionLeft))
    }

    @Test
    fun `remote source preview falls back and compacts endpoints`() {
        assertEquals("填写 SMB 共享地址", remoteSourcePreview("", fallback = "填写 SMB 共享地址", maxLength = 20))

        val preview = remoteSourcePreview(
            value = "smb://smb.ynz.local/share/temporary/test/very/deep/folder/with/long/name",
            fallback = "fallback",
            maxLength = 42,
        )

        assertTrue(preview.length <= 42)
        assertTrue(preview.startsWith("smb://smb.ynz"))
        assertTrue(preview.endsWith("/long/name"))
        assertTrue(preview.contains("..."))
    }

    @Test
    fun `remote source editor chrome uses shared source type labels`() {
        assertEquals("WebDAV", MediaSourceType.WEBDAV.tvLabel())
        assertEquals("DAV", MediaSourceType.WEBDAV.tvBadgeLabel())
        assertEquals("SMB", MediaSourceType.SMB.tvLabel())
        assertEquals("SMB", MediaSourceType.SMB.tvBadgeLabel())
    }

    @Test
    fun `remote browser path preview keeps root readable`() {
        assertEquals("/", remoteBrowserPathPreview("", maxLength = 20))

        val preview = remoteBrowserPathPreview(
            path = "/Fixture WebDAV/Season 01/Subfolder With A Very Long Name/Episode.mkv",
            maxLength = 36,
        )

        assertTrue(preview.length <= 36)
        assertTrue(preview.startsWith("/Fixture"))
        assertTrue(preview.endsWith("Episode.mkv"))
        assertTrue(preview.contains("..."))
    }

    @Test
    fun `remote browser first row up key maps to parent navigation`() {
        assertTrue(remoteBrowserShouldNavigateUp(currentIndex = 0, key = Key.DirectionUp))
        assertFalse(remoteBrowserShouldNavigateUp(currentIndex = 1, key = Key.DirectionUp))
        assertFalse(remoteBrowserShouldNavigateUp(currentIndex = 0, key = Key.DirectionDown))
    }

    @Test
    fun `remote browser rows can return focus to editor column`() {
        val entries = listOf(
            FileEntry(path = "/Season 01", name = "Season 01", isDirectory = true),
            FileEntry(path = "/Season 01/Episode 01.mkv", name = "Episode 01.mkv", isDirectory = false),
        )

        assertEquals(RemoteBrowserFocusTarget.PreviousPanel, entries.remoteBrowserFocusTarget(0, Key.DirectionLeft))
        assertEquals(RemoteBrowserFocusTarget.Row(1), entries.remoteBrowserFocusTarget(0, Key.DirectionDown))
        assertEquals(RemoteBrowserFocusTarget.Row(0), entries.remoteBrowserFocusTarget(1, Key.DirectionUp))
        assertEquals(RemoteBrowserFocusTarget.Row(8), (0 until 10).map {
            FileEntry(path = "/Item $it", name = "Item $it", isDirectory = true)
        }.remoteBrowserFocusTarget(7, Key.DirectionDown))
        assertEquals(RemoteBrowserFocusTarget.Row(7), (0 until 10).map {
            FileEntry(path = "/Item $it", name = "Item $it", isDirectory = true)
        }.remoteBrowserFocusTarget(8, Key.DirectionUp))
        assertNull(entries.remoteBrowserFocusTarget(0, Key.DirectionUp))
        assertNull(entries.remoteBrowserFocusTarget(1, Key.DirectionDown))
    }

    @Test
    fun `remote browser focus also accepts shared direction intents`() {
        val entries = listOf(
            FileEntry(path = "/Season 01", name = "Season 01", isDirectory = true),
            FileEntry(path = "/Season 01/Episode 01.mkv", name = "Episode 01.mkv", isDirectory = false),
        )

        assertEquals(RemoteBrowserFocusTarget.PreviousPanel, entries.remoteBrowserFocusTarget(0, MiruPlayInputIntent.DirectionLeft))
        assertEquals(RemoteBrowserFocusTarget.Row(1), entries.remoteBrowserFocusTarget(0, MiruPlayInputIntent.DirectionDown))
        assertEquals(RemoteBrowserFocusTarget.Row(0), entries.remoteBrowserFocusTarget(1, MiruPlayInputIntent.DirectionUp))
        assertNull(entries.remoteBrowserFocusTarget(0, MiruPlayInputIntent.DirectionUp))
        assertNull(entries.remoteBrowserFocusTarget(0, MiruPlayInputIntent.Activate))
        assertTrue(remoteBrowserShouldNavigateUp(currentIndex = 0, intent = MiruPlayInputIntent.DirectionUp))
        assertFalse(remoteBrowserShouldNavigateUp(currentIndex = 1, intent = MiruPlayInputIntent.DirectionUp))
    }

    @Test
    fun `remote browser page helpers keep every remote item reachable`() {
        assertEquals(0, remoteBrowserPageStartForIndex(index = 0, itemCount = 17))
        assertEquals(0, remoteBrowserPageStartForIndex(index = 7, itemCount = 17))
        assertEquals(8, remoteBrowserPageStartForIndex(index = 8, itemCount = 17))
        assertEquals(16, remoteBrowserPageStartForIndex(index = 16, itemCount = 17))
        assertEquals(16, remoteBrowserPageStartForIndex(index = 30, itemCount = 17))
        assertEquals(8, remoteBrowserCoercedPageStart(pageStart = 12, itemCount = 17))
        assertEquals(16, remoteBrowserCoercedPageStart(pageStart = 40, itemCount = 17))
        assertEquals(0, remoteBrowserCoercedPageStart(pageStart = -8, itemCount = 17))
        assertEquals(
            "显示 9-16 / 17 ${mediaSourceRemoteBrowserPageUnitLabel()}，按上/下继续翻页。",
            remoteBrowserPageSummary(pageStart = 8, visibleCount = 8, itemCount = 17),
        )
        assertEquals(
            "显示 17-17 / 17 ${mediaSourceRemoteBrowserPageUnitLabel()}，按上/下继续翻页。",
            remoteBrowserPageSummary(pageStart = 16, visibleCount = 1, itemCount = 17),
        )
        assertNull(remoteBrowserPageSummary(pageStart = 0, visibleCount = 4, itemCount = 4))
    }

    @Test
    fun `remote browser up button can enter rows or empty state`() {
        assertEquals(
            RemoteBrowserFocusTarget.Row(0),
            remoteBrowserUpButtonFocusTarget(itemCount = 3, key = Key.DirectionDown),
        )
        assertEquals(
            RemoteBrowserFocusTarget.EmptyState,
            remoteBrowserUpButtonFocusTarget(itemCount = 0, key = Key.DirectionDown),
        )
        assertEquals(
            RemoteBrowserFocusTarget.PreviousPanel,
            remoteBrowserUpButtonFocusTarget(itemCount = 0, key = Key.DirectionLeft),
        )
        assertNull(remoteBrowserUpButtonFocusTarget(itemCount = 0, key = Key.DirectionUp))
    }

    @Test
    fun `remote browser up button and empty state also accept shared direction intents`() {
        assertEquals(
            RemoteBrowserFocusTarget.Row(0),
            remoteBrowserUpButtonFocusTarget(itemCount = 3, intent = MiruPlayInputIntent.DirectionDown),
        )
        assertEquals(
            RemoteBrowserFocusTarget.EmptyState,
            remoteBrowserUpButtonFocusTarget(itemCount = 0, intent = MiruPlayInputIntent.DirectionDown),
        )
        assertEquals(
            RemoteBrowserFocusTarget.PreviousPanel,
            remoteBrowserUpButtonFocusTarget(itemCount = 3, intent = MiruPlayInputIntent.DirectionLeft),
        )
        assertEquals(RemoteBrowserFocusTarget.UpButton, remoteBrowserEmptyFocusTarget(MiruPlayInputIntent.DirectionUp))
        assertEquals(RemoteBrowserFocusTarget.PreviousPanel, remoteBrowserEmptyFocusTarget(MiruPlayInputIntent.DirectionLeft))
        assertNull(remoteBrowserUpButtonFocusTarget(itemCount = 3, intent = MiruPlayInputIntent.DirectionUp))
        assertNull(remoteBrowserEmptyFocusTarget(MiruPlayInputIntent.DirectionDown))
    }

    @Test
    fun `remote browser empty state returns to up button or editor`() {
        assertEquals(RemoteBrowserFocusTarget.UpButton, remoteBrowserEmptyFocusTarget(Key.DirectionUp))
        assertEquals(RemoteBrowserFocusTarget.PreviousPanel, remoteBrowserEmptyFocusTarget(Key.DirectionLeft))
        assertNull(remoteBrowserEmptyFocusTarget(Key.DirectionDown))
        assertNull(remoteBrowserEmptyFocusTarget(Key.DirectionRight))
    }
}
