package com.miruplay.tv.player

import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IjkPlaybackAndroidIoTest {
    @Test
    fun `bridge reopens Media3 data source for absolute seeks`() {
        val bytes = "0123456789".encodeToByteArray()
        val bridge = IjkPlaybackAndroidIo(DataSource.Factory { ByteArrayDataSource(bytes) })
        val buffer = ByteArray(4)

        assertEquals(0, bridge.open("memory://episode"))
        assertEquals(10L, bridge.seek(0L, AVSEEK_SIZE))
        assertEquals(4, bridge.read(buffer, buffer.size))
        assertArrayEquals("0123".encodeToByteArray(), buffer)
        assertEquals(7L, bridge.seek(7L, SEEK_SET))
        assertEquals(3, bridge.read(buffer, buffer.size))
        assertArrayEquals("7893".encodeToByteArray(), buffer)
        assertEquals(0, bridge.close())
    }

    private companion object {
        const val SEEK_SET = 0
        const val AVSEEK_SIZE = 0x10000
    }
}
