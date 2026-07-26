package com.yujif.thinklet.realtimecompanion.memory

import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionUseCase
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionCapability

internal fun shouldUseCookingMemory(useCase: RealtimeCompanionUseCase): Boolean {
    return useCase.hasCapability(RealtimeCompanionCapability.CookingMemory)
}

internal fun cookingMemoryPromptContext(
    useCase: RealtimeCompanionUseCase,
    memory: CookingMemory,
): String {
    if (!shouldUseCookingMemory(useCase)) return ""
    return memory.toPromptContext()
}
