package cn.hunanjiangxi.jxlabtrace.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import cn.hunanjiangxi.jxlabtrace.net.SidStore
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * `jx_sid` 落盘文件的加密封装（接口注入 ⇒ FileSidStore 可脱离 Android 框架做 JVM 往返单测，M3-2）。
 * 生产实现 [KeystoreSidCipher] 走 Android Keystore，密钥不可导出。
 */
interface SidCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/**
 * 文件版 `jx_sid` 存储：内容经 [SidCipher] 加密后写入 [file]，进程重启不丢。
 * B2 起为生产实现（替换测试用 InMemory）；加密封装可注入以便单测。
 */
class FileSidStore(
    private val file: File,
    private val cipher: SidCipher,
) : SidStore {

    override fun read(): String? = synchronized(lock) {
        if (!file.exists()) return null
        runCatching {
            cipher.decrypt(file.readBytes()).toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    override fun write(value: String?) = synchronized(lock) {
        if (value.isNullOrBlank()) {
            file.delete()
        } else {
            file.parentFile?.mkdirs()
            file.writeBytes(cipher.encrypt(value.toByteArray(Charsets.UTF_8)))
        }
    }

    private val lock = Any()
}

/**
 * Android Keystore 封装的 AES-256-GCM 加密（密钥存在 Keystore，不进应用数据目录）。
 * 仅在真机/带 Keystore 的环境可用；单测注入替代实现。
 */
class KeystoreSidCipher : SidCipher {

    private val key: SecretKey by lazy { obtainKey() }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        return iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        val iv = blob.copyOfRange(0, GCM_IV_LENGTH)
        val body = blob.copyOfRange(GCM_IV_LENGTH, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(body)
    }

    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jx_sid_store"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }
}
