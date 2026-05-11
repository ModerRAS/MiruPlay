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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.miruplay.tv.ui.theme.AccentBlue
import com.miruplay.tv.ui.theme.CardBg
import com.miruplay.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.net.URL

private object RemoteImageCache {
    private val cache = LruCache<String, ImageBitmap>(80)

    fun get(url: String): ImageBitmap? = cache.get(url)

    private fun put(url: String, bitmap: ImageBitmap) {
        cache.put(url, bitmap)
    }

    fun load(context: Context, url: String): ImageBitmap? {
        get(url)?.let { return it }

        val file = cacheFile(context, url)
        decode(file)?.let { bitmap ->
            put(url, bitmap)
            return bitmap
        }

        return runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            val connection = URL(url).openConnection().apply {
                connectTimeout = 10_000
                readTimeout = 20_000
            }
            connection.getInputStream().use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            decode(file)
        }.getOrNull()?.also { put(url, it) }
    }

    private fun decode(file: File): ImageBitmap? {
        if (!file.exists() || file.length() <= 0L) return null
        return BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    }

    private fun cacheFile(context: Context, url: String): File {
        val key = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "miruplay_image_cache"), key)
    }
}

@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = { ImagePlaceholder() }
) {
    val context = LocalContext.current.applicationContext
    val bitmapState = produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        val target = url?.takeIf { it.isNotBlank() } ?: return@produceState
        value = RemoteImageCache.get(target) ?: withContext(Dispatchers.IO) {
            RemoteImageCache.load(context, target)
        }
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
