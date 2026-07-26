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
    fun detectsBudgetCrossing() {
        val tracker = CostTracker(maxCostUsd = 0.01)

        tracker.addUsage(RealtimeUsage(audioInputTokens = 1_000))

        assertTrue(tracker.isOverBudget)
    }
}
