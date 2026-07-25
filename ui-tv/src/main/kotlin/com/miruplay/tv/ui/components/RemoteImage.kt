package com.miruplay.tv.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.mediasource.WebDavHttpStatusException
import com.miruplay.tv.mediasource.WebDavRequest
import com.miruplay.tv.mediasource.WebDavRequestCoordinator
import com.miruplay.tv.mediasource.WebDavRequestKind
import com.miruplay.tv.mediasource.WebDavTransportResult
import com.miruplay.tv.ui.theme.AccentBlue
import com.miruplay.tv.ui.theme.CardBg
import com.miruplay.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import kotlin.math.max

private object RemoteImageCache {
    private const val MAX_MEMORY_CACHE_KIB = 24 * 1024
    private const val MAX_DECODED_IMAGE_EDGE = 1_024
    private const val MAX_PARALLEL_IMAGE_LOADS = 4

    private val cache = object : LruCache<String, ImageBitmap>(MAX_MEMORY_CACHE_KIB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return max(1, value.width * value.height * 4 / 1024)
        }
    }
    private val loadSemaphore = Semaphore(MAX_PARALLEL_IMAGE_LOADS)
    private val loggedFailureKeys = Collections.synchronizedSet(mutableSetOf<String>())

    fun get(localPath: String?, url: String?): ImageBitmap? =
        localPath?.takeIf { it.isNotBlank() }?.let { cache.get(localCacheKey(it)) }
            ?: url?.takeIf { it.isNotBlank() }?.let { cache.get(remoteCacheKey(it)) }

    private fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }

    suspend fun load(context: Context, localPath: String?, url: String?): ImageBitmap? = loadSemaphore.withPermit {
        withContext(Dispatchers.IO) {
            loadBlocking(context, localPath, url)
        }
    }

    private fun loadBlocking(context: Context, localPath: String?, url: String?): ImageBitmap? {
        get(localPath, url)?.let { return it }

        localPath?.takeIf { it.isNotBlank() }?.let { path ->
            val key = localCacheKey(path)
            decode(File(path))?.let { bitmap ->
                put(key, bitmap)
                return bitmap
            }
        }

        val remoteUrl = url?.takeIf { it.isNotBlank() } ?: return null
        val remoteKey = remoteCacheKey(remoteUrl)

        val file = cacheFile(context, remoteUrl)
        decode(file)?.let { bitmap ->
            put(remoteKey, bitmap)
            return bitmap
        }

        val failureKey = cacheKey(remoteUrl)
        return runCatching {
            file.parentFile?.mkdirs()
            val temp = File.createTempFile(file.name, ".tmp", file.parentFile)
            val download = {
                val connection = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 10_000
                    readTimeout = 20_000
                }
                try {
                    if (connection.responseCode !in 200..299) {
                        throw WebDavHttpStatusException(connection.responseCode)
                    }
                    connection.getInputStream().use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                } finally {
                    connection.disconnect()
                }
            }
            WebDavRequestCoordinator.execute(
                WebDavRequest(
                    method = "GET",
                    url = remoteUrl,
                    kind = WebDavRequestKind.ARTWORK,
                ),
            ) {
                download()
                WebDavTransportResult(Unit, 200)
            }.close()
            try {
                if (!temp.renameTo(file)) temp.copyTo(file, overwrite = true)
            } finally {
                temp.delete()
            }
            decode(file)
        }.onFailure { error ->
            if (loggedFailureKeys.add(failureKey)) {
                MiruLog.w(
                    "RemoteImage",
                    "Remote image load failed",
                    error,
                    attributes = mapOf("url_hash" to failureKey)
                )
            }
        }.getOrNull()?.also {
            put(remoteKey, it)
            loggedFailureKeys.remove(failureKey)
        }
    }

    private fun decode(file: File): ImageBitmap? {
        if (!file.exists() || file.length() <= 0L) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > MAX_DECODED_IMAGE_EDGE || height / sampleSize > MAX_DECODED_IMAGE_EDGE) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun cacheFile(context: Context, url: String): File {
        return File(File(context.cacheDir, "miruplay_image_cache"), cacheKey(url))
    }

    private fun localCacheKey(path: String): String = "local:$path"

    private fun remoteCacheKey(url: String): String = "remote:$url"

    private fun cacheKey(url: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    localPath: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = { ImagePlaceholder() }
) {
    val context = LocalContext.current.applicationContext
    val bitmapState = produceState<ImageBitmap?>(initialValue = null, key1 = localPath, key2 = url) {
        val localTarget = localPath?.takeIf { it.isNotBlank() }
        val remoteTarget = url?.takeIf { it.isNotBlank() }
        if (localTarget == null && remoteTarget == null) return@produceState
        value = RemoteImageCache.get(localTarget, remoteTarget)
            ?: RemoteImageCache.load(context, localTarget, remoteTarget)
    }

    val bitmap = bitmapState.value
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier) {
            placeholder()
        }
    }
}

@Composable
fun ImagePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(CardBg, AccentBlue.copy(alpha = 0.75f), CardBg)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Image,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.7f),
            modifier = Modifier.padding(18.dp)
        )
    }
}
