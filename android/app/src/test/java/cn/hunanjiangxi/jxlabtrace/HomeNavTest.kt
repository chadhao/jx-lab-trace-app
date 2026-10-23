package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.ui.home.HOME_TABS
import cn.hunanjiangxi.jxlabtrace.ui.home.HomeEntryId
import cn.hunanjiangxi.jxlabtrace.ui.home.homeEntryIds
import cn.hunanjiangxi.jxlabtrace.ui.home.homeRoleLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MIMO-TASK-S02-NAV 判据单测：
 * - N-2：给定 roles ⇒ 期望入口 id 集合与 SCOPE 矩阵一致（三变体 + 并集 + 角色无关回退）；
 * - N-3：TabBar 三项恒为「现场·追溯·我的」，与角色无关。
 */
class HomeNavTest {

    // 权限矩阵驱动用例的权限点集（2026-10-23：me.permissions 上线后口径）
    private val FULL_PERMS = listOf("recv.arrive.confirm","recv.weigh","recv.bag.gen","ship.load.scan","out.register","sample.take","prod.batch.create","prod.feed.scan","prod.op.log","prod.fg.gen","trace.forward","trace.backward","trace.batch.view","rpt.view")
    private val RECV_ONLY_PERMS = listOf("recv.arrive.confirm","recv.weigh","recv.bag.gen","ship.load.scan","out.register","trace.forward")
    private val PROD_PERMS = listOf("sample.take","prod.batch.create","prod.feed.scan","prod.op.log","prod.fg.gen","trace.backward")
    private val MGMT_PERMS = listOf("trace.forward","trace.backward","trace.batch.view","rpt.view")


    // ===== N-2：角色 → 入口集合 =====

    @Test
    fun `N-2 receiver - 收货·建袋打码·装车出场·追溯`() {
        assertEquals(
            setOf(
                HomeEntryId.RECV, HomeEntryId.BAG, HomeEntryId.LOAD, HomeEntryId.TRACE,
            ),
            homeEntryIds(RECV_ONLY_PERMS).toSet(),
        )
    }

    @Test
    fun `N-2 production - 取样·建批·投料·作业段·成品打码·追溯`() {
        assertEquals(
            setOf(
                HomeEntryId.SAMPLE, HomeEntryId.BATCH, HomeEntryId.FEED,
                HomeEntryId.OPERATION, HomeEntryId.FG_PRINT, HomeEntryId.TRACE,
            ),
            homeEntryIds(PROD_PERMS).toSet(),
        )
    }

    @Test
    fun `N-2 management - 追溯·报表`() {
        assertEquals(
            setOf(HomeEntryId.TRACE, HomeEntryId.REPORT),
            homeEntryIds(MGMT_PERMS).toSet(),
        )
    }

    @Test
    fun `N-2 sales - 追溯·报表`() {
        assertEquals(
            setOf(HomeEntryId.TRACE, HomeEntryId.REPORT),
            homeEntryIds(MGMT_PERMS).toSet(),
        )
    }

    @Test
    fun `N-2 多角色 - 入口取并集`() {
        assertEquals(
            setOf(
                HomeEntryId.RECV, HomeEntryId.BAG, HomeEntryId.LOAD, HomeEntryId.TRACE,
                HomeEntryId.SAMPLE, HomeEntryId.BATCH, HomeEntryId.FEED,
                HomeEntryId.OPERATION, HomeEntryId.FG_PRINT,
            ),
            homeEntryIds(RECV_ONLY_PERMS + PROD_PERMS).toSet(),
        )
        assertEquals(
            setOf(
                HomeEntryId.RECV, HomeEntryId.BAG, HomeEntryId.LOAD,
                HomeEntryId.TRACE, HomeEntryId.REPORT,
            ),
            homeEntryIds(RECV_ONLY_PERMS + MGMT_PERMS).toSet(),
        )
    }

    @Test
    fun `N-2 无权限点 - 无入口（矩阵语义：无权限即无功能）`() {
        assertEquals(emptyList<HomeEntryId>(), homeEntryIds(emptyList()))
        assertEquals(emptyList<HomeEntryId>(), homeEntryIds(listOf("unknown.perm")))
        // sysadmin = 51 点全量 ⇒ 全入口（矩阵聚合结果，非角色特判）
        assertEquals(
            HomeEntryId.entries.toList(),
            homeEntryIds(FULL_PERMS),
        )
    }

    // ===== N-3：TabBar 三项与角色无关 =====

    @Test
    fun `N-3 TabBar 三项恒为 现场·追溯·我的`() {
        assertEquals(
            listOf("home", "trace", "me"),
            HOME_TABS.map { it.id },
        )
        assertEquals(
            listOf("现场", "追溯", "我的"),
            HOME_TABS.map { it.label },
        )
        // 全部角色一致：常量与 roles 解耦（三 APP 角色逐一遍历断言）
        listOf(RECV_ONLY_PERMS, PROD_PERMS, MGMT_PERMS, FULL_PERMS).forEach { perms ->
            assertEquals(3, HOME_TABS.size)
            assertEquals(listOf("现场", "追溯", "我的"), HOME_TABS.map { it.label })
            // 入口集合随权限变化，但追溯入口恒在（TabBar 追溯项对全部角色可达）
            assertTrue(HomeEntryId.TRACE in homeEntryIds(perms))
        }
    }

    // ===== 身份栏角色中文 =====

    @Test
    fun `身份栏角色中文 - 业务角色沿用 S01 口径，sysadmin 显示系统管理员`() {
        assertEquals("收货员", homeRoleLabel("receiver"))
        assertEquals("生产班组", homeRoleLabel("production"))
        assertEquals("管理层", homeRoleLabel("management"))
        assertEquals("销售", homeRoleLabel("sales"))
        assertEquals("系统管理员", homeRoleLabel("sysadmin"))
    }
}
