package com.yujif.thinklet.realtimecompanion.core

import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionToolRequest

internal interface RealtimeCompanionUseCaseSession {
    fun promptContext(): String

    fun persist(
        context: String,
        appendTimeline: (String) -> Unit = {},
        currentTimeMillis: () -> Long = { System.currentTimeMillis() },
        onFailure: (Throwable) -> Unit = {},
    ): Boolean

    fun handleToolRequest(
        request: RealtimeCompanionToolRequest,
        sendFunctionCallOutput: (callId: String, output: String) -> Unit,
        refreshInstructions: () -> Unit,
        appendTimeline: (String) -> Unit,
        currentTimeMillis: () -> Long = { System.currentTimeMillis() },
        onPersistFailure: (Throwable) -> Unit = {},
    )
}
