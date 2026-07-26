package com.yujif.thinklet.realtimecompanion.memory

import com.yujif.thinklet.realtimecompanion.openai.CookingMemoryUpdateRequest
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionToolRequest
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionUseCase
import org.json.JSONObject

internal fun handleCookingMemoryToolRequest(
    request: RealtimeCompanionToolRequest,
    useCase: RealtimeCompanionUseCase,
    memory: CookingMemory,
    saveMemory: (CookingMemory) -> Boolean,
    sendFunctionCallOutput: (callId: String, output: String) -> Unit,
    refreshInstructions: () -> Unit,
    appendTimeline: (String) -> Unit,
    currentTimeMillis: () -> Long = { System.currentTimeMillis() },
): CookingMemory {
    return when (request) {
        is CookingMemoryUpdateRequest -> handleCookingMemoryUpdateRequest(
            request = request,
            useCase = useCase,
            memory = memory,
            saveMemory = saveMemory,
            sendFunctionCallOutput = sendFunctionCallOutput,
            refreshInstructions = refreshInstructions,
            appendTimeline = appendTimeline,
            currentTimeMillis = currentTimeMillis,
        )
    }
}

private fun handleCookingMemoryUpdateRequest(
    request: CookingMemoryUpdateRequest,
    useCase: RealtimeCompanionUseCase,
    memory: CookingMemory,
    saveMemory: (CookingMemory) -> Boolean,
    sendFunctionCallOutput: (callId: String, output: String) -> Unit,
    refreshInstructions: () -> Unit,
    appendTimeline: (String) -> Unit,
    currentTimeMillis: () -> Long,
): CookingMemory {
    if (!shouldUseCookingMemory(useCase)) {
        sendFunctionCallOutput(
            request.callId,
            """{"status":"error","message":"cooking_memory_disabled"}""",
        )
        return memory
    }

    val updatedMemory = request.patch.applyTo(memory)
    val persisted = saveMemory(updatedMemory)
    val output = if (persisted) {
        """{"status":"ok"}"""
    } else {
        """{"status":"error","message":"memory_save_failed"}"""
    }
    sendFunctionCallOutput(request.callId, output)
    refreshInstructions()
    appendTimeline(
        """{"type":"memory_updated","call_id":${JSONObject.quote(request.callId)},"persisted":$persisted,"time":${currentTimeMillis()}}""",
    )
    return updatedMemory
}
