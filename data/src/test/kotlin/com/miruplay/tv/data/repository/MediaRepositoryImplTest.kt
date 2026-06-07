package com.miruplay.tv.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.miruplay.tv.data.db.MiruPlayDatabase
import com.miruplay.tv.data.entity.IndexEntryEntity
import com.miruplay.tv.data.secure.MediaSourceSecretStore
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaRepositoryImplTest {
    
    private lateinit var database: MiruPlayDatabase
    private lateinit var repository: MediaRepositoryImpl
    
    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MiruPlayDatabase::class.java
        ).build()
        repository = MediaRepositoryImpl(
            mediaSourceDao = database.mediaSourceDao(),
            indexDao = database.indexDao(),
            secretStore = InMemoryMediaSourceSecretStore()
        )
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun `addSource should return valid id`() = runBlocking {
        val source = MediaSourceInfo(
            name = "Test NAS",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf("url" to "https://nas.local/dav")
        )
        
        val result = repository.addSource(source)
        assertTrue("Expected Success", result.isSuccess())
        val id = result.getOrNull()
        assertNotNull("Id should not be null", id)
        assertTrue("Id should be positive", id!! > 0)
    }
    
    @Test
    fun `getSources should return added source`() = runBlocking {
        val source = MediaSourceInfo(
            name = "Test NAS",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf("url" to "https://nas.local/dav")
        )
        
        repository.addSource(source)
        val sources = repository.getSources().getOrNull()
        assertNotNull("Sources should not be null", sources)
        assertTrue("Should have at least 1 source", sources!!.isNotEmpty())
        assertEquals("Test NAS", sources[0].name)
    }

    @Test
    fun `remote source should restore url without path alias`() = runBlocking {
        val source = MediaSourceInfo(
            name = "WebDAV",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf("url" to "http://example.test/dav")
        )

        val id = repository.addSource(source).getOrNull()!!
        val restored = repository.getSourceById(id).getOrNull()

        assertNotNull("Restored source should exist", restored)
        assertEquals("URL should be preserved", "http://example.test/dav", restored!!.connectionInfo["url"])
        assertNull("Remote source should not synthesize path", restored.connectionInfo["path"])
    }

    @Test
    fun `local source should restore path alias`() = runBlocking {
        val source = MediaSourceInfo(
            name = "Local",
            type = MediaSourceType.LOCAL,
            connectionInfo = mapOf("path" to "/storage/emulated/0/Download")
        )

        val id = repository.addSource(source).getOrNull()!!
        val restored = repository.getSourceById(id).getOrNull()

        assertNotNull("Restored source should exist", restored)
        assertEquals("Path should be preserved", "/storage/emulated/0/Download", restored!!.connectionInfo["path"])
        assertEquals("Local source should still expose url alias", "/storage/emulated/0/Download", restored.connectionInfo["url"])
    }
    
    @Test
    fun `removeSource should cascade delete index entries`() = runBlocking {
        val source = MediaSourceInfo(
            name = "Remove Test",
            type = MediaSourceType.LOCAL,
            connectionInfo = mapOf("url" to "/storage/test")
        )
        
        val id = repository.addSource(source).getOrNull()!!
        
        // Add index entries for this source
        database.indexDao().insertAll(listOf(
            IndexEntryEntity(
                sourceId = id,
                path = "/storage/test/anime/ep01.mkv",
                animeName = "Test Anime"
            )
        ))
        
        // Verify index entry exists
        val beforeCount = database.indexDao().getCount(id)
        assertTrue("Should have index entries before removal", beforeCount > 0)
        
        // Remove source
        val result = repository.removeSource(id)
        assertTrue("Remove should succeed", result.isSuccess())
        
        // Verify index entry is gone
        val afterCount = database.indexDao().getCount(id)
        assertEquals("Index entries should be deleted", 0, afterCount)
    }
    
    @Test
    fun `updateSource should modify connection status`() = runBlocking {
        val source = MediaSourceInfo(
            name = "Update Test",
            type = MediaSourceType.SMB,
            connectionInfo = mapOf("url" to "smb://nas.local/share")
        )
        
        val id = repository.addSource(source).getOrNull()!!
        
        val updated = source.copy(id = id, isConnected = true)
        repository.updateSource(updated)
        
        val retrieved = repository.getSourceById(id).getOrNull()
        assertNotNull("Should retrieve updated source", retrieved)
        assertTrue("IsConnected should be true", retrieved!!.isConnected)
    }

    @Test
    fun `addSource should persist content mode`() = runBlocking {
        val source = MediaSourceInfo(
            name = "Drama NAS",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf("url" to "https://nas.local/drama"),
            contentMode = MediaContentMode.DRAMA
        )

        val id = repository.addSource(source).getOrNull()!!
        val retrieved = repository.getSourceById(id).getOrNull()

        assertNotNull("Should retrieve persisted source", retrieved)
        assertEquals(MediaContentMode.DRAMA, retrieved!!.contentMode)
    }

    @Test
    fun `updateSource should persist last scanned timestamp`() = runBlocking {
        val source = MediaSourceInfo(
            name = "Scan Time Test",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf("url" to "https://nas.local/dav")
        )

        val id = repository.addSource(source).getOrNull()!!
        repository.updateSource(source.copy(id = id, lastScanned = 123_456L))

        val retrieved = repository.getSourceById(id).getOrNull()
        assertNotNull("Should retrieve updated source", retrieved)
        assertEquals(123_456L, retrieved!!.lastScanned)
    }

    @Test
    fun `updateSource should persist content mode changes`() = runBlocking {
        val source = MediaSourceInfo(
            name = "Mode Update Test",
            type = MediaSourceType.LOCAL,
            connectionInfo = mapOf("path" to "/storage/emulated/0/Drama"),
            contentMode = MediaContentMode.ANIME
        )

        val id = repository.addSource(source).getOrNull()!!
        repository.updateSource(source.copy(id = id, contentMode = MediaContentMode.DRAMA))

        val retrieved = repository.getSourceById(id).getOrNull()
        assertNotNull("Should retrieve updated source", retrieved)
        assertEquals(MediaContentMode.DRAMA, retrieved!!.contentMode)
    }

    @Test
    fun `getSourceById should default missing persisted content mode to anime`() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO media_source (
                name,
                type,
                url,
                username,
                password,
                extra_config,
                is_connected,
                last_scanned
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf("Legacy Source", "WEBDAV", "https://legacy.local/dav", null, null, null, 0, 0L)
        )

        val sourceId = database.mediaSourceDao().getAll().single().id
        val retrieved = repository.getSourceById(sourceId).getOrNull()

        assertNotNull("Should retrieve legacy source", retrieved)
        assertEquals(MediaContentMode.ANIME, retrieved!!.contentMode)
    }
}

private class InMemoryMediaSourceSecretStore : MediaSourceSecretStore {
    private val passwords = mutableMapOf<Long, String>()

    override fun getMediaSourcePassword(sourceId: Long): String? = passwords[sourceId]

    override fun setMediaSourcePassword(sourceId: Long, password: String?) {
        if (password.isNullOrBlank()) {
            passwords.remove(sourceId)
        } else {
            passwords[sourceId] = password
        }
    }

    override fun clearMediaSourcePassword(sourceId: Long) {
        passwords.remove(sourceId)
    }
}
