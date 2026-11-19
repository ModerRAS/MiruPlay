package com.miruplay.tv.data.schedule

import android.content.Context
import com.miruplay.tv.model.ScheduleDataSourceUrl
import com.miruplay.tv.model.ScheduleFetchError
import com.miruplay.tv.model.ScheduleSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 加载结果：UI 据此区分 未配置 / 加载中 / 无数据(404) / 失败 / 成功。 */
sealed interface ScheduleLoadResult {
    data class Success(
        val snapshot: ScheduleSnapshot,
        /** true 表示来自内存/磁盘缓存（未发网络请求）。 */
        val fromCache: Boolean,
        /** true 表示缓存已过期、网络又失败，展示的是旧数据；[error] 是失败原因。 */
        val stale: Boolean,
        val error: ScheduleFetchError? = null,
    ) : ScheduleLoadResult

    data object NotConfigured : ScheduleLoadResult
    data object Empty : ScheduleLoadResult
    data class Failure(val error: ScheduleFetchError) : ScheduleLoadResult
}

@Serializable
internal data class CachedSchedule(
    val fetchedAtMillis: Long,
    val snapshot: ScheduleSnapshot,
)

/**
 * 时间表仓库：内存 + 磁盘两级缓存（TTL 1 小时），网络失败时回退到旧缓存（stale）。
 *
 * - 缓存文件在 app cacheDir 下，随 `clearCache` 或系统清理缓存消失；
 * - 404 视为「服务端尚无数据」：清掉缓存并返回 [ScheduleLoadResult.Empty]；
 * - 所有 load 调用串行化，避免并发刷新重复打网络。
 */
@Singleton
class ScheduleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: AirRadarScheduleClient,
    private val preferences: ScheduleDataSourcePreferences,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val loadMutex = Mutex()

    @Volatile
    private var memoryCache: CachedSchedule? = null

    private val cacheDirectory: File by lazy { File(context.cacheDir, CACHE_DIRECTORY_NAME) }
    private val cacheFile: File by lazy { File(cacheDirectory, CACHE_FILE_NAME) }

    fun configuredUrlOrNull(): String? = preferences.configuredUrlOrNull()

    fun rawConfiguredUrl(): String = preferences.rawDataSourceUrl

    /** 保存数据源地址；非法地址返回 false（不覆盖旧值）。 */
    fun saveDataSourceUrl(rawUrl: String?): Boolean = preferences.save(rawUrl)

    fun clearDataSourceUrl() {
        preferences.clear()
        clearCache()
    }

    /** 当前是否有可展示的缓存（用于设置页状态摘要，不触发网络）。 */
    fun hasCachedSnapshot(): Boolean = memoryCache != null || cacheFile.exists()

    suspend fun load(
        forceRefresh: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
    ): ScheduleLoadResult = loadMutex.withLock {
        val baseUrl = configuredUrlOrNull()
            ?: return@withLock ScheduleLoadResult.NotConfigured

        if (!forceRefresh) {
            val cached = freshCacheOrNull(nowMillis)
            if (cached != null) {
                return@withLock ScheduleLoadResult.Success(
                    snapshot = cached.snapshot,
                    fromCache = true,
                    stale = false,
                )
            }
        }

        return@withLock when (val result = client.fetchLatest(baseUrl)) {
            is ScheduleFetchResult.Success -> {
                val cached = CachedSchedule(fetchedAtMillis = nowMillis, snapshot = result.snapshot)
                memoryCache = cached
                writeDiskCache(cached)
                ScheduleLoadResult.Success(
                    snapshot = result.snapshot,
                    fromCache = false,
                    stale = false,
                )
            }

            ScheduleFetchResult.NoData -> {
                clearCache()
                ScheduleLoadResult.Empty
            }

            is ScheduleFetchResult.Failure -> {
                // 网络/解析失败时尽量给用户看上次成功的数据，而不是空白页。
                val stale = memoryCache ?: readDiskCache()
                if (stale != null) {
                    ScheduleLoadResult.Success(
                        snapshot = stale.snapshot,
                        fromCache = true,
                        stale = true,
                        error = result.error,
                    )
                } else {
                    ScheduleLoadResult.Failure(result.error)
                }
            }
        }
    }

    fun clearCache() {
        memoryCache = null
        runCatching { cacheFile.delete() }
    }

    private suspend fun freshCacheOrNull(nowMillis: Long): CachedSchedule? {
        memoryCache?.takeIf { it.isFresh(nowMillis) }?.let { return it }
        val disk = readDiskCache() ?: return null
        return if (disk.isFresh(nowMillis)) {
            memoryCache = disk
            disk
        } else {
            // 过期缓存留着给网络失败时兜底，但不算 fresh。
            memoryCache = disk
            null
        }
    }

    private suspend fun readDiskCache(): CachedSchedule? = withContext(Dispatchers.IO) {
        val file = cacheFile
        if (!file.exists()) return@withContext null
        val parsed = runCatching {
            json.decodeFromString<CachedSchedule>(file.readText())
        }.getOrNull()
        if (parsed == null) {
            runCatching { file.delete() }
        }
        parsed
    }

    private suspend fun writeDiskCache(cached: CachedSchedule) {
        runCatching {
            withContext(Dispatchers.IO) {
                if (!cacheDirectory.exists()) cacheDirectory.mkdirs()
                val temp = File(cacheDirectory, "$CACHE_FILE_NAME.tmp")
                temp.writeText(json.encodeToString(CachedSchedule.serializer(), cached))
                if (cacheFile.exists()) cacheFile.delete()
                if (!temp.renameTo(cacheFile)) {
                    temp.copyTo(cacheFile, overwrite = true)
                    temp.delete()
                }
            }
        }
    }

    internal fun cacheFileForTest(): File = cacheFile

    companion object {
        const val CACHE_TTL_MILLIS: Long = 60L * 60L * 1000L
        private const val CACHE_DIRECTORY_NAME = "schedule"
        private const val CACHE_FILE_NAME = "latest_schedule.json"
    }
}

internal fun CachedSchedule.isFresh(nowMillis: Long): Boolean =
    nowMillis - fetchedAtMillis in 0 until ScheduleRepository.CACHE_TTL_MILLIS && fetchedAtMillis > 0L

/** 供 UI 层判断配置地址是否可保存。 */
internal fun isValidDataSourceInput(raw: String?): Boolean = ScheduleDataSourceUrl.isValid(raw)
