package cn.hunanjiangxi.jxlabtrace.net

/**
 * M12：`ApiError → 中文话术` 统一映射。
 *
 * 覆盖封闭类全部分支（Offline/Unauthorized/Forbidden/Server/Biz/Parse），
 * 其中 [ApiError.Biz] 对三类 409 业务码给专属话术（其余业务码透传后端中文 message）。
 * 单测：ErrorMessagesTest（五类 + 三类 409 业务码）。
 */
object ErrorMessages {

    /** 三类 409 业务码 → 专属话术（S03 ③ 失败条与业务提交共用同一口径）。 */
    val KNOWN_409: Map<String, String> = mapOf(
        "ALREADY_FED" to "该袋已投料，请勿重复扫；如投错了请用「投料更正」（限本班次内）。",
        "ALREADY_VOID" to "该对象已作废，请到追溯页查看作废原因与去向。",
        "NO_RETURN_DECISION" to "该车尚无退货判定，不能退车；请联系综合运营部处理。",
    )

    /** 错误 → 展示话术（永不出现技术码给一线工人，SCOPE 铁律 2）。 */
    fun of(error: ApiError): String = when (error) {
        is ApiError.Offline,
        is ApiError.Unauthorized,
        is ApiError.Forbidden,
        is ApiError.Server,
        is ApiError.Parse,
        -> error.userMessage
        is ApiError.Biz -> KNOWN_409[error.code] ?: error.userMessage
    }
}
