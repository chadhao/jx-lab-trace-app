package cn.hunanjiangxi.jxlabtrace.auth

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 当前用户能力表（GET /api/me）。 */
@Serializable
data class Me(
    val openId: String = "",
    val name: String = "",
    val roles: List<String> = emptyList(),
    val sysRoles: List<String> = emptyList(),
    /** 后端 /api/me 当前不返回 permissions 字段，保留字段以便口径升级后直接生效。 */
    val permissions: List<String> = emptyList(),
) {
    /** 业务角色 ∪ 系统角色（未分配角色判定：二者皆空）。 */
    val allRoles: List<String> get() = roles + sysRoles
}

/** 能力表本地缓存抽象（单测用内存实现，生产用 DataStore）。 */
interface MeStore {
    fun read(): Me?
    fun write(me: Me?)
}

/** 仅供单测：内存版 MeStore。 */
class InMemoryMeStore : MeStore {
    @Volatile
    private var value: Me? = null

    override fun read(): Me? = value
    override fun write(me: Me?) {
        value = me
    }
}

/**
 * 当前用户仓储：GET /api/me → [Me]，并落本地缓存供首页渲染（离线可读）。
 * 401 重授权由调用方经 `SessionCoordinator.withSessionRetry` 包裹（M3-3）。
 */
class MeRepository(
    private val api: ApiClient,
    private val store: MeStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    fun cached(): Me? = store.read()

    suspend fun refresh(): ApiResult<Me> {
        val result = api.executeRead("/api/me") { body -> parseMe(body, json) }
        if (result is ApiResult.Success) store.write(result.data)
        return result
    }

    companion object {
        fun parseMe(body: String, json: Json): Me {
            val obj = json.parseToJsonElement(body) as? JsonObject
                ?: error("/api/me 响应不是 JSON 对象")
            fun strings(key: String): List<String> =
                (obj[key] as? JsonArray)?.mapNotNull { it.asStringOrNull() } ?: emptyList()
            return Me(
                openId = obj["open_id"]?.asStringOrNull().orEmpty(),
                name = obj["name"]?.asStringOrNull().orEmpty(),
                roles = strings("roles"),
                sysRoles = strings("sys_roles"),
                permissions = strings("permissions"),
            )
        }

        private fun JsonElement.asStringOrNull(): String? =
            runCatching { jsonPrimitive.content }.getOrNull()
    }
}
