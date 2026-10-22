package cn.hunanjiangxi.jxlabtrace

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import cn.hunanjiangxi.jxlabtrace.net.ApiError
import cn.hunanjiangxi.jxlabtrace.ui.components.AppInterceptPage
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 判据 I-3：结构化拦截四字段在 InterceptPage 正确展示——
 * error 短标签作标题、reason 人话原因、owner 责任角色、contact 联系途径三段齐备（§5.1–3）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AppInterceptDisplayTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun interceptFourFieldsDisplayed() {
        compose.setContent {
            JxPreviewTheme {
                AppInterceptPage(
                    intercept = ApiError.Intercept(
                        httpCode = 404,
                        error = "无预报",
                        reason = "该车牌今日无预报记录，不能确认到货。",
                        owner = "收货文员（预报登记）",
                        contact = "飞书联系收货文员在 Web「收货 › 预报登记」补录预报后再操作",
                    ),
                    onBack = {},
                )
            }
        }
        // ① error 短标签作标题
        compose.onNodeWithText("无预报").assertIsDisplayed()
        // ② reason 人话原因
        compose.onNodeWithText("该车牌今日无预报记录，不能确认到货。").assertIsDisplayed()
        // ③ owner 责任角色（InterceptPage 恒渲染「负责人：」前缀）
        compose.onNodeWithText("负责人：收货文员（预报登记）").assertIsDisplayed()
        // ③ contact 联系途径
        compose.onNodeWithText("联系途径：飞书联系收货文员在 Web「收货 › 预报登记」补录预报后再操作")
            .assertIsDisplayed()
        // 出路按钮（无死胡同）
        compose.onNodeWithText("返回").assertIsDisplayed()
    }

    @Test
    fun interceptWithConflictShowsRefreshAction() {
        compose.setContent {
            JxPreviewTheme {
                AppInterceptPage(
                    intercept = ApiError.Intercept(
                        httpCode = 409,
                        error = "尚未过磅",
                        reason = "该车还未过磅，不能生成袋码；请先完成过磅。",
                        owner = "收货文员（收货与打码）",
                        contact = "飞书联系收货文员按页面提示处理",
                    ),
                    onBack = {},
                    actionText = "刷新",
                    onAction = {},
                )
            }
        }
        compose.onNodeWithText("尚未过磅").assertIsDisplayed()
        compose.onNodeWithText("刷新").assertIsDisplayed()
    }
}
