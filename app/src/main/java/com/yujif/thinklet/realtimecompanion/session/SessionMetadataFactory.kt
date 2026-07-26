package com.yujif.thinklet.realtimecompanion.session

import com.yujif.thinklet.realtimecompanion.core.SessionPaths
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionUseCase

internal object SessionMetadataFactory {
    fun create(
        paths: SessionPaths,
        model: String,
        useCase: RealtimeCompanionUseCase,
        frameCadenceMillis: Long,
        maxCostUsd: Double,
        elapsedNoticeMillis: Long,
        extensionPromptMillis: Long,
        extensionTimeoutMillis: Long,
        minFreeBytesRequired: Long,
    ): SessionMetadata {
        return SessionMetadata(
            sessionId = paths.sessionDir.name,
            model = model,
            useCaseId = useCase.id,
            useCaseLabel = useCase.displayName,
            frameCadenceMillis = frameCadenceMillis,
            maxCostUsd = maxCostUsd,
            elapsedNoticeMillis = elapsedNoticeMillis,
            extensionPromptMillis = extensionPromptMillis,
            extensionTimeoutMillis = extensionTimeoutMillis,
            minFreeBytesRequired = minFreeBytesRequired,
        )
    }
}
