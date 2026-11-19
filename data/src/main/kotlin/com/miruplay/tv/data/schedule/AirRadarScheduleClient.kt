package com.miruplay.tv.data.schedule

import com.miruplay.tv.model.ScheduleDataSourceUrl
import com.miruplay.tv.model.ScheduleFetchError
import com.miruplay.tv.model.ScheduleSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed interface ScheduleFetchResult {
    data class Success(val snapshot: ScheduleSnapshot) : ScheduleFetchResult

    /** 404 no_schedule_data：服务端尚未发布任何数据。 */
    data object NoData : ScheduleFetchResult
    data class Failure(val error: ScheduleFetchError) : ScheduleFetchResult
}

/**
 * AirRadar 时间表 HTTP 客户端。
 *
 * - 复用 App 的单例 OkHttp（练连接池），但使用更短的 10s 超时；
 * - 只 GET `{baseUrl}/api/schedule/latest`，地址来自用户运行时配置；
 * - 404 视为「尚无数据」，其它非 2xx 视为可重试错误。
 */
class AirRadarScheduleClient @Inject constructor(
    baseClient: OkHttpClient,
) {
    private val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun fetchLatest(baseUrl: String): ScheduleFetchResult = withContext(Dispatchers.IO) {
        val normalized = ScheduleDataSourceUrl.normalize(baseUrl)
            ?: return@withContext ScheduleFetchResult.Failure(ScheduleFetchError.InvalidUrl)
        val request = Request.Builder()
            .url(ScheduleDataSourceUrl.latestScheduleEndpoint(normalized))
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == HTTP_NOT_FOUND -> ScheduleFetchResult.NoData
                    !response.isSuccessful -> ScheduleFetchResult.Failure(
                        ScheduleFetchError.Http(response.code),
                    )
                    else -> {
                        val body = response.body?.string().orEmpty()
                        val snapshot = runCatching {
                            json.decodeFromString<ScheduleSnapshot>(body)
                        }.getOrNull()
                        if (snapshot == null) {
                            ScheduleFetchResult.Failure(ScheduleFetchError.InvalidData)
                        } else {
                            ScheduleFetchResult.Success(snapshot)
                        }
                    }
                }
            }
        } catch (_: SocketTimeoutException) {
            ScheduleFetchResult.Failure(ScheduleFetchError.Timeout)
        } catch (_: IOException) {
            ScheduleFetchResult.Failure(ScheduleFetchError.Network)
        }
    }

    companion object {
        internal const val TIMEOUT_SECONDS = 10L
        private const val HTTP_NOT_FOUND = 404
    }
}
