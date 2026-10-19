package cn.hunanjiangxi.jxlabtrace.scan

/**
 * 27 位追踪码解析（纯 Kotlin，零 Android 依赖）。
 *
 * 口径唯一来源：后端 `spec/code-rules.json`（frozen v1）：
 * - 结构：V(1) T(1) BT(2) CUSTOMER(4) MATERIAL(4) DATE(6) SEQ1(2) SEQ2(3) SEQ3(3) + 校验(1) = 27；
 * - 校验：weighted_mod，从右向左权重 1/3 交替，字母表下标求和 mod 36；
 * - 人读行分组：`2-2-4-4-6-2-3-3-1`（V+T 合并成 2 位对象头），与裸串严格互转；
 * - 手输不设 27 位裸串（`code_faces.manual_entry`）——由 [ManualEntry] 承担。
 */
object CodeParser {

    const val TOTAL_LENGTH = 27
    const val PAYLOAD_LENGTH = 26
    const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val VERSION_PATTERN = "123456789"
    const val OBJECT_TYPES = "ABCDEF"
    val HUMAN_GROUPS = listOf(2, 2, 4, 4, 6, 2, 3, 3, 1)

    /** 对象类型中文（spec object_types）。 */
    val OBJECT_TYPE_NAMES: Map<Char, String> = mapOf(
        'A' to "车次",
        'B' to "原料吨袋",
        'C' to "生产批",
        'D' to "成品批",
        'E' to "成品吨袋",
        'F' to "来料批次",
    )

    /** 解析拒绝原因（spec parse_steps 前四步）。 */
    enum class Reject(val message: String) {
        LENGTH("长度不是 27 位"),
        VERSION("编码版本非法"),
        TYPE("对象类型非法"),
        CHECKSUM("校验位不匹配"),
    }

    /** 解析结果。 */
    sealed interface Result {
        data class Ok(val code: ParsedCode) : Result
        data class Rejected(val reject: Reject) : Result
    }

    /** 已通过校验的码。 */
    data class ParsedCode(
        val version: Char,
        val objectType: Char,
        val bizType: String,
        val customer: String,
        val material: String,
        val date: String,
        val seq1: String,
        val seq2: String,
        val seq3: String,
        val full: String,
        val human: String,
    ) {
        val typeLabel: String get() = OBJECT_TYPE_NAMES[objectType].orEmpty()
    }

    /** 归一化：只保留字母表字符（0-9A-Z）并大写——相机/HID/手输/人读行四路入参统一口径。 */
    fun normalize(raw: String): String = raw.filter { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' }.uppercase()

    /** 按 spec check_algorithm 计算 26 位 payload 的校验字符（从右向左，权 1/3 交替）。 */
    fun checkChar(payload: String): Char {
        require(payload.length == PAYLOAD_LENGTH) { "payload 必须 26 位，实际 ${payload.length}" }
        var sum = 0
        for (i in payload.indices.reversed()) {
            val value = ALPHABET.indexOf(payload[i].uppercaseChar())
            val weight = if ((PAYLOAD_LENGTH - 1 - i) % 2 == 0) 1 else 3
            sum += value * weight
        }
        return ALPHABET[sum % 36]
    }

    /** 解析裸串/任意入参（自动归一化）。 */
    fun parse(raw: String): Result {
        val code = normalize(raw)
        if (code.length != TOTAL_LENGTH) return Result.Rejected(Reject.LENGTH)
        val version = code[0]
        if (version !in VERSION_PATTERN) return Result.Rejected(Reject.VERSION)
        val type = code[1]
        if (type !in OBJECT_TYPES) return Result.Rejected(Reject.TYPE)

        val payload = code.substring(0, PAYLOAD_LENGTH)
        val check = code[PAYLOAD_LENGTH - 1]
        if (checkChar(payload) != check) return Result.Rejected(Reject.CHECKSUM)

        var cursor = 0
        fun take(width: Int): String = code.substring(cursor, cursor + width).also { cursor += width }

        val parsed = ParsedCode(
            version = version,
            objectType = type,
            bizType = take(2),
            customer = take(4),
            material = take(4),
            date = take(6),
            seq1 = take(2),
            seq2 = take(3),
            seq3 = take(3),
            full = code,
            human = "",
        )
        return Result.Ok(parsed.copy(human = toHuman(code)))
    }

    /** 裸串 → 人读行（`2-2-4-4-6-2-3-3-1` 分组，连字符连接）。 */
    fun toHuman(raw: String): String {
        val code = normalize(raw)
        require(code.length == TOTAL_LENGTH) { "必须 27 位才能转人读行" }
        val out = StringBuilder()
        var cursor = 0
        HUMAN_GROUPS.forEachIndexed { index, width ->
            if (index > 0) out.append('-')
            out.append(code, cursor, cursor + width)
            cursor += width
        }
        return out.toString()
    }

    /** 人读行 → 裸串（容忍空格分隔；分组宽度必须与 2-2-4-4-6-2-3-3-1 完全一致）。 */
    fun fromHuman(human: String): Result {
        val groups = human.trim().split(Regex("""[\s\-]+""")).filter { it.isNotEmpty() }
        if (groups.size != HUMAN_GROUPS.size) return Result.Rejected(Reject.LENGTH)
        groups.forEachIndexed { i, g ->
            if (g.length != HUMAN_GROUPS[i]) return Result.Rejected(Reject.LENGTH)
        }
        return parse(groups.joinToString(""))
    }
}
