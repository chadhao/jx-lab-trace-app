package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.print.shouldAutoConnect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 启动自动连接条件判定纯函数四场景（MIMO-TASK-AUTO-CONNECT ⑤）：
 * 三与——保存地址 && 权限已授予 && 未连接；缺任一 ⇒ false（调用方静默跳过）。
 */
class AutoConnectTest {

    @Test
    fun `场景1 有地址+有权限+未连接 - 应自动连接`() {
        assertTrue(shouldAutoConnect(saved = "AA:BB:CC:DD:EE:FF", hasPermission = true, connected = false))
    }

    @Test
    fun `场景2 无保存地址 - 不连接`() {
        assertFalse(shouldAutoConnect(saved = null, hasPermission = true, connected = false))
        // 空白地址同「未保存」
        assertFalse(shouldAutoConnect(saved = "   ", hasPermission = true, connected = false))
        assertFalse(shouldAutoConnect(saved = "", hasPermission = true, connected = false))
    }

    @Test
    fun `场景3 缺蓝牙权限 - 不连接静默跳过`() {
        assertFalse(shouldAutoConnect(saved = "AA:BB:CC:DD:EE:FF", hasPermission = false, connected = false))
    }

    @Test
    fun `场景4 已连接 - 不重复连接幂等`() {
        assertFalse(shouldAutoConnect(saved = "AA:BB:CC:DD:EE:FF", hasPermission = true, connected = true))
    }
}
