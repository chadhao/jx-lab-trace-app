package cn.hunanjiangxi.jxlabtrace.auth

import cn.hunanjiangxi.jxlabtrace.net.ApiError

/** S01 失败三类（设计稿 index.html#s01：网络 / 授权被拒 / 账号未分配角色）。 */
enum class LoginFailureKind { Network, Rejected, NoRole }

/**
 * S01 失败卡：三类各有专属文案与出路（M3-1）。
 * [canRetry] 为 false 时界面**不得**出现「重试」按钮——未分配角色是业务问题，重试无意义。
 */
data class LoginFailure(
    val kind: LoginFailureKind,
    val title: String,
    val reason: String,
    val errorCode: String,
    val canRetry: Boolean,
    val actionLabel: String,
)

/** 授权流程给出的判定信号（纯数据，供 [loginOutcomeOf] 分类，便于单测）。 */
sealed interface LoginSignal {
    /** 网络不可达（加载授权页失败 / 已断网）。 */
    object NetworkDown : LoginSignal

    /** 授权被拒或回调缺失（回到站内但没有拿到 jx_sid）。 */
    object AuthorizationRejected : LoginSignal

    /** GET /api/me 成功返回。 */
    data class MeLoaded(val me: Me) : LoginSignal

    /** GET /api/me 失败。 */
    data class MeError(val error: ApiError) : LoginSignal
}

/** S01 判定结果。 */
sealed interface LoginOutcome {
    data class Success(val me: Me) : LoginOutcome
    data class Failed(val failure: LoginFailure) : LoginOutcome
}

/**
 * S01 登录判定纯函数：把授权信号映射为四态中的「成功 / 失败卡」。
 * 三类失败文案互不相同；③未分配角色 canRetry=false（M3-1 单测入口）。
 */
fun loginOutcomeOf(signal: LoginSignal): LoginOutcome = when (signal) {
    LoginSignal.NetworkDown -> LoginOutcome.Failed(
        LoginFailure(
            kind = LoginFailureKind.Network,
            title = "登录未完成",
            reason = "网络不可达，请确认已连车间 WiFi 或开启 4G",
            errorCode = "AUTH_NETWORK",
            canRetry = true,
            actionLabel = "重试",
        ),
    )

    LoginSignal.AuthorizationRejected -> LoginOutcome.Failed(
        LoginFailure(
            kind = LoginFailureKind.Rejected,
            title = "授权未完成",
            reason = "飞书授权被取消或回调缺失，请重新发起登录",
            errorCode = "AUTH_REJECTED",
            canRetry = true,
            actionLabel = "重新登录",
        ),
    )

    is LoginSignal.MeLoaded -> {
        if (signal.me.roles.isEmpty() && signal.me.permissions.isEmpty()) {
            LoginOutcome.Failed(
                LoginFailure(
                    kind = LoginFailureKind.NoRole,
                    title = "登录未完成",
                    reason = "您的账号尚未分配角色，请联系综合运营部开通",
                    errorCode = "AUTH_NO_ROLE",
                    canRetry = false,
                    actionLabel = "我知道了",
                ),
            )
        } else {
            LoginOutcome.Success(signal.me)
        }
    }

    is LoginSignal.MeError -> when (signal.error) {
        is ApiError.Offline -> loginOutcomeOf(LoginSignal.NetworkDown)
        is ApiError.Unauthorized -> loginOutcomeOf(LoginSignal.AuthorizationRejected)
        else -> loginOutcomeOf(LoginSignal.NetworkDown)
    }
}
