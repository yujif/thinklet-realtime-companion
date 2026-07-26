package com.yujif.thinklet.realtimecompanion.memory

import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookingMemoryPolicyTest {
    @Test
    fun cookingSupportUsesCookingMemory() {
        val memory = CookingMemory(recipe = "きのこリゾット")

        assertTrue(shouldUseCookingMemory(RealtimeCompanionUseCase.CookingSupport))
        assertEquals(
            "調理メモリー:\n作っているもの: きのこリゾット",
            cookingMemoryPromptContext(
                useCase = RealtimeCompanionUseCase.CookingSupport,
                memory = memory,
            ),
        )
    }

    @Test
    fun englishConversationLearningDoesNotUseCookingMemory() {
        val memory = CookingMemory(recipe = "きのこリゾット")

        assertFalse(shouldUseCookingMemory(RealtimeCompanionUseCase.EnglishConversationLearning))
        assertEquals(
            "",
            cookingMemoryPromptContext(
                useCase = RealtimeCompanionUseCase.EnglishConversationLearning,
                memory = memory,
            ),
        )
    }

    @Test
    fun genericRealtimeConversationDoesNotUseCookingMemory() {
        val memory = CookingMemory(recipe = "きのこリゾット")

        assertFalse(shouldUseCookingMemory(RealtimeCompanionUseCase.GenericRealtimeConversation))
        assertEquals(
            "",
            cookingMemoryPromptContext(
                useCase = RealtimeCompanionUseCase.GenericRealtimeConversation,
                memory = memory,
            ),
        )
    }
}
