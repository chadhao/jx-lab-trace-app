package cn.hunanjiangxi.jxlabtrace.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

private val Context.meDataStore: DataStore<Preferences> by preferencesDataStore(name = "me_cache")

/**
 * 能力表 DataStore 缓存：进程重启后首页仍能渲染身份，联网后 [MeRepository.refresh] 覆盖。
 * 读写均为单 key 小操作，runBlocking 收口以满足 [MeStore] 的同步接口。
 */
class DataStoreMeStore(private val context: Context) : MeStore {

    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): Me? = runBlocking {
        val raw = context.meDataStore.data.first()[KEY] ?: return@runBlocking null
        runCatching { json.decodeFromString(Me.serializer(), raw) }.getOrNull()
    }

    override fun write(me: Me?) {
        val raw = me?.let { json.encodeToString(Me.serializer(), it) }
        runBlocking {
            context.meDataStore.edit { prefs ->
                if (raw == null) prefs.remove(KEY) else prefs[KEY] = raw
            }
        }
    }

    companion object {
        private val KEY = stringPreferencesKey("me_json")
    }
}
