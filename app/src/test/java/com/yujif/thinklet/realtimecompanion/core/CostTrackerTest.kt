package com.yujif.thinklet.realtimecompanion.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CostTrackerTest {
    @Test
    fun estimatesRealtimeCostFromUsageTokens() {
        val tracker = CostTracker(maxCostUsd = 2.0)

        tracker.addUsage(
            RealtimeUsage(
                audioInputTokens = 1_800,
                audioOutputTokens = 2_400,
                textInputTokens = 1_000,
                textOutputTokens = 1_000,
                imageInputTokens = 63_000,
            ),
        )

        assertEquals(0.5542, tracker.estimatedCostUsd, 0.0001)
        assertFalse(tracker.isOverBudget)
    }

    @Test
    fun chargesCachedInputTokensAtCachedRates() {
        // Same fixture as tests/test_build_session_review_html.py
        // (test_calculate_realtime_cost_uses_cached_modality_breakdown) so the app-side
        // estimate and the review report cannot drift apart.
        val tracker = CostTracker(maxCostUsd = 2.0)

        tracker.addUsage(
            RealtimeUsage(
                textInputTokens = 101,
                cachedTextInputTokens = 1,
                audioInputTokens = 202,
                cachedAudioInputTokens = 2,
                imageInputTokens = 303,
                cachedImageInputTokens = 3,
                textOutputTokens = 400,
                audioOutputTokens = 500,
            ),
        )

        val expected = (
            100 * 4.00 + 1 * 0.40 +
                200 * 32.00 + 2 * 0.40 +
                300 * 5.00 + 3 * 0.50 +
                400 * 24.00 + 500 * 64.00
            ) / 1_000_000
        assertEquals(expected, tracker.estimatedCostUsd, 1e-9)
    }

    @Test
    fun cachedTokensNeverMakeUncachedInputNegative() {
        val tracker = CostTracker(maxCostUsd = 2.0)

        tracker.addUsage(
            RealtimeUsage(
                audioInputTokens = 100,
                cachedAudioInputTokens = 150,
            ),
        )

        // Uncached audio is clamped to 0; only the cached rate applies.
        assertEquals(150 * 0.40 / 1_000_000, tracker.estimatedCostUsd, 1e-9)
    }

    @Test
    fun detectsBudgetCrossing() {
        val tracker = CostTracker(maxCostUsd = 0.01)

        tracker.addUsage(RealtimeUsage(audioInputTokens = 1_000))

        assertTrue(tracker.isOverBudget)
    }
}
