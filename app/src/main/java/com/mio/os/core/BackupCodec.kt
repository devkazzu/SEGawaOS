package com.mio.os.core

import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup file format plus optional password encryption.
 *
 * Plain format: `{"v":2,"parts":{name:json,...}}`. Encrypted format: base64
 * of `salt(16) + iv(12) + AES-256-GCM(plain)` with the key derived as
 * `SHA-256(password || salt)`. Uses only JDK + `org.json` APIs so the
 * round-trip is covered by JVM unit tests.
 */
object BackupCodec {

    const val CURRENT = 2

    fun pack(parts: Map<String, String>): String {
        val o = JSONObject().put("v", CURRENT)
        val p = JSONObject()
        parts.forEach { (k, v) -> p.put(k, v) }
        o.put("parts", p)
        return o.toString()
    }

    fun unpack(raw: String): Map<String, String>? = runCatching {
        val o = JSONObject(raw)
        if (o.optInt("v", 0) !in 1..CURRENT) return null
        val p = o.getJSONObject("parts")
        p.keys().asSequence().associateWith { p.optString(it, "") }
    }.getOrNull()

    fun encrypt(password: String, plain: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        }
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(salt + iv + ct)
    }

    fun decrypt(password: String, packed: String): String? = runCatching {
        val all = Base64.getDecoder().decode(packed)
        require(all.size > 28)
        val salt = all.copyOfRange(0, 16)
        val iv = all.copyOfRange(16, 28)
        val ct = all.copyOfRange(28, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        }
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray(Charsets.UTF_8) + salt)
        return SecretKeySpec(digest, "AES")
    }
}
