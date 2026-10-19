package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.net.ApiError
import cn.hunanjiangxi.jxlabtrace.net.ErrorMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M12：ErrorMessages 单测 —— ApiError 各类中文话术 + 三类 409 业务码专属话术。
 */
class ErrorMessagesTest {

    @Test
    fun `五类错误 - 均有不同中文话术`() {
        val samples = listOf(
            ApiError.Offline() to "网络",
            ApiError.Unauthorized() to "登录已过期",
            ApiError.Forbidden() to "你的角色无此权限",
            ApiError.Server(500) to "服务器繁忙",
            ApiError.Biz("OTHER_CODE", "某个业务失败了") to "某个业务失败了",
            ApiError.Parse(null) to "数据解析失败",
        )
        samples.forEach { (error, expectFragment) ->
            val message = ErrorMessages.of(error)
            assertTrue(
                "${error.javaClass.simpleName} 话术应含「$expectFragment」，实际：$message",
                message.contains(expectFragment),
            )
            assertTrue("不得为空", message.isNotBlank())
        }

        // 互不相同
        val messages = samples.map { ErrorMessages.of(it.first) }
        assertEquals(messages.size, messages.toSet().size)
    }

    @Test
    fun `Forbidden 话术 - SCOPE 规定原文且不含技术码`() {
        val message = ErrorMessages.of(ApiError.Forbidden())
        assertEquals("你的角色无此权限，请联系综合运营部。", message)
        assertTrue("不得暴露 HTTP 技术码", !message.contains("403"))
    }

    @Test
    fun `三类 409 业务码 - 专属话术`() {
        val fed = ErrorMessages.of(ApiError.Biz("ALREADY_FED", "该袋已投料"))
        assertTrue("ALREADY_FED 应给专属话术，实际：$fed", fed.contains("投料更正"))
        assertTrue("应说明已投料", fed.contains("已投料"))

        val voided = ErrorMessages.of(ApiError.Biz("ALREADY_VOID", "已作废"))
        assertTrue("ALREADY_VOID 应指回追溯，实际：$voided", voided.contains("追溯"))

        val noReturn = ErrorMessages.of(ApiError.Biz("NO_RETURN_DECISION", "无退货判定"))
        assertTrue("NO_RETURN_DECISION 应给出路，实际：$noReturn", noReturn.contains("综合运营部"))

        // 三者互不相同
        val set = setOf(fed, voided, noReturn)
        assertEquals("三类 409 话术应互不相同", 3, set.size)
    }

    @Test
    fun `未知业务码 - 透传后端 message 且兜底不空`() {
        val withMessage = ErrorMessages.of(ApiError.Biz("WHATEVER", "后端给的中文原因"))
        assertEquals("后端给的中文原因", withMessage)

        val blank = ErrorMessages.of(ApiError.Biz("WHATEVER", ""))
        assertTrue("空 message 应有兜底话术", blank.isNotBlank())
        assertNotEquals("", blank)
    }
}
