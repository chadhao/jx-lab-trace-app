package cn.hunanjiangxi.jxlabtrace

import android.app.Application
import cn.hunanjiangxi.jxlabtrace.auth.DataStoreMeStore
import cn.hunanjiangxi.jxlabtrace.auth.FileSidStore
import cn.hunanjiangxi.jxlabtrace.auth.KeystoreSidCipher
import cn.hunanjiangxi.jxlabtrace.auth.MeRepository
import cn.hunanjiangxi.jxlabtrace.auth.ReauthBridge
import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.NetworkMonitor
import cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator
import java.io.File

/** 应用壳：装配全局单例（网络监听、会话、当前用户缓存）。 */
class JxLabTraceApp : Application() {

    lateinit var networkMonitor: NetworkMonitor
        private set

    lateinit var sidStore: FileSidStore
        private set

    lateinit var apiClient: ApiClient
        private set

    lateinit var session: SessionCoordinator
        private set

    lateinit var meRepository: MeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        networkMonitor = NetworkMonitor(this).also { it.start() }

        // M03：jx_sid 文件持久化（Keystore 加密），注入 OkHttp CookieJar。
        sidStore = FileSidStore(File(filesDir, SID_FILE), KeystoreSidCipher())
        // M12：online 门禁接 NetworkMonitor —— 离线时读写请求一律不发出。
        apiClient = ApiClient(
            baseUrl = BuildConfig.BASE_URL,
            cookieJar = JxCookieJar(sidStore),
            online = { networkMonitor.online.value },
        )
        // M03：401 ⇒ SessionExpired 事件 ⇒ 全屏原位重授权 ⇒ 重放原请求。
        session = SessionCoordinator(reauthorize = { ReauthBridge.awaitReauth(this) })
        meRepository = MeRepository(apiClient, DataStoreMeStore(this))
    }

    companion object {
        private const val SID_FILE = "jx_sid.bin"

        lateinit var instance: JxLabTraceApp
            private set
    }
}
