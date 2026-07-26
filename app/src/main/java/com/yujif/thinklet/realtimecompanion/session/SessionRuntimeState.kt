package com.yujif.thinklet.realtimecompanion.session

class SessionRuntimeState<Paths, Recorder, UseCaseSession> {
    var paths: Paths? = null
        private set
    var recorder: Recorder? = null
        private set
    var useCaseSession: UseCaseSession? = null
        private set

    fun startActiveSession(paths: Paths, recorder: Recorder, useCaseSession: UseCaseSession) {
        this.paths = paths
        this.recorder = recorder
        this.useCaseSession = useCaseSession
    }

    fun clear() {
        paths = null
        clearActiveSession()
    }

    fun clearActiveSession() {
        recorder = null
        useCaseSession = null
    }

    fun currentSessionPathDisplay(format: (Paths) -> String): String {
        return paths?.let(format).orEmpty()
    }

    fun currentRecorder(): Recorder? {
        return recorder
    }

    fun currentPaths(): Paths? {
        return paths
    }

    fun emitOptionalOutputs(
        speech: String?,
        utteranceId: String?,
        timelineJson: String?,
        speak: (String, String) -> Unit,
        appendTimeline: (Recorder, String) -> Unit,
        onTimelineFailure: (Throwable) -> Unit,
    ) {
        SessionOutputSink.emitOptionalOutputs(
            speech = speech,
            utteranceId = utteranceId,
            timelineJson = timelineJson,
            speak = speak,
            appendTimeline = { json ->
                recorder?.let { appendTimeline(it, json) }
            },
            onTimelineFailure = onTimelineFailure,
        )
    }

    fun appendUsage(
        json: String,
        appendUsage: (Recorder, String) -> Unit,
    ) {
        recorder?.let { appendUsage(it, json) }
    }

    fun recordStartRequested(
        manual: Boolean,
        nowMillis: Long,
        recordStartRequested: (Recorder, Boolean, Long) -> Unit,
    ) {
        recorder?.let { recordStartRequested(it, manual, nowMillis) }
    }

    fun recordStopped(
        reason: String,
        nowMillis: Long,
        recordStopped: (Recorder, String, Long) -> Unit,
    ) {
        recorder?.let { recordStopped(it, reason, nowMillis) }
    }

    fun currentUseCasePromptContext(promptContext: (UseCaseSession) -> String): String {
        return useCaseSession?.let(promptContext).orEmpty()
    }

    fun persistUseCaseSession(
        context: String,
        persist: (
            UseCaseSession,
            String,
            (String) -> Unit,
            () -> Long,
            (Throwable) -> Unit,
        ) -> Boolean,
        appendTimeline: (String) -> Unit,
        currentTimeMillis: () -> Long = { System.currentTimeMillis() },
        onFailure: (Throwable) -> Unit = {},
    ): Boolean {
        return useCaseSession?.let {
            persist(
                it,
                context,
                appendTimeline,
                currentTimeMillis,
                onFailure,
            )
        } ?: false
    }

    fun <Request> handleUseCaseToolRequest(
        request: Request,
        handleToolRequest: (
            UseCaseSession,
            Request,
            (callId: String, output: String) -> Unit,
            () -> Unit,
            (String) -> Unit,
            () -> Long,
            (Throwable) -> Unit,
        ) -> Unit,
        sendFunctionCallOutput: (callId: String, output: String) -> Unit,
        refreshInstructions: () -> Unit,
        appendTimeline: (String) -> Unit,
        currentTimeMillis: () -> Long = { System.currentTimeMillis() },
        onPersistFailure: (Throwable) -> Unit = {},
    ) {
        useCaseSession?.let {
            handleToolRequest(
                it,
                request,
                sendFunctionCallOutput,
                refreshInstructions,
                appendTimeline,
                currentTimeMillis,
                onPersistFailure,
            )
        }
    }
}
