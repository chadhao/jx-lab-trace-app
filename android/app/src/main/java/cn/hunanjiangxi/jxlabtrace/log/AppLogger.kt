package cn.hunanjiangxi.jxlabtrace.log

/** 日志级别。 */
enum class LogLevel { D, I, W, E }

/** 环形缓冲中的一条日志。 */
data class LogEntry(
    val timeMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
)

/**
 * 应用级日志门面：环形缓冲（默认 500 条）+ 可选 logcat 透传。
 * 「我的」页导出属后续批次；本模块只提供内存查询接口。
 */
object AppLogger {
    const val DEFAULT_CAPACITY = 500

    private val lock = Any()
    private var capacity: Int = DEFAULT_CAPACITY
    private val buffer = ArrayDeque<LogEntry>()

    /** 覆盖环形缓冲容量（仅供测试）。 */
    fun configureCapacity(newCapacity: Int) {
        require(newCapacity > 0) { "capacity must be > 0" }
        synchronized(lock) {
            capacity = newCapacity
            buffer.clear()
        }
    }

    fun d(tag: String, message: String) = log(LogLevel.D, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.I, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.W, tag, message)
    fun e(tag: String, message: String) = log(LogLevel.E, tag, message)

    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(timeMillis = System.currentTimeMillis(), level = level, tag = tag, message = message)
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > capacity) buffer.removeFirst()
        }
    }

    /** 按时间顺序复制当前缓冲（旧 → 新）。 */
    fun snapshot(): List<LogEntry> = synchronized(lock) { buffer.toList() }

    /** 关键字过滤查询。 */
    fun query(keyword: String? = null): List<LogEntry> = snapshot().filter {
        keyword == null || it.tag.contains(keyword, ignoreCase = true) || it.message.contains(keyword, ignoreCase = true)
    }

    fun clear() = synchronized(lock) { buffer.clear() }
}
