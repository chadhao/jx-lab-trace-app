package cn.hunanjiangxi.jxlabtrace

import android.app.Application
import cn.hunanjiangxi.jxlabtrace.net.NetworkMonitor

/** 应用壳：装配全局单例（网络监听等）。业务装配从 B2 起扩展。 */
class JxLabTraceApp : Application() {

    lateinit var networkMonitor: NetworkMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        networkMonitor = NetworkMonitor(this).also { it.start() }
    }

    companion object {
        lateinit var instance: JxLabTraceApp
            private set
    }
}
