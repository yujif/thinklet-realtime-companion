package com.yujif.thinklet.realtimecompanion.core

data class RealtimeUsage(
    val audioInputTokens: Long = 0,
    val audioOutputTokens: Long = 0,
    val textInputTokens: Long = 0,
    val textOutputTokens: Long = 0,
    val imageInputTokens: Long = 0,
)

class CostTracker(
    private val maxCostUsd: Double,
) {
    var estimatedCostUsd: Double = 0.0
        private set

    val isOverBudget: Boolean
        get() = estimatedCostUsd >= maxCostUsd

    fun addUsage(usage: RealtimeUsage) {
        estimatedCostUsd += usage.audioInputTokens * AUDIO_INPUT_USD_PER_TOKEN
        estimatedCostUsd += usage.audioOutputTokens * AUDIO_OUTPUT_USD_PER_TOKEN
        estimatedCostUsd += usage.textInputTokens * TEXT_INPUT_USD_PER_TOKEN
        estimatedCostUsd += usage.textOutputTokens * TEXT_OUTPUT_USD_PER_TOKEN
        estimatedCostUsd += usage.imageInputTokens * IMAGE_INPUT_USD_PER_TOKEN
    }

    private companion object {
        // gpt-realtime-2 per-token prices, verified 2026-06-21 against
        // https://developers.openai.com/api/docs/pricing. OpenAI can reprice at any time;
        // re-check that page before trusting the budget guard (maxCostUsd) against real spend.
        const val AUDIO_INPUT_USD_PER_TOKEN = 32.0 / 1_000_000.0
        const val AUDIO_OUTPUT_USD_PER_TOKEN = 64.0 / 1_000_000.0
        const val TEXT_INPUT_USD_PER_TOKEN = 4.0 / 1_000_000.0
        const val TEXT_OUTPUT_USD_PER_TOKEN = 24.0 / 1_000_000.0
        const val IMAGE_INPUT_USD_PER_TOKEN = 5.0 / 1_000_000.0
    }
}
