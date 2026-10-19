package cn.hunanjiangxi.jxlabtrace.scan

/**
 * 手输组装（S03 ④ / spec `code_faces.manual_entry`）：
 * 「先选父对象，再输本层序号」——**不提供 27 位裸串输入框**。
 * 继承父码的 V/BT/客户/物料/日期，按对象深度填序段，校验位系统计算。
 */
object ManualEntry {

    /** 手输支持的子对象 → 允许的父对象类型（spec key_rule 链嵌套）。 */
    val ALLOWED_PARENT: Map<Char, Char> = mapOf(
        'B' to 'A', // 原料吨袋 ← 车次
        'D' to 'C', // 成品批 ← 生产批
        'E' to 'D', // 成品吨袋 ← 成品批
    )

    sealed interface Result {
        data class Ok(val full: String, val human: String) : Result
        data class Rejected(val reason: String) : Result
    }

    /**
     * 组装子对象全码。
     * @param childType 子对象类型（A-F）
     * @param parentRaw 父对象码（27 位裸串或人读行）
     * @param levelSeq 本层序号（十进制，按序段宽度左补零）
     */
    fun assemble(childType: Char, parentRaw: String, levelSeq: Int): Result {
        if (childType == 'F') {
            // F（来料批次）三段序为创建时刻，不走「父对象+序号」路径。
            return Result.Rejected("来料批次不支持手输（创建时刻入码）")
        }
        val expectedParent = ALLOWED_PARENT[childType]
            ?: return Result.Rejected("类型 $childType 不支持手输，请扫码")
        if (levelSeq < 0) return Result.Rejected("序号不能为负数")

        val parent = when (val parsed = CodeParser.parse(parentRaw)) {
            is CodeParser.Result.Ok -> parsed.code
            is CodeParser.Result.Rejected ->
                return Result.Rejected("父对象码无效：${parsed.reject.message}")
        }
        if (parent.objectType != expectedParent) {
            return Result.Rejected(
                "父对象应为「${CodeParser.OBJECT_TYPE_NAMES[expectedParent]}」，" +
                    "当前选择的是「${parent.typeLabel}」",
            )
        }

        // 序段布局（spec key_rule）：本层填自己那段，未用到的更深层占位 000。
        val (seq1, seq2, seq3) = when (childType) {
            'B', 'D' -> Triple(parent.seq1, levelSeq.pad(3), "000")
            'E' -> Triple(parent.seq1, parent.seq2, levelSeq.pad(3))
            // 深度 1（A/C）为链根本身，无父可选；到不了这里，但保持穷尽。
            else -> Triple(levelSeq.pad(2), "000", "000")
        }
        if (childType in listOf('B', 'D', 'E') && levelSeq > 999) {
            return Result.Rejected("序号超出范围（最大 999）")
        }

        val payload = buildString {
            append(parent.version)
            append(childType)
            append(parent.bizType)
            append(parent.customer)
            append(parent.material)
            append(parent.date)
            append(seq1)
            append(seq2)
            append(seq3)
        }
        require(payload.length == CodeParser.PAYLOAD_LENGTH)
        val full = payload + CodeParser.checkChar(payload)
        return Result.Ok(full = full, human = CodeParser.toHuman(full))
    }

    private fun Int.pad(width: Int): String = toString().padStart(width, '0').takeLast(width)
}
