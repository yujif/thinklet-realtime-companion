package com.yujif.thinklet.realtimecompanion.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiLogRedactorTest {
    @Test
    fun redactsBearerTokensAndExplicitApiKeys() {
        val redacted = ApiLogRedactor.redact(
            """{"Authorization":"Bearer sk-test-secret","api_key":"sk-other-secret","body":"ok"}""",
        )

        assertFalse(redacted.contains("sk-test-secret"))
        assertFalse(redacted.contains("sk-other-secret"))
        assertTrue(redacted.contains("Bearer [REDACTED]"))
        assertTrue(redacted.contains(""""api_key":"[REDACTED]""""))
    }
}
