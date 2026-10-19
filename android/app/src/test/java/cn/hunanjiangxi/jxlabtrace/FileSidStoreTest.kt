package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.auth.FileSidStore
import cn.hunanjiangxi.jxlabtrace.auth.SidCipher
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * M3-2：FileSidStore 读写往返（加密封装接口注入 ⇒ 纯 JVM 单测，不依赖 Android 框架）。
 * FakeCipher 做确定性混淆，同时证明落盘字节不是明文（确经加密通道）。
 */
class FileSidStoreTest {

    /** 测试用可逆混淆：每字节 XOR 0x5A（0x5A 无 `#` 前缀，不触 J4 裸色值门禁）。 */
    private class FakeCipher : SidCipher {
        override fun encrypt(plain: ByteArray): ByteArray =
            ByteArray(plain.size) { i -> (plain[i].toInt() xor 0x5A).toByte() }

        override fun decrypt(blob: ByteArray): ByteArray = encrypt(blob) // XOR 自逆
    }

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = createTempDir(prefix = "sidstore")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun store() = FileSidStore(File(dir, "jx_sid.bin"), FakeCipher())

    @Test
    fun `写入后读取往返一致`() {
        val store = store()
        store.write("sid-value-abc123")
        assertEquals("sid-value-abc123", store.read())
    }

    @Test
    fun `跨实例（进程重启语义）仍可读出`() {
        store().write("persisted-sid")
        val reopened = FileSidStore(File(dir, "jx_sid.bin"), FakeCipher())
        assertEquals("persisted-sid", reopened.read())
    }

    @Test
    fun `落盘字节确经加密 - 非明文`() {
        val file = File(dir, "jx_sid.bin")
        val plain = "sid-value-abc123"
        FileSidStore(file, FakeCipher()).write(plain)
        val onDisk = file.readBytes()
        assertFalse("文件内容不得是明文 sid", String(onDisk, Charsets.UTF_8) == plain)
        // 且解密后与原文逐字节一致（往返无损）
        assertArrayEquals(
            plain.toByteArray(Charsets.UTF_8),
            FakeCipher().decrypt(onDisk),
        )
    }

    @Test
    fun `写 null 或空串 - 删除文件并读出 null`() {
        val store = store()
        store.write("to-be-cleared")
        store.write(null)
        assertFalse(File(dir, "jx_sid.bin").exists())
        assertNull(store.read())

        store.write("again")
        store.write("")
        assertNull(store.read())
    }

    @Test
    fun `文件不存在 - 读出 null 而非抛异常`() {
        assertNull(store().read())
    }
}
