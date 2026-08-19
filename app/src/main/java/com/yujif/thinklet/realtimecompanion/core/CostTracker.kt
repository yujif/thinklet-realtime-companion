package com.yujif.thinklet.realtimecompanion.core

/**
 * Token counts from one Realtime `response.done` usage block.
 *
 * `cached*InputTokens` are the subset of the matching `*InputTokens` that the API
 * served from its prompt cache (`input_token_details.cached_tokens_details`) and
 * bills at the cached input rate instead of the list input rate.
 */
data class RealtimeUsage(
    val audioInputTokens: Long = 0,
    val audioOutputTokens: Long = 0,
    val textInputTokens: Long = 0,
    val textOutputTokens: Long = 0,
    val imageInputTokens: Long = 0,
    val cachedAudioInputTokens: Long = 0,
    val cachedTextInputTokens: Long = 0,
    val cachedImageInputTokens: Long = 0,
)

class CostTracker(
    private val maxCostUsd: Double,
) {
    var estimatedCostUsd: Double = 0.0
        private set

    val isOverBudget: Boolean
        get() = estimatedCostUsd >= maxCostUsd

    fun addUsage(usage: RealtimeUsage) {
        // Keep this formula in sync with calculate_realtime_cost() in
        // scripts/build_session_review_html.py: uncached input at the list rate,
        // cached input at the cached rate, output at the output rate.
        val uncachedAudioInput = (usage.audioInputTokens - usage.cachedAudioInputTokens).coerceAtLeast(0)
        val uncachedTextInput = (usage.textInputTokens - usage.cachedTextInputTokens).coerceAtLeast(0)
        val uncachedImageInput = (usage.imageInputTokens - usage.cachedImageInputTokens).coerceAtLeast(0)

        estimatedCostUsd += uncachedAudioInput * AUDIO_INPUT_USD_PER_TOKEN
        estimatedCostUsd += usage.cachedAudioInputTokens * AUDIO_CACHED_INPUT_USD_PER_TOKEN
        estimatedCostUsd += usage.audioOutputTokens * AUDIO_OUTPUT_USD_PER_TOKEN
        estimatedCostUsd += uncachedTextInput * TEXT_INPUT_USD_PER_TOKEN
        estimatedCostUsd += usage.cachedTextInputTokens * TEXT_CACHED_INPUT_USD_PER_TOKEN
        estimatedCostUsd += usage.textOutputTokens * TEXT_OUTPUT_USD_PER_TOKEN
        estimatedCostUsd += uncachedImageInput * IMAGE_INPUT_USD_PER_TOKEN
        estimatedCostUsd += usage.cachedImageInputTokens * IMAGE_CACHED_INPUT_USD_PER_TOKEN
    }

    private companion object {
        // gpt-realtime-2 per-token prices, verified 2026-08-20 against
        // https://developers.openai.com/api/docs/pricing. OpenAI can reprice at any time
        // (list and cached rates alike); re-check that page before trusting the budget
        // guard (maxCostUsd) against real spend.
        const val AUDIO_INPUT_USD_PER_TOKEN = 32.0 / 1_000_000.0
        const val AUDIO_CACHED_INPUT_USD_PER_TOKEN = 0.40 / 1_000_000.0
        const val AUDIO_OUTPUT_USD_PER_TOKEN = 64.0 / 1_000_000.0
        const val TEXT_INPUT_USD_PER_TOKEN = 4.0 / 1_000_000.0
        const val TEXT_CACHED_INPUT_USD_PER_TOKEN = 0.40 / 1_000_000.0
        const val TEXT_OUTPUT_USD_PER_TOKEN = 24.0 / 1_000_000.0
        const val IMAGE_INPUT_USD_PER_TOKEN = 5.0 / 1_000_000.0
        const val IMAGE_CACHED_INPUT_USD_PER_TOKEN = 0.50 / 1_000_000.0
    }
}
