package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.app.HomeInProgressItem
import cn.hunanjiangxi.jxlabtrace.app.HomeLatest
import cn.hunanjiangxi.jxlabtrace.app.HomeOverview
import cn.hunanjiangxi.jxlabtrace.app.HomeRecentItem
import cn.hunanjiangxi.jxlabtrace.app.HomeTodoItem
import cn.hunanjiangxi.jxlabtrace.ui.home.formatRecentAt
import cn.hunanjiangxi.jxlabtrace.ui.home.inProgressCardValue
import cn.hunanjiangxi.jxlabtrace.ui.home.recentCardValue
import cn.hunanjiangxi.jxlabtrace.ui.home.todosCardLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真机四 Bug 回归（MIMO-TASK-S02-NAV 追加）：
 * Bug-1 title="/" 纯符号回落；Bug-2 待办卡逐项行；Bug-3 at RFC3339 格式化 + action 人话。
 */
class HomeCardValueTest {

    // ---- Bug-1：进行中卡 title 纯符号回落 ----

    @Test
    fun `Bug-1 title为纯符号斜杠 - 回落域label`() {
        val overview = HomeOverview(
            in_progress = listOf(
                HomeInProgressItem(
                    key = "notice_open", label = "预报中车次", domain = "recv", count = 2,
                    latest = HomeLatest(id = 1, code = "N1", title = "/", status = "预报", at = "2026-10-22 09:00:00"),
                ),
            ),
        )
        val (value, hint) = inProgressCardValue(overview)
        assertEquals("预报中车次", value) // 纯符号视同空 ⇒ 回落 label
        assertTrue(hint.contains("在办 2 项"))
    }

    @Test
    fun `Bug-1 title为纯符号且label也空 - 回落无`() {
        val overview = HomeOverview(
            in_progress = listOf(
                HomeInProgressItem(
                    key = "k", label = "", domain = "recv", count = 0,
                    latest = HomeLatest(id = 1, title = "—", status = "", at = ""),
                ),
            ),
        )
        val (value, _) = inProgressCardValue(overview)
        assertEquals("无", value)
    }

    @Test
    fun `Bug-1 title含字母数字 - 正常展示`() {
        val overview = HomeOverview(
            in_progress = listOf(
                HomeInProgressItem(
                    key = "k", label = "预报中车次", domain = "recv", count = 1,
                    latest = HomeLatest(id = 1, title = "湘F·12345 / 李师傅", status = "预报", at = ""),
                ),
            ),
        )
        val (value, _) = inProgressCardValue(overview)
        assertEquals("湘F·12345 / 李师傅", value)
    }

    // ---- Bug-2：待办卡逐项行 ----

    @Test
    fun `Bug-2 多项todos - 逐项label冒号count行`() {
        val overview = HomeOverview(
            todos = listOf(
                HomeTodoItem(key = "await_receive", label = "待收车数", domain = "recv", count = 1),
                HomeTodoItem(key = "await_label", label = "待打码数", domain = "recv", count = 2),
                HomeTodoItem(key = "await_load", label = "待装车数", domain = "ship", count = 0),
            ),
        )
        val lines = todosCardLines(overview)
        assertEquals(3, lines.size)
        assertEquals("待收车数：1", lines[0])
        assertEquals("待打码数：2", lines[1])
        assertEquals("待装车数：0", lines[2])
        // 不再是单行长串
        assertFalse(lines.any { it.contains(" · ") })
    }

    @Test
    fun `Bug-2 空todos - 单行无`() {
        val lines = todosCardLines(HomeOverview())
        assertEquals(listOf("无"), lines)
    }

    // ---- Bug-3：最近作业 at 格式化 + action 人话 ----

    @Test
    fun `Bug-3 action为feishu_login - 映射飞书登录`() {
        val overview = HomeOverview(
            recent = listOf(
                HomeRecentItem(
                    id = 1,
                    at = "2026-10-22T15:39:10.886+08:00",
                    entity = "s_audit_log",
                    action = "feishu_login",
                    actor = "张三",
                ),
            ),
        )
        val (value, hint) = recentCardValue(overview)
        assertEquals("飞书登录", value)
        // RFC3339 → MM-dd HH:mm
        assertTrue("hint 应含格式化时间：$hint", hint.contains("10-22 15:39"))
        assertFalse(hint.contains("T")) // 不再直显 ISO 的 T
    }

    @Test
    fun `Bug-3 at为契约datetime格式 归一MM-dd HHmm`() {
        val overview = HomeOverview(
            recent = listOf(
                HomeRecentItem(id = 1, at = "2026-10-21 09:20:00", action = "create"),
            ),
        )
        val (value, hint) = recentCardValue(overview)
        assertEquals("新建", value)
        assertTrue(hint.contains("10-21 09:20"))
    }

    @Test
    fun `Bug-3 未知action码 - 原样显示`() {
        val overview = HomeOverview(
            recent = listOf(
                HomeRecentItem(id = 1, at = "2026-10-22T10:00:00+08:00", action = "custom_op"),
            ),
        )
        val (value, _) = recentCardValue(overview)
        assertEquals("custom_op", value)
    }

    @Test
    fun `formatRecentAt 解析失败原样兜底`() {
        assertEquals("not-a-time", formatRecentAt("not-a-time"))
        assertEquals("", formatRecentAt(""))
    }
}
