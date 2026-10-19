package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.scan.CodeParser
import cn.hunanjiangxi.jxlabtrace.scan.ManualEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M4-1：CodeParser 单测 —— spec `code-rules.json` 的 `sample_vectors` 全量向量逐条断言
 * （解析 + 校验 + 人读行双向转换），非法校验位全部拒绝，人读行⇄裸串互转无损。
 *
 * 向量硬编码自后端 spec/code-rules.json（frozen v1，2026-10-08）：
 */
class CodeParserTest {

    /** payload → expected full/human/weighted_sum/check（与 spec sample_vectors 逐字段一致）。 */
    private data class Vector(
        val type: Char,
        val payload: String,
        val weightedSum: Int,
        val remainder: Int,
        val check: Char,
        val full: String,
        val human: String,
    )

    private val vectors = listOf(
        Vector('A', "1ACG0001000226100703000000", 93, 21, 'L',
            "1ACG0001000226100703000000L", "1A-CG-0001-0002-261007-03-000-000-L"),
        Vector('B', "1BCG0001000226100703007000", 115, 7, '7',
            "1BCG00010002261007030070007", "1B-CG-0001-0002-261007-03-007-000-7"),
        Vector('C', "1CCG0001000326100703000000", 96, 24, 'O',
            "1CCG0001000326100703000000O", "1C-CG-0001-0003-261007-03-000-000-O"),
        Vector('D', "1DCG0001000326100703002000", 103, 31, 'V',
            "1DCG0001000326100703002000V", "1D-CG-0001-0003-261007-03-002-000-V"),
        Vector('E', "1ECG0001000326100703002015", 112, 4, '4',
            "1ECG00010003261007030020154", "1E-CG-0001-0003-261007-03-002-015-4"),
        Vector('F', "1FCG0001000226101414045030", 130, 22, 'M',
            "1FCG0001000226101414045030M", "1F-CG-0001-0002-261014-14-045-030-M"),
    )

    @Test
    fun `sample_vectors - 校验字符逐条与 spec 一致`() {
        vectors.forEach { v ->
            assertEquals("类型 ${v.type} 校验字符", v.check, CodeParser.checkChar(v.payload))
            assertEquals("类型 ${v.type} full", v.full, v.payload + v.check)
        }
    }

    @Test
    fun `sample_vectors - 解析逐条通过并还原各段`() {
        vectors.forEach { v ->
            val result = CodeParser.parse(v.full)
            assertTrue("类型 ${v.type} 应解析成功，实际 $result", result is CodeParser.Result.Ok)
            val parsed = (result as CodeParser.Result.Ok).code
            assertEquals(v.type, parsed.objectType)
            assertEquals("CG", parsed.bizType)
            assertTrue("full 应为 27 位", parsed.full.length == 27)
            assertEquals(v.full, parsed.full)
            assertEquals(v.human, parsed.human)
        }
    }

    @Test
    fun `sample_vectors - 人读行与裸串双向无损`() {
        vectors.forEach { v ->
            assertEquals(v.human, CodeParser.toHuman(v.full))
            val back = CodeParser.fromHuman(v.human)
            assertTrue("人读行应可回裸串：${v.human}", back is CodeParser.Result.Ok)
            assertEquals(v.full, (back as CodeParser.Result.Ok).code.full)
            // 再转回人读行（往返两次仍无损）
            assertEquals(v.human, CodeParser.toHuman((back as CodeParser.Result.Ok).code.full))
        }
    }

    @Test
    fun `非法校验位 - 全部拒绝`() {
        vectors.forEach { v ->
            val alphabet = CodeParser.ALPHABET
            val goodIndex = alphabet.indexOf(v.check)
            val badCheck = alphabet[(goodIndex + 1) % alphabet.length]
            val badFull = v.payload + badCheck
            val result = CodeParser.parse(badFull)
            assertTrue("坏校验位应拒绝：$badFull", result is CodeParser.Result.Rejected)
            assertEquals(
                CodeParser.Reject.CHECKSUM,
                (result as CodeParser.Result.Rejected).reject,
            )
        }
    }

    @Test
    fun `长度与版本类型非法 - 按 parse_steps 拒绝`() {
        assertEquals(
            CodeParser.Reject.LENGTH,
            (CodeParser.parse("1ACG0001000226100703000000") as CodeParser.Result.Rejected).reject,
        )
        assertEquals(
            CodeParser.Reject.VERSION,
            (CodeParser.parse("0ACG0001000226100703000000X") as CodeParser.Result.Rejected).reject,
        )
        assertEquals(
            CodeParser.Reject.TYPE,
            (CodeParser.parse("1ZCG0001000226100703000000X") as CodeParser.Result.Rejected).reject,
        )
        // 别系统的码（后端 400 样例）—— 归一化后长度不对
        assertEquals(
            CodeParser.Reject.LENGTH,
            (CodeParser.parse("NOT-A-JX-CODE-000000000000000") as CodeParser.Result.Rejected).reject,
        )
    }

    @Test
    fun `人读行分组宽度与 spec human_groups 一致`() {
        assertEquals(listOf(2, 2, 4, 4, 6, 2, 3, 3, 1), CodeParser.HUMAN_GROUPS)
        // 27 = 分组总宽
        assertEquals(27, CodeParser.HUMAN_GROUPS.sum())
        // 容忍空格分隔的人读行
        val spaced = vectors[1].human.replace('-', ' ')
        val result = CodeParser.fromHuman(spaced)
        assertTrue(result is CodeParser.Result.Ok)
        assertEquals(vectors[1].full, (result as CodeParser.Result.Ok).code.full)
    }

    @Test
    fun `手输组装 - 与 spec 车次样例同参且校验位正确`() {
        // 父=A 车次（样例 1），子=B 袋序 7 ⇒ 应得到 spec 的 B 样例 full
        val assembled = ManualEntry.assemble('B', vectors[0].full, 7)
        assertTrue("手输应成功，实际 $assembled", assembled is ManualEntry.Result.Ok)
        assertEquals(vectors[1].full, (assembled as ManualEntry.Result.Ok).full)
        assertEquals(vectors[1].human, assembled.human)
    }

    @Test
    fun `手输组装 - 父子类型不匹配与不支持类型均拒绝`() {
        // B 的父必须是 A 车次；喂 C 生产批应拒绝
        val mismatch = ManualEntry.assemble('B', vectors[2].full, 7)
        assertTrue(mismatch is ManualEntry.Result.Rejected)

        // F 不支持「父对象+序号」手输
        val f = ManualEntry.assemble('F', vectors[0].full, 1)
        assertTrue(f is ManualEntry.Result.Rejected)

        // 非法父码
        val badParent = ManualEntry.assemble('B', "not-a-code", 7)
        assertTrue(badParent is ManualEntry.Result.Rejected)
    }
}
