package com.yujif.thinklet.realtimecompanion.openai

enum class RealtimeCompanionCapability {
    CookingMemory,
}

data class RealtimeCompanionUseCase(
    val id: String,
    val displayName: String,
    val japaneseSpeechName: String,
    val instructions: String,
    val capabilities: Set<RealtimeCompanionCapability> = emptySet(),
) {
    fun hasCapability(capability: RealtimeCompanionCapability): Boolean {
        return capability in capabilities
    }

    companion object {
        val GenericRealtimeConversation = RealtimeCompanionUseCase(
            id = "generic_realtime_conversation",
            displayName = "Generic Realtime Conversation",
            japaneseSpeechName = "汎用モード",
            instructions = RealtimeCompanionInstructions.genericRealtimeConversation,
        )

        val CookingSupport = RealtimeCompanionUseCase(
            id = "cooking_support",
            displayName = "Cooking Support",
            japaneseSpeechName = "料理モード",
            instructions = RealtimeCompanionInstructions.generalCookingAssistant,
            capabilities = setOf(RealtimeCompanionCapability.CookingMemory),
        )

        val EnglishConversationLearning = RealtimeCompanionUseCase(
            id = "english_conversation_learning",
            displayName = "English Conversation Learning",
            japaneseSpeechName = "英語練習モード",
            instructions = RealtimeCompanionInstructions.englishConversationLearning,
        )

        val availableUseCases = listOf(GenericRealtimeConversation, CookingSupport, EnglishConversationLearning)

        fun fromId(id: String): RealtimeCompanionUseCase {
            return availableUseCases.firstOrNull { it.id == id.trim() } ?: GenericRealtimeConversation
        }

        fun nextAfter(useCase: RealtimeCompanionUseCase): RealtimeCompanionUseCase {
            return availableUseCases[(indexOf(useCase) + 1) % availableUseCases.size]
        }

        fun previousBefore(useCase: RealtimeCompanionUseCase): RealtimeCompanionUseCase {
            return availableUseCases[(indexOf(useCase) + availableUseCases.size - 1) % availableUseCases.size]
        }

        private fun indexOf(useCase: RealtimeCompanionUseCase): Int {
            return availableUseCases.indexOfFirst { it.id == useCase.id }.takeIf { it >= 0 } ?: 0
        }
    }
}
