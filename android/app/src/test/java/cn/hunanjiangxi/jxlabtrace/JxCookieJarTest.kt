package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** jx_sid：响应落盘、请求自动携带、清空后不带。 */
class JxCookieJarTest {

    private val store = InMemorySidStore()
    private val jar = JxCookieJar(store)
    private val apiBase = "http://api.example.com/".toHttpUrl()

    @Test
    fun `响应中的 jx_sid 被持久化`() {
        val setCookie = Cookie.Builder()
            .name("jx_sid")
            .value("sid-abc-123")
            .domain("api.example.com")
            .path("/")
            .httpOnly()
            .build()
        jar.saveFromResponse(apiBase, listOf(setCookie))
        assertEquals("sid-abc-123", store.read())
    }

    @Test
    fun `请求自动携带 jx_sid`() {
        store.write("sid-xyz-789")
        val cookies = jar.loadForRequest(apiBase)
        assertEquals(1, cookies.size)
        val cookie = cookies.single()
        assertEquals("jx_sid", cookie.name)
        assertEquals("sid-xyz-789", cookie.value)
    }

    @Test
    fun `sid 清空后请求不携带`() {
        store.write(null)
        assertTrue(jar.loadForRequest(apiBase).isEmpty())
    }

    @Test
    fun `空字符串视为已登出`() {
        val setCookie = Cookie.Builder().name("jx_sid").value("").domain("api.example.com").path("/").build()
        jar.saveFromResponse(apiBase, listOf(setCookie))
        assertNull(store.read())
    }
}
