package com.yujif.thinklet.realtimecompanion.core

object ApiLogRedactor {
    private val bearerRegex = Regex("Bearer\\s+[^\"\\s,}]+")
    private val apiKeyRegex = Regex(""""api_key"\s*:\s*"[^"]*"""")

    fun redact(value: String): String {
        return value
            .replace(bearerRegex, "Bearer [REDACTED]")
            .replace(apiKeyRegex, """"api_key":"[REDACTED]"""")
    }
}
