package cn.hunanjiangxi.jxlabtrace.auth

import cn.hunanjiangxi.jxlabtrace.log.LogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 登录诊断导出：把 [cn.hunanjiangxi.jxlabtrace.log.AppLogger] 环形缓冲
 * 格式化为可分享纯文本（`HH:mm:ss.SSS LEVEL [tag] message` 逐行）。
 * 纯函数，无 Android 依赖，便于单测。
 */
object LoginDiag {

    private const val EMPTY_TEXT = "(缓冲为空)"

    /** 单条日志的导出行格式：`HH:mm:ss.SSS LEVEL [tag] message`。 */
    fun formatLine(entry: LogEntry, timeMillis: Long = entry.timeMillis): String {
        val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA).format(Date(timeMillis))
        return "$stamp ${entry.level.name} [${entry.tag}] ${entry.message}"
    }

    /** 全量导出文本；空缓冲返回 [EMPTY_TEXT] 兜底。 */
    fun exportText(entries: List<LogEntry>): String =
        if (entries.isEmpty()) EMPTY_TEXT
        else entries.joinToString(separator = "\n") { formatLine(it) }
}
