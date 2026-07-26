package com.yujif.thinklet.realtimecompanion.session

import com.yujif.thinklet.realtimecompanion.core.RealtimeCompanionUseCaseSession
import com.yujif.thinklet.realtimecompanion.core.SessionPaths
import com.yujif.thinklet.realtimecompanion.memory.CookingMemorySession
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionCapability
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionUseCase

internal object RealtimeCompanionUseCaseSessionFactory {
    fun start(
        useCase: RealtimeCompanionUseCase,
        paths: SessionPaths,
    ): RealtimeCompanionUseCaseSession {
        if (!useCase.hasCapability(RealtimeCompanionCapability.CookingMemory)) {
            return NoOpRealtimeCompanionUseCaseSession
        }
        return CookingMemorySession.start(
            useCase = useCase,
            memoryFile = paths.memoryFile,
        )
    }
}
