package com.yujif.thinklet.realtimecompanion.security

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM envelope stored as base64(iv):base64(ciphertext-and-tag). */
class ApiKeyCipher(private val key: SecretKey) {
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return listOf(
            Base64.getEncoder().encodeToString(cipher.iv),
            Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))),
        ).joinToString(DELIMITER)
    }

    fun decrypt(envelope: String): String? {
        return runCatching {
            val parts = envelope.split(DELIMITER, limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.getDecoder().decode(parts[0])),
            )
            cipher.doFinal(Base64.getDecoder().decode(parts[1])).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val DELIMITER = ":"
    }
}
