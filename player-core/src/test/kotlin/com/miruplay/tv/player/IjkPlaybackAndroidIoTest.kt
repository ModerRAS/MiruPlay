package com.miruplay.tv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
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

    @Test
    fun `bridge forwards request headers to every Media3 open`() {
        val source = CapturingDataSource()
        val bridge = IjkPlaybackAndroidIo(
            dataSourceFactory = DataSource.Factory { source },
            requestHeaders = mapOf("X-Test-Header" to "expected"),
        )

        assertEquals(0, bridge.open("https://play.example/episode.mkv"))
        assertEquals("expected", source.openedDataSpec?.httpRequestHeaders?.get("X-Test-Header"))
        assertEquals(0, bridge.close())
    }

    private class CapturingDataSource : DataSource {
        var openedDataSpec: DataSpec? = null

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            openedDataSpec = dataSpec
            return 0L
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C.RESULT_END_OF_INPUT

        override fun getUri(): Uri? = openedDataSpec?.uri

        override fun close() = Unit
    }

    private companion object {
        const val SEEK_SET = 0
        const val AVSEEK_SIZE = 0x10000
    }
}
