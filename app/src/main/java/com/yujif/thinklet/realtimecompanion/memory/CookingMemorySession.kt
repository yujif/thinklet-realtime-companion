package com.yujif.thinklet.realtimecompanion.memory

import com.yujif.thinklet.realtimecompanion.core.RealtimeCompanionUseCaseSession
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionToolRequest
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionUseCase
import org.json.JSONObject
import java.io.File

internal class CookingMemorySession private constructor(
    private val useCase: RealtimeCompanionUseCase,
    private val store: CookingMemoryStore?,
    private var memory: CookingMemory,
) : RealtimeCompanionUseCaseSession {
    override fun promptContext(): String {
        return cookingMemoryPromptContext(useCase = useCase, memory = memory)
    }

    override fun persist(
        context: String,
        appendTimeline: (String) -> Unit,
        currentTimeMillis: () -> Long,
        onFailure: (Throwable) -> Unit,
    ): Boolean {
        return persist(
            memory = memory,
            context = context,
            appendTimeline = appendTimeline,
            currentTimeMillis = currentTimeMillis,
            onFailure = onFailure,
        )
    }

    fun persist(context: String): Boolean {
        return persist(
            context = context,
            appendTimeline = {},
            currentTimeMillis = { System.currentTimeMillis() },
            onFailure = {},
        )
    }

    fun persist(
        context: String,
        appendTimeline: (String) -> Unit,
        currentTimeMillis: () -> Long,
    ): Boolean {
        return persist(
            context = context,
            appendTimeline = appendTimeline,
            currentTimeMillis = currentTimeMillis,
            onFailure = {},
        )
    }

    override fun handleToolRequest(
        request: RealtimeCompanionToolRequest,
        sendFunctionCallOutput: (callId: String, output: String) -> Unit,
        refreshInstructions: () -> Unit,
        appendTimeline: (String) -> Unit,
        currentTimeMillis: () -> Long,
        onPersistFailure: (Throwable) -> Unit,
    ) {
        memory = handleCookingMemoryToolRequest(
            request = request,
            useCase = useCase,
            memory = memory,
            saveMemory = { updatedMemory ->
                persist(
                    memory = updatedMemory,
                    context = "tool_update",
                    appendTimeline = appendTimeline,
                    currentTimeMillis = currentTimeMillis,
                    onFailure = onPersistFailure,
                )
            },
            sendFunctionCallOutput = sendFunctionCallOutput,
            refreshInstructions = refreshInstructions,
            appendTimeline = appendTimeline,
            currentTimeMillis = currentTimeMillis,
        )
    }

    fun handleToolRequest(
        request: RealtimeCompanionToolRequest,
        sendFunctionCallOutput: (callId: String, output: String) -> Unit,
        refreshInstructions: () -> Unit,
        appendTimeline: (String) -> Unit,
        currentTimeMillis: () -> Long,
    ) {
        handleToolRequest(
            request = request,
            sendFunctionCallOutput = sendFunctionCallOutput,
            refreshInstructions = refreshInstructions,
            appendTimeline = appendTimeline,
            currentTimeMillis = currentTimeMillis,
            onPersistFailure = {},
        )
    }

    private fun persist(
        memory: CookingMemory,
        context: String,
        appendTimeline: (String) -> Unit,
        currentTimeMillis: () -> Long,
        onFailure: (Throwable) -> Unit,
    ): Boolean {
        val store = store ?: return false
        return runCatching {
            store.save(memory)
        }.onFailure { error ->
            onFailure(error)
            appendTimeline(memoryPersistFailureTimeline(context, error, currentTimeMillis()))
        }.isSuccess
    }

    private fun memoryPersistFailureTimeline(
        context: String,
        error: Throwable,
        timeMillis: Long,
    ): String {
        return """{"type":"memory_persist_failed","context":${JSONObject.quote(context)},"error":${JSONObject.quote(error.javaClass.simpleName)},"time":$timeMillis}"""
    }

    companion object {
        fun start(
            useCase: RealtimeCompanionUseCase,
            memoryFile: File,
        ): CookingMemorySession {
            val store = if (shouldUseCookingMemory(useCase)) {
                CookingMemoryStore(memoryFile)
            } else {
                null
            }
            return CookingMemorySession(
                useCase = useCase,
                store = store,
                memory = store?.load() ?: CookingMemory(),
            )
        }
    }
}
