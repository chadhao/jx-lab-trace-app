package cn.hunanjiangxi.jxlabtrace.prod

import cn.hunanjiangxi.jxlabtrace.recv.WeighLogic
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ===== M07 生产线纯逻辑（S08–S11）=====
// 判据挂钩：
//   M7-1 建批零输入：默认值来自上批；无上批 ⇒ 空 + 逐项校验；
//   M7-2 投料进度：本地累计（FeedProgress）；
//   M7-3 班次判定：**只吃服务端时间**（ShiftRules），手机时间不可达；
//   M7-4 更正请求体：FeedCorrectForm 组装（feed_id/new_weight/reason/version）；
//   M7-5 作业段：重复点「结束」幂等（OperationTracker）。

/**
 * S08 建批表单（判据 M7-1）：
 * - 有上批 ⇒ customer/投入/产出三项默认沿用上批（显式修改入口，防手滑换料）；
 * - 无上批 ⇒ 字段空，提交校验逐项提示；
 * - 批号由后端取号生成，APP 只显示（提交前显示「提交后生成」）。
 */
object BatchForm {

    /** 无上批时的空表单。 */
    fun empty(): CreateBatchBody = CreateBatchBody(
        customer_id = 0,
        input_material_id = 0,
        planned_output_material_id = 0,
    )

    /** 默认表单：沿用上批配方（客户/投入/产出）。上批为空 ⇒ [empty]。 */
    fun defaultsFrom(previous: ProdBatch?): CreateBatchBody =
        if (previous == null) {
            empty()
        } else {
            CreateBatchBody(
                customer_id = previous.customer_id,
                input_material_id = previous.input_material_id,
                planned_output_material_id = previous.planned_output_material_id,
            )
        }

    /** 逐项校验错误（提交前提示；全空则全部命中）。 */
    fun validate(body: CreateBatchBody): List<String> = buildList {
        if (body.customer_id <= 0) add("必须选择客户（沿用上批或修改）")
        if (body.input_material_id <= 0) add("必须选择投入物料")
        if (body.planned_output_material_id <= 0) add("必须选择计划产出物料")
    }

    fun isValid(body: CreateBatchBody): Boolean = validate(body).isEmpty()

    /** 「上一批」摘要行（S08 顶栏展示）。 */
    fun previousSummary(previous: ProdBatch?): String =
        if (previous == null) "无上一批 · 请显式选择配方" else "上一批 ${previous.human.ifBlank { previous.code }} · ${previous.status}"
}

/**
 * S09 投料进度（判据 M7-2）：本地即时累计，不依赖列表刷新。
 */
object FeedProgress {

    /** 扫码确认成功后的新进度：已投 +1。 */
    fun afterConfirm(current: Int): Int = current + 1

    /** 展示文案：`已投 19 袋`。 */
    fun label(fed: Int): String = "已投 $fed 袋（实时）"

    /** 百分比（无计划总数时按 40 袋参考满度；>100% 封顶 100）。 */
    fun percent(fed: Int, planned: Int = DEFAULT_PLANNED): Int =
        if (planned <= 0) 0 else ((fed.toDouble() / planned) * 100).toInt().coerceIn(0, 100)

    const val DEFAULT_PLANNED = 40
}

/**
 * 班次规则（判据 M7-3 · M12-3）：
 * **时间判定唯一输入是服务端时间**——生产代码不得传入手机时间；
 * 手机改未来/过去不影响任何时效判定。
 *
 * 班次窗口：按服务端时区把一天切为 3 班（00–08 / 08–16 / 16–24）。
 * 「本班次内」＝投料时刻与当前服务端时刻落在同一班次窗口。
 */
object ShiftRules {

    const val SHIFT_HOURS = 8

    /** 服务端时间戳（epoch millis）→ 班次键 `yyyy-MM-dd#班序(0/1/2)`。 */
    fun shiftKey(serverMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(serverMillis), zone)
        val shift = (ldt.hour / SHIFT_HOURS).coerceIn(0, 2)
        return "${ldt.toLocalDate()}#$shift"
    }

    /** 投料是否仍在本班次内可更正（两个入参都必须是**服务端时间**）。 */
    fun withinSameShift(fedAtMillis: Long, serverNowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        shiftKey(fedAtMillis, zone) == shiftKey(serverNowMillis, zone)

    /** 后端 fed_at（`yyyy-MM-dd HH:mm:ss`，服务端时区）→ epoch millis。 */
    fun parseFedAt(fedAt: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val text = fedAt?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching {
            LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
}

/**
 * S09 更正表单（判据 M7-4）：原因三选一 + 说明必填；请求体含
 * feed_id / new_weight / reason / version。
 */
object FeedCorrectForm {

    /** 原因三选一（S09 设计稿：磅读错 / 扫错袋 / 其它）。 */
    val REASONS = listOf("磅读错", "扫错袋", "其它")

    /** 表单有效：选中原因 ∈ [REASONS] 且说明非空白且新重量 > 0。 */
    fun isValid(reasonCategory: String?, detail: String, newWeightTons: Double?): Boolean =
        reasonCategory != null &&
            reasonCategory in REASONS &&
            detail.isNotBlank() &&
            newWeightTons != null &&
            newWeightTons > 0

    /** 后端 reason 字段：`类别 · 说明`（与 S06 补打同口径）。 */
    fun reasonText(reasonCategory: String, detail: String): String =
        "${reasonCategory.trim()} · ${detail.trim()}"

    /** 组装请求体（M7-4 断言形状）。 */
    fun build(
        feed: FeedRecord,
        newWeightTons: Double,
        reasonCategory: String,
        detail: String,
    ): FeedCorrectBody = FeedCorrectBody(
        feed_id = feed.id,
        new_weight = newWeightTons,
        reason = reasonText(reasonCategory, detail),
        version = feed.version + 1,
        feed_weight = newWeightTons,
        remark = detail.trim(),
    )
}

/**
 * S10 作业段（判据 M7-5）：开始/结束两点打点；本地计时仅展示；
 * 重复点「结束」幂等——第二次不再发起请求。
 */
class OperationTracker {

    /** 未开始 / 计时中 / 已结束。 */
    enum class Phase { Idle, Running, Ended }

    var phase: Phase = Phase.Idle
        private set

    /** 开始时刻（服务端时间 millis；开始按钮时由 VM 注入）。 */
    var startMillis: Long? = null
        private set

    /** 结束请求已发出（幂等门：Ended 后 [onEnd] 返回 false ⇒ 不发第二次）。 */
    fun onStart(serverMillis: Long): Boolean {
        if (phase != Phase.Idle) return false
        phase = Phase.Running
        startMillis = serverMillis
        return true
    }

    /** 结束打点：首次返回 true（VM 据此 POST）；重复调用返回 false 且不发请求。 */
    fun onEnd(): Boolean {
        if (phase != Phase.Running) return false
        phase = Phase.Ended
        return true
    }

    /** 断网提交失败回滚（表单态保留 ⇒ 回到 Running 可重试）。 */
    fun onEndFailed() {
        if (phase == Phase.Ended) phase = Phase.Running
    }

    /** 开启下一段（Done 后复位为 Idle，重新打点）。 */
    fun reset() {
        phase = Phase.Idle
        startMillis = null
    }

    /** 本地计时展示（仅 UI；后端时间才是准）。 */
    fun elapsedMillis(nowMillis: Long): Long {
        val start = startMillis ?: return 0
        return (nowMillis - start).coerceAtLeast(0)
    }
}

/**
 * S11 成品打码袋数折算：默认按投料净重 ÷ 0.6 t/袋（与 S06 同一折算口径）。
 */
object FgBagPlan {

    /** 投料累计净重（吨）。 */
    fun totalFeedWeight(feeds: List<FeedRecord>): Double =
        feeds.mapNotNull { it.feed_weight }.sum()

    /** 默认袋数：ceil(净重 / 0.6)，至少 1；净重未知 ⇒ 0（须显式输入）。 */
    fun defaultBagCount(feeds: List<FeedRecord>): Int {
        val net = totalFeedWeight(feeds)
        if (net <= 0) return 0
        return kotlin.math.ceil(net / WeighLogic.DEFAULT_BAG_TONS).toInt().coerceAtLeast(1)
    }
}
