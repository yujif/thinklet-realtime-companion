package com.yujif.thinklet.realtimecompanion.session

import com.yujif.thinklet.realtimecompanion.core.CostTracker
import com.yujif.thinklet.realtimecompanion.core.RealtimeUsage
import org.json.JSONObject

class RealtimeSessionUsageHandler(
    maxCostUsd: Double,
) {
    private val costTracker = CostTracker(maxCostUsd = maxCostUsd)

    fun handleUsageEvent(
        json: String,
        appendUsage: (String) -> Unit,
        speak: (speech: String, utteranceId: String) -> Unit,
        stop: (reason: String) -> Unit,
    ) {
        appendUsage(json)
        parseUsage(json)?.let { usage ->
            costTracker.addUsage(usage)
            if (costTracker.isOverBudget) {
                speak(BUDGET_STOP_SPEECH, BUDGET_STOP_UTTERANCE_ID)
                stop(BUDGET_STOP_REASON)
            }
        }
    }

    private fun parseUsage(json: String): RealtimeUsage? {
        return runCatching {
            val usage = JSONObject(json).optJSONObject("response")?.optJSONObject("usage") ?: return null
            val inputDetails = usage.optJSONObject("input_token_details")
            val outputDetails = usage.optJSONObject("output_token_details")
            RealtimeUsage(
                audioInputTokens = inputDetails?.optLong("audio_tokens") ?: 0L,
                imageInputTokens = inputDetails?.optLong("image_tokens") ?: 0L,
                textInputTokens = inputDetails?.optLong("text_tokens") ?: 0L,
                audioOutputTokens = outputDetails?.optLong("audio_tokens") ?: 0L,
                textOutputTokens = outputDetails?.optLong("text_tokens") ?: 0L,
            )
        }.getOrNull()
    }

    private companion object {
        const val BUDGET_STOP_SPEECH = "上限に近づいています。停止します"
        const val BUDGET_STOP_UTTERANCE_ID = "budget_stop"
        const val BUDGET_STOP_REASON = "budget_guard"
    }
}
