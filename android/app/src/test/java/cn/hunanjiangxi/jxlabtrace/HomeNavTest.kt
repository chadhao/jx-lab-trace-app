package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.ui.home.HOME_TABS
import cn.hunanjiangxi.jxlabtrace.ui.home.HomeEntryId
import cn.hunanjiangxi.jxlabtrace.ui.home.homeEntryIds
import cn.hunanjiangxi.jxlabtrace.ui.home.homeRoleLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * MIMO-TASK-S02-NAV 判据单测：
 * - N-2：权限矩阵驱动入口集合（首页入口三改 2026-10-23：**入口恒不含 TRACE**——追溯由 TabBar 提供）；
 * - N-3：TabBar 三项恒为「现场·追溯·我的」，与权限无关。
 */
class HomeNavTest {

    // 权限矩阵驱动用例的权限点集（2026-10-23：me.permissions 上线后口径）
    private val FULL_PERMS = listOf("recv.arrive.confirm","recv.weigh","recv.bag.gen","ship.load.scan","out.register","sample.take","prod.batch.create","prod.feed.scan","prod.op.log","prod.fg.gen","trace.forward","trace.backward","trace.batch.view","rpt.view")
    /** 收货域：仅 recv.*（装车出场属 ship 域，不混入本用例）。 */
    private val RECV_PERMS = listOf("recv.arrive.confirm","recv.weigh","recv.bag.gen")
    private val PROD_PERMS = listOf("sample.take","prod.batch.create","prod.feed.scan","prod.op.log","prod.fg.gen","trace.backward")
    private val MGMT_PERMS = listOf("trace.forward","trace.backward","trace.batch.view","rpt.view")

    // ===== N-2：权限 → 入口集合（矩阵语义，恒无 TRACE） =====

    @Test
    fun `N-2 收货域 - RECV与BAG`() {
        assertEquals(
            setOf(HomeEntryId.RECV, HomeEntryId.BAG),
            homeEntryIds(RECV_PERMS).toSet(),
        )
    }

    @Test
    fun `N-2 生产域 - 生产五项`() {
        assertEquals(
            setOf(
                HomeEntryId.SAMPLE, HomeEntryId.BATCH, HomeEntryId.FEED,
                HomeEntryId.OPERATION, HomeEntryId.FG_PRINT,
            ),
            homeEntryIds(PROD_PERMS).toSet(),
        )
    }

    @Test
    fun `N-2 管理层 - 仅REPORT`() {
        assertEquals(
            setOf(HomeEntryId.REPORT),
            homeEntryIds(MGMT_PERMS).toSet(),
        )
    }

    @Test
    fun `N-2 多角色 - 入口取并集且无TRACE`() {
        assertEquals(
            setOf(
                HomeEntryId.RECV, HomeEntryId.BAG,
                HomeEntryId.SAMPLE, HomeEntryId.BATCH, HomeEntryId.FEED,
                HomeEntryId.OPERATION, HomeEntryId.FG_PRINT,
            ),
            homeEntryIds(RECV_PERMS + PROD_PERMS).toSet(),
        )
        assertEquals(
            setOf(
                HomeEntryId.RECV, HomeEntryId.BAG, HomeEntryId.REPORT,
            ),
            homeEntryIds(RECV_PERMS + MGMT_PERMS).toSet(),
        )
    }

    @Test
    fun `N-2 无权限点 - 空列表（矩阵语义：无权限即无功能）`() {
        assertEquals(emptyList<HomeEntryId>(), homeEntryIds(emptyList()))
        assertEquals(emptyList<HomeEntryId>(), homeEntryIds(listOf("unknown.perm")))
    }

    @Test
    fun `N-2 全量权限 - entries去TRACE`() {
        assertEquals(
            HomeEntryId.entries.filter { it != HomeEntryId.TRACE },
            homeEntryIds(FULL_PERMS),
        )
        // 入口列表任何权限组合下都不含 TRACE
        listOf(RECV_PERMS, PROD_PERMS, MGMT_PERMS, FULL_PERMS, emptyList()).forEach { perms ->
            assertFalse(
                "入口列表不应含 TRACE：perms=$perms",
                HomeEntryId.TRACE in homeEntryIds(perms),
            )
        }
    }

    // ===== N-3：TabBar 三项与权限无关 =====

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
        // 全部权限组合一致：常量与权限解耦；TRACE 恒由 TabBar 提供，入口列表不含
        listOf(RECV_PERMS, PROD_PERMS, MGMT_PERMS, FULL_PERMS).forEach { perms ->
            assertEquals(3, HOME_TABS.size)
            assertEquals(listOf("现场", "追溯", "我的"), HOME_TABS.map { it.label })
            assertFalse(
                "TRACE 由 TabBar 提供，入口列表不含：perms=$perms",
                HomeEntryId.TRACE in homeEntryIds(perms),
            )
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
