package com.miruplay.tv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.miruplay.tv.player.ijk.android.MiruIjkAndroidIo
import java.io.IOException

@UnstableApi
internal class IjkPlaybackAndroidIo(
    private val dataSourceFactory: DataSource.Factory,
    private val requestHeaders: Map<String, String> = emptyMap(),
) : MiruIjkAndroidIo {
    private var dataSource: DataSource? = null
    private var uri: Uri? = null
    private var position = 0L
    private var totalLength = C.LENGTH_UNSET.toLong()

    @Synchronized
    override fun open(url: String): Int {
        close()
        uri = Uri.parse(url)
        position = 0L
        openAt(position)
        return 0
    }

    @Synchronized
    override fun read(buffer: ByteArray, size: Int): Int {
        val source = dataSource ?: throw IOException("IJK data source is not open")
        val read = source.read(buffer, 0, size.coerceIn(0, buffer.size))
        if (read > 0) position += read
        return read
    }

    @Synchronized
    override fun seek(offset: Long, whence: Int): Long {
        if (whence and AVSEEK_SIZE != 0) return totalLength
        val target = when (whence and SEEK_MODE_MASK) {
            SEEK_SET -> offset
            SEEK_CUR -> position + offset
            SEEK_END -> {
                if (totalLength == C.LENGTH_UNSET.toLong()) return -1L
                totalLength + offset
            }
            else -> return -1L
        }.coerceAtLeast(0L)
        if (target == position) return position
        openAt(target)
        return position
    }

    @Synchronized
    override fun close(): Int {
        dataSource?.close()
        dataSource = null
        uri = null
        position = 0L
        totalLength = C.LENGTH_UNSET.toLong()
        return 0
    }

    private fun openAt(targetPosition: Long) {
        val sourceUri = uri ?: throw IOException("IJK data source URI is missing")
        dataSource?.close()
        val next = dataSourceFactory.createDataSource()
        try {
            val remainingLength = next.open(
                DataSpec.Builder()
                    .setUri(sourceUri)
                    .setPosition(targetPosition)
                    .setHttpRequestHeaders(requestHeaders)
                    .build(),
            )
            dataSource = next
            position = targetPosition
            if (remainingLength != C.LENGTH_UNSET.toLong()) {
                totalLength = targetPosition + remainingLength
            }
        } catch (error: Throwable) {
            runCatching { next.close() }
            throw IOException("Failed to open IJK data source", error)
        }
    }

    private companion object {
        const val SEEK_SET = 0
        const val SEEK_CUR = 1
        const val SEEK_END = 2
        const val SEEK_MODE_MASK = 0xFFFF
        const val AVSEEK_SIZE = 0x10000
    }
}
