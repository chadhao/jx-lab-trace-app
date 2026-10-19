package cn.hunanjiangxi.jxlabtrace

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cn.hunanjiangxi.jxlabtrace.print.PrinterGateCard
import cn.hunanjiangxi.jxlabtrace.print.PrinterUiState
import cn.hunanjiangxi.jxlabtrace.print.printEntryEnabled
import cn.hunanjiangxi.jxlabtrace.print.printerDisconnectedHint
import cn.hunanjiangxi.jxlabtrace.print.printerUiStateOf
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * M9-3：未连接打印机 ⇒ 打印入口显示「去连接」状态卡（Compose 状态单测）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PrinterGateTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `断连状态映射 - Disconnected且提示去连接`() {
        val state = printerUiStateOf(connected = false)
        assertEquals(PrinterUiState.Disconnected, state)
        assertFalse(printEntryEnabled(state))
        val hint = printerDisconnectedHint(state)
        assertNotNull(hint)
        assertTrue(hint!!.contains("去连接"))
    }

    @Test
    fun `已连接 - 可打印且无断连提示`() {
        val state = printerUiStateOf(connected = true, deviceName = "KM-118BT")
        assertTrue(state is PrinterUiState.Connected)
        assertTrue(printEntryEnabled(state))
        assertNull(printerDisconnectedHint(state))
        assertEquals("TSPL", (state as PrinterUiState.Connected).protocol)
    }

    @Test
    fun `连接中与错误 - 状态互斥`() {
        assertTrue(printerUiStateOf(connected = false, connecting = true) is PrinterUiState.Connecting)
        assertTrue(printerUiStateOf(connected = false, lastError = "蓝牙关闭") is PrinterUiState.Error)
    }

    @Test
    fun `M9-3 断连 - 状态卡显示去连接`() {
        compose.setContent {
            JxPreviewTheme {
                PrinterGateCard(
                    state = PrinterUiState.Disconnected,
                    onGoSettings = {},
                    onSelfTest = {},
                )
            }
        }
        compose.onNodeWithText("去连接").assertIsDisplayed()
    }

    @Test
    fun `已连接 - 显示设备名与自检入口`() {
        compose.setContent {
            JxPreviewTheme {
                PrinterGateCard(
                    state = PrinterUiState.Connected("KM-118BT"),
                    onGoSettings = {},
                    onSelfTest = {},
                )
            }
        }
        compose.onNodeWithText("KM-118BT").assertIsDisplayed()
        compose.onNodeWithText("自检打印").assertIsDisplayed()
    }

    @Test
    fun `断连点击去连接 - 回调触发`() {
        var clicked = false
        compose.setContent {
            JxPreviewTheme {
                PrinterGateCard(
                    state = PrinterUiState.Disconnected,
                    onGoSettings = { clicked = true },
                    onSelfTest = {},
                )
            }
        }
        compose.onNodeWithText("去连接").performClick()
        assertTrue(clicked)
    }
}
