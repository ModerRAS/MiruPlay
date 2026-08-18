package com.miruplay.tv.translation

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.Result.Companion.failure
import com.miruplay.tv.core.common.Result.Companion.success
import com.miruplay.tv.model.SubtitleFormat
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.player.PlaybackDataSourceFactory
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.scraper.core.BangumiProxyAwareOkHttpClient
import com.miruplay.tv.scraper.core.toBangumiHttpProxyConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleTranslationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: TranslationPreferencesRepository,
    private val cloudDriveRepository: CloudDriveAutomationRepository,
    private val dataSourceFactory: PlaybackDataSourceFactory,
    private val proxyClient: BangumiProxyAwareOkHttpClient,
) {
    private val googleTranslator = GoogleTranslator(proxyClient)
    private val bingTranslator = BingTranslator(proxyClient)
    private val deepSeekTranslator = DeepSeekTranslator(proxyClient)

    /** 复用用户 RSS 自动化里的代理配置；取不到（未配置/失败）时保持 NO_PROXY。 */
    suspend fun translateTrack(
        track: SubtitleTrack,
        targetLanguageCode: String,
        provider: TranslationProvider,
    ): Result<SubtitleTrack> {
        try {
            applyProxyFromCloudDriveConfig()

            if (provider == TranslationProvider.DEEPSEEK && preferences.deepSeekApiKey.isBlank()) {
                return failure(
                    AppError.ScrapingError.ApiError(
                        source = "DeepSeek",
                        message = "请先在 WebControl 设置 DeepSeek API Key",
                    ),
                )
            }

            val sourceText = readSubtitleText(track)
            val cues = SubtitleFileParser.parse(sourceText)
            if (cues.isEmpty()) {
                return failure(
                    AppError.ScrapingError.ApiError(
                        source = provider.id,
                        message = "字幕文件中没有可翻译的内容",
                    ),
                )
            }

            val translatedCues = ArrayList<SubtitleCue>(cues.size)
            cues.forEachIndexed { index, cue ->
                if (index > 0 && provider != TranslationProvider.DEEPSEEK) delay(CUE_DELAY_MILLIS)
                val translatedText = when (provider) {
                    TranslationProvider.GOOGLE -> googleTranslator.translate(cue.text, googleLanguageCode(targetLanguageCode))
                    TranslationProvider.BING -> bingTranslator.translate(cue.text, bingLanguageCode(targetLanguageCode))
                    TranslationProvider.DEEPSEEK -> deepSeekTranslator.translate(
                        cue.text,
                        preferences.deepSeekApiKey,
                        deepSeekLanguageInstruction(targetLanguageCode),
                    )
                }
                translatedCues += cue.copy(text = translatedText)
            }

            val srtText = SubtitleFileParser.toSrt(translatedCues)
            val outputFile = writeCacheSrt(track, targetLanguageCode, srtText)
            val displayName = SUPPORTED_TARGET_LANGUAGES
                .firstOrNull { it.code == targetLanguageCode }
                ?.displayName ?: targetLanguageCode
            return success(
                SubtitleTrack(
                    language = targetLanguageCode,
                    title = "翻译: $displayName (${provider.id})",
                    isExternal = true,
                    path = outputFile.absolutePath,
                    format = SubtitleFormat.SRT,
                ),
            )
        } catch (error: Throwable) {
            return failure(
                AppError.ScrapingError.ApiError(
                    source = provider.id,
                    message = error.message ?: "翻译失败",
                ),
            )
        }
    }

    private suspend fun applyProxyFromCloudDriveConfig() {
        val config = runCatching { cloudDriveRepository.getConfig().getOrNull() }.getOrNull()
        if (config != null) {
            proxyClient.configureProxy(config.toBangumiHttpProxyConfig())
        }
        // 拿不到配置时保持初始 NO_PROXY（BangumiProxyAwareOkHttpClient enabled=false 即直连）
    }

    /** 本地绝对路径 / file:// / content:// / http(s) 远程（WebDAV 走播放器的数据源通道）。 */
    private suspend fun readSubtitleText(track: SubtitleTrack): String = withContext(Dispatchers.IO) {
        val path = track.path
        when {
            path.startsWith("content://") -> {
                context.contentResolver.openInputStream(Uri.parse(path))
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw IllegalStateException("无法打开字幕：$path")
            }
            path.startsWith("file://") -> {
                File(Uri.parse(path).path ?: throw IllegalStateException("无效的文件路径：$path")).readText(Charsets.UTF_8)
            }
            path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true) -> {
                readRemote(path)
            }
            else -> File(path).readText(Charsets.UTF_8)
        }
    }

    private suspend fun readRemote(url: String): String = withContext(Dispatchers.IO) {
        readRemoteBlocking(url)
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun readRemoteBlocking(url: String): String {
        val dataSource = dataSourceFactory.createDataSource()
        try {
            dataSource.open(DataSpec(Uri.parse(url)))
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            while (true) {
                val read = dataSource.read(chunk, 0, chunk.size)
                if (read == -1) break
                if (read > 0) buffer.write(chunk, 0, read)
            }
            return buffer.toString(Charsets.UTF_8.name())
        } finally {
            dataSource.close()
        }
    }

    private fun writeCacheSrt(track: SubtitleTrack, targetLanguageCode: String, srtText: String): File {
        val dir = File(context.cacheDir, "translated_subtitles").apply { mkdirs() }
        val stem = track.title
            .substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._\\-]"), "_")
            .take(48)
            .ifBlank { "subtitle" }
        val file = File(dir, "${stem}_${targetLanguageCode}_${System.currentTimeMillis()}.srt")
        file.writeText(srtText, Charsets.UTF_8)
        return file
    }

    private companion object {
        // 免费端点每条之间小延迟，防限流
        const val CUE_DELAY_MILLIS = 150L
    }
}
