package cn.hunanjiangxi.jxlabtrace.log

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃留栈（MIMO-TASK-S02-NAV 追加 · 2026-10-22）：
 * 蓝牙闪退两轮防御未兜住且无 logcat——治本先让所有未捕获异常留下完整栈。
 * 追加写入 `files/crash-log.txt`；写完交还原 handler（保持系统崩溃/重启行为）。
 * 纯逻辑与文件 IO 分离：[formatCrash] 纯函数可 JVM 单测。
 */
object CrashLogWriter {

    const val FILE_NAME = "crash-log.txt"
    private const val TAG = "CrashLog"
    /** 单条崩溃块头部标记，便于从导出文本里切分。 */
    const val HEADER_PREFIX = "===== CRASH"

    /**
     * 单条崩溃块格式化（纯函数）：
     * ```
     * ===== CRASH 2026-10-22 20:50:01.123 · v9b38484 =====
     * thread: main
     * java.lang.SecurityException: ...
     *     at ...
     *     ...
     * ```
     */
    fun formatCrash(
        timeMillis: Long,
        threadName: String,
        throwable: Throwable,
        versionName: String,
    ): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).format(Date(timeMillis))
        val stack = java.io.StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString().trimEnd()
        return buildString {
            appendLine("$HEADER_PREFIX $stamp · v$versionName =====")
            appendLine("thread: $threadName")
            appendLine("${throwable.javaClass.name}: ${throwable.message ?: ""}".trimEnd())
            appendLine(stack)
            appendLine("===== END CRASH =====")
        }
    }

    /**
     * 追加写入崩溃块。任何 IO 失败静默吞掉——崩溃路径绝不能再抛。
     * 返回是否写入成功（测试可断言）。
     */
    fun append(file: File, block: String): Boolean = try {
        file.parentFile?.mkdirs()
        FileWriter(file, /* append = */ true).use { it.write(block); it.write("\n") }
        true
    } catch (_: Exception) {
        false
    }

    /** 读取崩溃日志全文（诊断导出用）；不存在/失败返回空串。 */
    fun readAll(file: File): String = try {
        if (file.isFile) file.readText() else ""
    } catch (_: Exception) {
        ""
    }

    /**
     * 安装全局 handler：捕获 ⇒ 写 crash-log ⇒ 交还安装前的默认 handler（保持系统崩溃行为）。
     * 幂等：重复安装时链到当前 handler，不丢已有链路。
     */
    fun install(
        file: File,
        versionName: String,
    ): Thread.UncaughtExceptionHandler {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val handler = Thread.UncaughtExceptionHandler { thread, throwable ->
            // 写文件失败绝不阻断崩溃链路
            try {
                val block = formatCrash(
                    timeMillis = System.currentTimeMillis(),
                    threadName = thread.name,
                    throwable = throwable,
                    versionName = versionName,
                )
                append(file, block)
            } catch (_: Exception) {
                // swallow —— 崩溃路径不允许二次抛
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            }
        }
        Thread.setDefaultUncaughtExceptionHandler(handler)
        return handler
    }
}
