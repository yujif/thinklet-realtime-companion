package com.yujif.thinklet.realtimecompanion.session

import com.yujif.thinklet.realtimecompanion.core.RealtimeCompanionUseCaseSession
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionToolRequest

internal object NoOpRealtimeCompanionUseCaseSession : RealtimeCompanionUseCaseSession {
    override fun promptContext(): String = ""

    override fun persist(
        context: String,
        appendTimeline: (String) -> Unit,
        currentTimeMillis: () -> Long,
        onFailure: (Throwable) -> Unit,
    ): Boolean = false

    override fun handleToolRequest(
        request: RealtimeCompanionToolRequest,
        sendFunctionCallOutput: (callId: String, output: String) -> Unit,
        refreshInstructions: () -> Unit,
        appendTimeline: (String) -> Unit,
        currentTimeMillis: () -> Long,
        onPersistFailure: (Throwable) -> Unit,
    ) = Unit
}
