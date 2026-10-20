package cn.hunanjiangxi.jxlabtrace.recv

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * S04 到货确认纯逻辑（M05）：
 * - 本地过滤当日 + 按车牌（D23：零后端改动）；
 * - 无预报拦截：最近 3 条同牌预报 + 一键拨号；
 * - 拦截页**不产生任何写请求**（判据 M5-1）。
 */
object NoticesFilter {

    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE

    /** 当日过滤：arrive_date 为空（未确认链根日）或等于今日。 */
    fun isToday(notice: RecvNotice, today: LocalDate): Boolean {
        val d = notice.arrive_date
        return d.isBlank() || runCatching { LocalDate.parse(d, DATE) }.getOrNull() == today
    }

    /** S04 列表：当日 + 状态=预报（待确认）。 */
    fun pendingToday(notices: List<RecvNotice>, today: LocalDate): List<RecvNotice> =
        notices
            .filter { it.status == "预报" && isToday(it, today) }
            .sortedBy { it.seq_no }

    /** 已完成区（当日已到货）。 */
    fun arrivedToday(notices: List<RecvNotice>, today: LocalDate): List<RecvNotice> =
        notices
            .filter { it.status == "已到货" && isToday(it, today) }
            .sortedBy { it.seq_no }

    /** 按车牌精确匹配（输入容忍大小写与空格）。 */
    fun matchPlate(notice: RecvNotice, plateQuery: String): Boolean {
        val q = plateQuery.trim().uppercase()
        if (q.isEmpty()) return false
        return notice.plate_no.trim().uppercase() == q ||
            notice.plate_no.trim().uppercase().contains(q)
    }

    /** 无预报拦截：该车牌最近 3 条预报（任意日期，按 id 倒序取 3）。 */
    fun recentByPlate(notices: List<RecvNotice>, plate: String, limit: Int = 3): List<RecvNotice> =
        notices
            .filter { matchPlate(it, plate) }
            .sortedByDescending { it.id }
            .take(limit)

    /** 车牌是否在给定列表中有今日预报（拦截判定）。 */
    fun hasTodayNotice(notices: List<RecvNotice>, plate: String, today: LocalDate): Boolean =
        pendingToday(notices, today).any { matchPlate(it, plate) }
}

/**
 * 拨号意图（纯数据 · M5-1 断言：ACTION_DIAL + tel: 占位号）。
 * UI 层转成真正的 android Intent（不直接呼出，交给系统拨号盘确认）。
 */
data class DialIntent(val action: String, val uri: String)

/**
 * M12 四类拦截之「无预报 / 无车辆档案」文案变体（并入同一拦截页——SCOPE §5：
 * 两者出路相同：电话报车号/建档 → PC 处理 → APP 刷新重试）。
 */
enum class ArrivalInterceptKind(val title: String, val guidance: String) {
    NO_NOTICE(
        title = "该车牌今日无预报，暂不能收货",
        guidance = "请先电话联系综合运营部补预报，补录后下拉刷新本页即可继续",
    ),
    NO_VEHICLE_FILE(
        title = "车辆档案不存在，暂不能收货",
        guidance = "该车牌未在系统建档；请电话联系综合运营部先建档，建档后下拉刷新本页即可继续",
    ),
}

/**
 * 拦截文案选择（纯本地判定，零写请求）：
 * 车牌在给定列表**完全无任何记录** ⇒ 档案缺失变体；有历史记录但今日无待确认预报 ⇒ 无预报变体。
 */
fun arrivalInterceptKind(
    notices: List<RecvNotice>,
    plate: String,
): ArrivalInterceptKind {
    val matched = notices.filter { NoticesFilter.matchPlate(it, plate) }
    return if (matched.isEmpty()) {
        ArrivalInterceptKind.NO_VEHICLE_FILE
    } else {
        ArrivalInterceptKind.NO_NOTICE
    }
}

object DialActions {

    /** 系统拨号 Action（Intent.ACTION_DIAL 的字符串等价，避免 JVM 单测依赖 Android 框架）。 */
    const val ACTION_DIAL = "android.intent.action.DIAL"

    /** 综合运营部占位号 —— 集中一处待项目方替换（与 ScanIntercepts.OPERATIONS_PHONE 同源口径）。 */
    const val OPERATIONS_PHONE = "0730-xxxxxxx"

    /** 构造拨号意图。 */
    fun operationsDial(): DialIntent = DialIntent(ACTION_DIAL, "tel:$OPERATIONS_PHONE")
}

/**
 * S05 过磅纯逻辑。
 * 净重 = 毛重 − 皮重（吨，3 位小数）；袋数默认按净重 ÷ 0.6 折算（可改）。
 */
object WeighLogic {

    /** 默认单袋吨数（任务包：0.6 t/袋）。 */
    const val DEFAULT_BAG_TONS = 0.6

    /** 净重（吨，保留 3 位）。非法输入返回 null。 */
    fun netTons(gross: String, tare: String): Double? {
        val g = gross.trim().toDoubleOrNull() ?: return null
        val t = tare.trim().toDoubleOrNull() ?: return null
        val net = g - t
        if (net <= 0) return null
        return round3(net)
    }

    /** 袋数默认折算：ceil(net / 0.6)，至少 1。 */
    fun defaultBagCount(netTons: Double?): Int {
        if (netTons == null || netTons <= 0) return 0
        return kotlin.math.ceil(netTons / DEFAULT_BAG_TONS).toInt().coerceAtLeast(1)
    }

    /** 提交合法性：毛 > 皮 ≥ 0 且净 > 0。 */
    fun canSubmitWeigh(gross: String, tare: String): Boolean = netTons(gross, tare) != null

    private fun round3(v: Double): Double = kotlin.math.round(v * 1000.0) / 1000.0
}

/**
 * S06 建袋幂等（判据 M5-2）：本地以「车次 + 袋序」去重；
 * 失败重试不重复建 —— 重试前先查 [已有袋]，有则直接采用。
 */
object BagIdempotency {

    /** 合并去重：以 bag_seq 为键，保留先到者（后端一次生成，seq 唯一）。 */
    fun merge(existing: List<RecvBag>, incoming: List<RecvBag>): List<RecvBag> {
        val bySeq = LinkedHashMap<Int, RecvBag>()
        for (b in existing) bySeq[b.bag_seq] = b
        for (b in incoming) bySeq.putIfAbsent(b.bag_seq, b)
        return bySeq.values.sortedBy { it.bag_seq }
    }

    /** 序号连续且无重复（建袋成功判定）。 */
    fun isContiguous(bags: List<RecvBag>, expectedCount: Int): Boolean {
        if (bags.size != expectedCount) return false
        val seqs = bags.map { it.bag_seq }.sorted()
        return seqs == (1..expectedCount).toList()
    }

    /** 重试策略：已有 N 袋 —— N==期望则跳过生成；N==0 则全量生成；0<N<期望视为异常（后端一次性生成）。 */
    sealed interface RetryPlan {
        /** 无需再建（幂等命中）。 */
        data class AlreadyDone(val bags: List<RecvBag>) : RetryPlan

        /** 全量生成。 */
        object GenerateAll : RetryPlan

        /** 部分存在（后端语义下不应发生）——提示人工核查。 */
        data class Partial(val existing: Int, val expected: Int) : RetryPlan
    }

    fun planRetry(existing: List<RecvBag>, expectedCount: Int): RetryPlan = when {
        existing.size == expectedCount && isContiguous(existing, expectedCount) ->
            RetryPlan.AlreadyDone(existing)
        existing.isEmpty() -> RetryPlan.GenerateAll
        else -> RetryPlan.Partial(existing.size, expectedCount)
    }
}

/**
 * 补打表单（判据 M5-3）：原因三选一 + 说明必填；空原因 ⇒ 提交禁用。
 */
object ReprintForm {

    /** 原因三选一（S06 设计稿）。 */
    val REASONS = listOf("标签破损", "打印模糊", "贴错重贴")

    /** 表单有效：选中原因 ∈ [REASONS] 且说明非空白。 */
    fun isValid(reasonCategory: String?, detail: String): Boolean =
        reasonCategory != null && reasonCategory in REASONS && detail.isNotBlank()

    /** 后端 reason 字段：`类别 · 说明`。 */
    fun reasonText(reasonCategory: String, detail: String): String =
        "${reasonCategory.trim()} · ${detail.trim()}"
}

/**
 * 退车（判据 M5-4）：二次确认对话框后才发 POST return。
 */
object ReturnFlow {

    /** 确认对话框文案（组件复用同一文案口径）。 */
    const val CONFIRM_TITLE = "确认退车？"
    const val CONFIRM_MESSAGE = "退车后该车全部袋码将作废，且需质检「退货」判定在先。此操作不可撤销。"

    /** 可退车状态：非已退货。 */
    fun canReturn(truckStatus: String): Boolean = truckStatus != "已退货"
}
