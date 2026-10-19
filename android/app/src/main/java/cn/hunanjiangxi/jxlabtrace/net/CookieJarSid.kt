package cn.hunanjiangxi.jxlabtrace.net

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** `jx_sid` 会话 Cookie 的持久化抽象（便于单测注入内存实现）。 */
interface SidStore {
    fun read(): String?
    fun write(value: String?)
}

/** 进程重启不丢的 `jx_sid` 存储：EncryptedSharedPreferences。 */
class EncryptedSidStore(context: Context, fileName: String = "jx_session") : SidStore {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun read(): String? = prefs.getString(KEY_SID, null)?.takeIf { it.isNotBlank() }

    override fun write(value: String?) {
        prefs.edit().apply {
            if (value == null) remove(KEY_SID) else putString(KEY_SID, value)
        }.apply()
    }

    companion object {
        private const val KEY_SID = "jx_sid"
    }
}

/**
 * 自动携带/落盘 `jx_sid` 的 CookieJar：
 * 响应 Set-Cookie: jx_sid=… 时持久化；同域请求自动附带。
 */
class JxCookieJar(private val store: SidStore) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val sid = store.read() ?: return emptyList()
        val cookie = Cookie.Builder()
            .name(SID_COOKIE)
            .value(sid)
            .domain(url.host)
            .path("/")
            .httpOnly()
            .build()
        return listOf(cookie)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val sid = cookies.firstOrNull { it.name == SID_COOKIE }?.value
        if (sid != null) {
            store.write(sid.ifBlank { null })
        }
    }

    companion object {
        const val SID_COOKIE = "jx_sid"
    }
}

/** 仅供单测：内存版 SidStore。 */
class InMemorySidStore : SidStore {
    @Volatile
    private var value: String? = null

    override fun read(): String? = value
    override fun write(value: String?) {
        this.value = value
    }
}
