package cn.hunanjiangxi.jxlabtrace.net

/**
 * 统一领域错误模型。所有对外文案均为中文 [userMessage]，不暴露技术码。
 *
 * 对应判据：Offline（前置探测拦截）/ Unauthorized(401) / Forbidden(403) /
 * Server(5xx) / Biz(业务码) / Parse(解析失败)。
 */
sealed class ApiError(val userMessage: String) {

    /** 断网 / 前置探测失败：请求未发出。 */
    class Offline : ApiError("网络中断，作业提交已暂停。请检查网络后重试。")

    /** 401：会话失效。 */
    class Unauthorized : ApiError("登录已过期，请重新登录。")

    /** 403：越权（话术按 SCOPE 铁律，不显示技术错误码）。 */
    class Forbidden : ApiError("你的角色无此权限，请联系综合运营部。")

    /** 5xx 及其他非预期 HTTP 状态。 */
    class Server(val httpCode: Int) : ApiError("服务器繁忙（$httpCode），请稍后重试。")

    /** 业务错误码（HTTP 200 但 envelope.code != 0）。 */
    class Biz(val code: String, val message: String) : ApiError(
        message.ifBlank { "业务处理失败，请稍后重试。" }
    )

    /** 响应体解析失败。 */
    class Parse(val cause: Throwable?) : ApiError("数据解析失败，请重试；若反复出现请联系管理员。")

    /**
     * /api/app/ 前缀 4xx 结构化拦截四字段（docs/APP-API-A-B.md §2.1）：
     * `{error, reason, owner, contact}` → InterceptPage 三段展示（原因+owner+contact）。
     * [httpCode] 保留原始状态（400/403/404/409…）；[userMessage]＝人话 [reason]。
     */
    class Intercept(
        val httpCode: Int,
        val error: String,
        val reason: String,
        val owner: String,
        val contact: String,
    ) : ApiError(reason.ifBlank { error.ifBlank { "操作被拦截。" } })
}

/** 网络层统一返回值。 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val error: ApiError) : ApiResult<Nothing>()
}
