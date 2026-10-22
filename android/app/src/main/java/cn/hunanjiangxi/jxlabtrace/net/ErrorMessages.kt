package cn.hunanjiangxi.jxlabtrace.net

/**
 * M12：`ApiError → 中文话术` 统一映射。
 *
 * 覆盖封闭类全部分支（Offline/Unauthorized/Forbidden/Server/Biz/Parse），
 * 其中 [ApiError.Biz] 分两级：
 * ① [KNOWN_409] 按**机器码**命中（S03 扫码 409 带 `code` 字段的场景）；
 * ② [KNOWN_409_TEXTS] 按**后端中文原文包含**命中（业务写接口 409 统一
 *    `{"error":"…"}` 无机器码——ship/recv/prod 冲突文案；M12 增补）；
 * 均未命中则透传后端中文 message。
 * 单测：ErrorMessagesTest（五类 + 机器码 + 文案包含两级 409）。
 */
object ErrorMessages {

    /** 三类 409 业务码 → 专属话术（S03 ③ 失败条与业务提交共用同一口径）。 */
    val KNOWN_409: Map<String, String> = mapOf(
        "ALREADY_FED" to "该袋已投料，请勿重复扫；如投错了请用「投料更正」（限本班次内）。",
        "ALREADY_VOID" to "该对象已作废，请到追溯页查看作废原因与去向。",
        "NO_RETURN_DECISION" to "该车尚无退货判定，不能退车；请联系综合运营部处理。",
    )

    /**
     * M12 增补：后端各业务 409 中文原文 → 专属话术（按包含匹配，取第一条命中）。
     * 来源（internal/store 哨兵错误逐条实读）：
     * - ship：已归集 / 已登记出场 / 无明细 / 状态不允许 / 跨客户拆单 / 取号繁忙·超限
     * - recv：已投料不许退车（联动）/ 袋在用 / 已作废 / 无退货判定
     * - prod：批作废 / 已投料
     * 不含出货撤销链（撤销属 PC，APP 无入口——SCOPE §4）。
     */
    val KNOWN_409_TEXTS: List<Pair<String, String>> = listOf(
        "已被出货单归集" to "该袋已在本次出货单中，不重复计数；如需调整请到 PC 端发起出货撤销。",
        "已登记出场" to "该出货单已登记出场，不得重复登记；如需更正请到 PC 端处理。",
        "无明细，不得出场" to "出货单还没有归集任何袋，请先扫码装车再登记出场。",
        "当前状态不允许" to "对象当前状态不允许该操作，请刷新后重试；若反复出现请联系综合运营部。",
        "跨客户装车须拆单" to "跨客户装车须拆单：请为另一位客户另建出货单分别装车。",
        "取号繁忙" to "出货单号取号繁忙，请稍后重试。",
        "序号已达上限" to "当日出货单号已达上限，请人工决策；请联系综合运营部处理。",
        "该车已有投料" to "该车已有投料，不能退车；如需处理请联系综合运营部。",
        "尚无退货判定" to "该车尚无退货判定，不能退车；请联系综合运营部处理。",
        "已取样 / 已投料" to "该袋已取样或已投料，不允许作废；如需处理请联系综合运营部。",
        "该对象已作废" to "该对象已作废，请到追溯页查看作废原因与去向。",
        "已投料" to "该袋已投料，请勿重复扫；如投错了请用「投料更正」（限本班次内）。",
    )

    /** 错误 → 展示话术（永不出现技术码给一线工人，SCOPE 铁律 2）。 */
    fun of(error: ApiError): String = when (error) {
        is ApiError.Offline,
        is ApiError.Unauthorized,
        is ApiError.Forbidden,
        is ApiError.Server,
        is ApiError.Parse,
        -> error.userMessage
        // 结构化拦截四字段：userMessage 已是人话 reason（§2.1），原样透传。
        is ApiError.Intercept -> error.userMessage
        is ApiError.Biz -> {
            KNOWN_409[error.code]
                ?: KNOWN_409_TEXTS.firstNotNullOfOrNull { (needle, phrase) ->
                    if (error.message.contains(needle)) phrase else null
                }
                ?: error.userMessage
        }
    }
}
