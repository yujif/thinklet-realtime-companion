package com.yujif.thinklet.realtimecompanion.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class ApiKeyCipherTest {
    private val cipher = ApiKeyCipher(SecretKeySpec(ByteArray(32) { it.toByte() }, "AES"))

    @Test
    fun encryptsAndDecryptsTheApiKey() {
        val encrypted = cipher.encrypt("test-api-key")

        assertNotEquals("test-api-key", encrypted)
        assertEquals("test-api-key", cipher.decrypt(encrypted))
    }

    @Test
    fun rejectsMalformedCiphertextWithoutThrowing() {
        assertNull(cipher.decrypt("not-a-valid-ciphertext"))
    }
}
