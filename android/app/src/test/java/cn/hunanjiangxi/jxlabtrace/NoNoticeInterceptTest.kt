package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.recv.DialActions
import cn.hunanjiangxi.jxlabtrace.recv.NoNoticeModel
import cn.hunanjiangxi.jxlabtrace.recv.NoticesFilter
import cn.hunanjiangxi.jxlabtrace.recv.RecvNotice
import cn.hunanjiangxi.jxlabtrace.recv.RecvRepository
import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M5-1：无预报拦截页 —— 不产生任何写请求（零 POST）+ 拨号 Intent 构造正确。
 */
class NoNoticeInterceptTest {

    private lateinit var server: MockWebServer
    private val writeRequests = java.util.Collections.synchronizedList(ArrayList<RecordedRequest>())

    private val notices = listOf(
        RecvNotice(id = 30, plate_no = "湘F·A1234", status = "已到货", seq_no = 1, arrive_date = "2026-10-18"),
        RecvNotice(id = 20, plate_no = "湘F·A1234", status = "已到货", seq_no = 2, arrive_date = "2026-10-16"),
        RecvNotice(id = 10, plate_no = "湘F·A1234", status = "已到货", seq_no = 1, arrive_date = "2026-10-15"),
        RecvNotice(id = 5, plate_no = "湘E·B6677", status = "预报", seq_no = 4, arrive_date = ""),
        RecvNotice(id = 4, plate_no = "湘E·B6677", status = "预报", seq_no = 3, arrive_date = ""),
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method != "GET" && request.method != "HEAD") {
                    writeRequests.add(request)
                }
                return MockResponse().setResponseCode(200).setBody("""{"count":0,"rows":[]}""")
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `拦截页操作零写请求 - 只读本地过滤与拨号`() = runTest {
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
        )
        val repo = RecvRepository(api)
        val model = NoNoticeModel { notices }

        // 预报拉取（GET）—— 允许
        repo.listNotices()

        // 拦截页动作：本地过滤最近 3 条 + 构造拨号（不触任何写接口）
        val recent = model.recentNotices("湘F·A1234")
        assertEquals("最近 3 条", 3, recent.size)
        assertEquals(listOf(30L, 20L, 10L), recent.map { it.id })
        val dial = model.dialIntent()
        assertEquals(DialActions.ACTION_DIAL, dial.action)
        assertEquals("tel:${DialActions.OPERATIONS_PHONE}", dial.uri)

        // 零 POST / PUT / PATCH / DELETE
        assertTrue("拦截路径不得产生写请求，实际=${writeRequests.map { it.method }}", writeRequests.isEmpty())
    }

    @Test
    fun `拨号 Intent 构造正确`() {
        val dial = DialActions.operationsDial()
        assertEquals("android.intent.action.DIAL", dial.action)
        assertTrue(dial.uri.startsWith("tel:"))
        assertEquals("tel:0730-xxxxxxx", dial.uri)
    }

    @Test
    fun `无今日预报判定 - 该车牌被拦截`() {
        val today = LocalDate.of(2026, 10, 20)
        // 湘F·A1234 今日无预报（历史 3 条均已完成）⇒ 拦截
        assertFalse(NoticesFilter.hasTodayNotice(notices, "湘F·A1234", today))
        // 湘E·B6677 有今日预报 ⇒ 不拦
        assertTrue(NoticesFilter.hasTodayNotice(notices, "湘E·B6677", today))
    }
}
