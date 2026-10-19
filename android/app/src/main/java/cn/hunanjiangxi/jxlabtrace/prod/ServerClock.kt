package cn.hunanjiangxi.jxlabtrace.prod

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 服务端时间源抽象（判据 M7-3）：班次判定**只吃本接口**——
 * 生产实现 [ServerClock]；单测注入固定时间（手机时间不可达）。
 */
fun interface ServerTimeSource {
    /** 服务端当前时间（epoch millis）；不可达返回 null（不回退手机时间）。 */
    suspend fun nowMillis(): Long?
}

/**
 * 服务端时间源（判据 M7-3 / M12-3）：
 * 班次与时效判定**只吃本类**——禁用 `System.currentTimeMillis` / 手机时钟。
 *
 * 实现：HEAD /healthz 读 `Date` 响应头（RFC 1123，GMT）；
 * 取不到时返回 null，由调用方按
 * 「服务端时间不可达 ⇒ 更正入口置灰」处理（宁灰不错放）。
 */
class ServerClock(private val api: ApiClient) : ServerTimeSource {

    /** 最近一次成功取得的服务端时间（epoch millis）。 */
    @Volatile
    var lastKnownMillis: Long? = null
        private set

    /** 取服务端当前时间；失败返回 null（不回退手机时间）。 */
    override suspend fun nowMillis(): Long? = withContext(Dispatchers.IO) {
        if (!api.currentlyOnline()) return@withContext null
        val request = okhttp3.Request.Builder()
            .url(api.urlOf(ApiClient.HEALTH_PATH))
            .head()
            .build()
        try {
            api.httpClient.newCall(request).execute().use { response ->
                val date = response.header("Date")
                parseHttpDate(date)?.also { lastKnownMillis = it }
            }
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        /** RFC 1123 日期（HTTP Date 头）。 */
        private val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US)

        /** 解析 `Wed, 21 Oct 2015 07:28:00 GMT` → epoch millis。 */
        fun parseHttpDate(value: String?): Long? {
            val text = value?.trim().orEmpty()
            if (text.isEmpty()) return null
            return runCatching {
                ZonedDateTime.parse(text, FORMATTER).toInstant().toEpochMilli()
            }.getOrNull()
        }

        /** millis → 后端时间格式 `yyyy-MM-dd HH:mm:ss`（提交 start_at/end_at 用）。 */
        fun toBackendDateTime(millis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
            java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }
}
