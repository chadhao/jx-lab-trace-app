package cn.hunanjiangxi.jxlabtrace

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.DomainBlock
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.NowBar
import cn.hunanjiangxi.jxlabtrace.ui.components.NumPad
import cn.hunanjiangxi.jxlabtrace.ui.components.ScanHero
import cn.hunanjiangxi.jxlabtrace.ui.components.TabBar
import cn.hunanjiangxi.jxlabtrace.ui.components.TabBarItem
import cn.hunanjiangxi.jxlabtrace.ui.components.TopBar
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * J7：尺寸断言 —— BigButton=60dp、NumPad 键位 ≥64dp、ScanHero ≥120dp、
 * TopBar=56dp、TabBar=64dp、可点区块 ≥56dp。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ComponentSizeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bigButtonHeightIs60dp() {
        compose.setContent {
            JxPreviewTheme { BigButton("提交过磅", onClick = {}) }
        }
        compose.onNodeWithText("提交过磅").assertHeightIsEqualTo(Tokens.Size.primaryButton)
    }

    @Test
    fun numPadKeysAtLeast64dp() {
        compose.setContent {
            JxPreviewTheme { NumPad(onDigit = {}, onBackspace = {}, onConfirm = {}) }
        }
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "backspace").forEach { key ->
            compose.onAllNodesWithTag("numpad_key_$key", useUnmergedTree = true)
                .onFirst()
                .assertHeightIsAtLeast(Tokens.Size.touchComfort)
        }
        compose.onNodeWithTag("numpad_confirm", useUnmergedTree = true)
            .assertHeightIsEqualTo(Tokens.Size.primaryButton)
    }

    @Test
    fun scanHeroHeightAtLeast120dp() {
        compose.setContent {
            JxPreviewTheme {
                ScanHero(title = "扫一扫查追溯", subtitle = "袋码 / 车码 / 批号", onClick = {})
            }
        }
        compose.onNodeWithTag("scan_hero").assertHeightIsAtLeast(Tokens.Size.traceScanHero)
    }

    @Test
    fun topBarHeightIs56dp() {
        compose.setContent {
            JxPreviewTheme { TopBar(title = "到货确认", onBack = {}) }
        }
        compose.onNodeWithTag("topbar").assertHeightIsEqualTo(Tokens.Size.topbar)
    }

    @Test
    fun tabBarHeightIs64dp() {
        compose.setContent {
            JxPreviewTheme {
                TabBar(
                    items = listOf(
                        TabBarItem("home", "首页", JxIcons.Home, Tokens.Color.Brand.primary),
                        TabBarItem("trace", "追溯", JxIcons.TraceForward, Tokens.Color.Domain.Trace.base),
                        TabBarItem("me", "我的", JxIcons.Me, Tokens.Color.Brand.primary),
                    ),
                    selectedId = "home",
                    onSelect = {},
                )
            }
        }
        compose.onNodeWithTag("tabbar").assertHeightIsEqualTo(Tokens.Size.tabbar)
    }

    @Test
    fun domainBlockAtLeastTouchMin() {
        compose.setContent {
            JxPreviewTheme {
                DomainBlock(
                    title = "收货",
                    subtitle = "到货确认 · 过磅 · 建袋打码",
                    icon = JxIcons.RecvTruck,
                    containerColor = Tokens.Color.Domain.Receiving.base,
                    contentColor = Tokens.Color.Domain.Receiving.on,
                    onClick = {},
                    modifier = Modifier.testTag("domain_block"),
                )
            }
        }
        compose.onNodeWithTag("domain_block").assertHeightIsAtLeast(Tokens.Size.touchMin)
    }

    @Test
    fun nowBarAtLeastTouchMin() {
        compose.setContent {
            JxPreviewTheme {
                NowBar(
                    title = "进行中：过磅",
                    subtitle = "湘F·12345",
                    actionLabel = "继续",
                    onAction = {},
                    modifier = Modifier.testTag("nowbar"),
                )
            }
        }
        compose.onNodeWithTag("nowbar").assertHeightIsAtLeast(Tokens.Size.touchMin)
    }
}
