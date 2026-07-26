package com.yujif.thinklet.realtimecompanion.session

import com.yujif.thinklet.realtimecompanion.core.RealtimeCompanionUseCaseSession
import com.yujif.thinklet.realtimecompanion.memory.CookingMemoryPatch
import com.yujif.thinklet.realtimecompanion.openai.CookingMemoryUpdateRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionResourceStarterTest {
    @Test
    fun startsRealtimeConnectionBeforeLocalInputCapture() {
        val events = mutableListOf<String>()

        val result = SessionResourceStarter.startActiveResources(
            startAssistantAudio = {
                events += "assistant_audio"
                "assistant"
            },
            stopAssistantAudio = { events += "stop_assistant_audio:$it" },
            createRealtimeSession = {
                events += "realtime"
                "realtime"
            },
            connectRealtime = {
                events += "connect:$it"
            },
            disconnectRealtime = { events += "disconnect:$it" },
            startMicrophone = {
                events += "microphone:$it"
                "microphone"
            },
            stopMicrophone = { events += "stop_microphone:$it" },
            startCamera = {
                events += "camera:$it"
                "camera"
            },
            stopCamera = { events += "stop_camera:$it" },
        )

        assertEquals(
            listOf(
                "assistant_audio",
                "realtime",
                "connect:realtime",
                "microphone:realtime",
                "camera:realtime",
            ),
            events,
        )
        assertEquals("realtime", result.realtimeSession)
        assertEquals("microphone", result.microphoneStreamer)
        assertEquals("assistant", result.assistantAudioPlayer)
        assertEquals("camera", result.cameraSessionRecorder)
    }

    @Test
    fun rollsBackAlreadyStartedResourcesWhenALaterStepFails() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("camera busy")

        val thrown = runCatching {
            SessionResourceStarter.startActiveResources(
                startAssistantAudio = {
                    events += "assistant_audio"
                    "assistant"
                },
                stopAssistantAudio = { events += "stop_assistant_audio:$it" },
                createRealtimeSession = {
                    events += "realtime"
                    "realtime"
                },
                connectRealtime = { events += "connect:$it" },
                disconnectRealtime = { events += "disconnect:$it" },
                startMicrophone = {
                    events += "microphone:$it"
                    "microphone"
                },
                stopMicrophone = { events += "stop_microphone:$it" },
                startCamera = {
                    events += "camera_failed:$it"
                    throw failure
                },
                stopCamera = { events += "stop_camera:$it" },
            )
        }.exceptionOrNull()

        assertEquals(failure, thrown)
        assertEquals(
            listOf(
                "assistant_audio",
                "realtime",
                "connect:realtime",
                "microphone:realtime",
                "camera_failed:realtime",
                "stop_microphone:microphone",
                "disconnect:realtime",
                "stop_assistant_audio:assistant",
            ),
            events,
        )
    }

    @Test
    fun activeResourcesTracksStartResultAndClearsOnStop() {
        val resources = SessionActiveResources<String, String, String, String>()

        assertEquals(false, resources.isSessionActive)

        resources.set(
            SessionResourceStartResult(
                realtimeSession = "realtime",
                microphoneStreamer = "microphone",
                assistantAudioPlayer = "assistant",
                cameraSessionRecorder = "camera",
            ),
        )

        assertEquals("realtime", resources.realtimeSession)
        assertEquals("microphone", resources.microphoneStreamer)
        assertEquals("assistant", resources.assistantAudioPlayer)
        assertEquals("camera", resources.cameraSessionRecorder)
        assertEquals(true, resources.isSessionActive)

        resources.clear()

        assertEquals(null, resources.realtimeSession)
        assertEquals(null, resources.microphoneStreamer)
        assertEquals(null, resources.assistantAudioPlayer)
        assertEquals(null, resources.cameraSessionRecorder)
        assertEquals(false, resources.isSessionActive)
    }

    @Test
    fun activeResourcesStartsAndStoresResourcesInStartupOrder() {
        val resources = SessionActiveResources<String, String, String, String>()
        val events = mutableListOf<String>()

        resources.startActiveResources(
            startAssistantAudio = {
                events += "assistant_audio"
                "assistant"
            },
            stopAssistantAudio = { events += "stop_assistant_audio:$it" },
            createRealtimeSession = {
                events += "realtime"
                "realtime"
            },
            connectRealtime = {
                events += "connect:$it"
            },
            disconnectRealtime = { events += "disconnect:$it" },
            startMicrophone = {
                events += "microphone:$it"
                "microphone"
            },
            stopMicrophone = { events += "stop_microphone:$it" },
            startCamera = {
                events += "camera:$it"
                "camera"
            },
            stopCamera = { events += "stop_camera:$it" },
        )

        assertEquals(
            listOf(
                "assistant_audio",
                "realtime",
                "connect:realtime",
                "microphone:realtime",
                "camera:realtime",
            ),
            events,
        )
        assertEquals("realtime", resources.realtimeSession)
        assertEquals("microphone", resources.microphoneStreamer)
        assertEquals("assistant", resources.assistantAudioPlayer)
        assertEquals("camera", resources.cameraSessionRecorder)
        assertEquals(true, resources.isSessionActive)
    }

    @Test
    fun activeResourcesStopsCurrentResourcesInShutdownOrderAndClearsReferences() {
        val resources = SessionActiveResources<String, String, String, String>()
        val events = mutableListOf<String>()
        resources.set(
            SessionResourceStartResult(
                realtimeSession = "realtime",
                microphoneStreamer = "microphone",
                assistantAudioPlayer = "assistant",
                cameraSessionRecorder = "camera",
            ),
        )

        resources.stopActiveResources(
            reason = "manual_stop",
            disconnectRealtime = { resource, reason -> events += "disconnect:$resource:$reason" },
            stopMicrophone = { events += "microphone:$it" },
            stopAssistantAudio = { events += "assistant:$it" },
            stopCamera = { events += "camera:$it" },
        )

        assertEquals(
            listOf(
                "disconnect:realtime:manual_stop",
                "microphone:microphone",
                "assistant:assistant",
                "camera:camera",
            ),
            events,
        )
        assertEquals(null, resources.realtimeSession)
        assertEquals(null, resources.microphoneStreamer)
        assertEquals(null, resources.assistantAudioPlayer)
        assertEquals(null, resources.cameraSessionRecorder)
        assertEquals(false, resources.isSessionActive)
    }

    @Test
    fun activeResourcesRoutesRealtimeToolResponsesThroughCurrentSession() {
        val resources = SessionActiveResources<String, String, String, String>()
        val events = mutableListOf<String>()
        resources.set(
            SessionResourceStartResult(
                realtimeSession = "realtime",
                microphoneStreamer = "microphone",
                assistantAudioPlayer = "assistant",
                cameraSessionRecorder = "camera",
            ),
        )

        resources.sendFunctionCallOutput(
            callId = "call_123",
            output = """{"ok":true}""",
            sendFunctionCallOutput = { resource, callId, output ->
                events += "tool_output:$resource:$callId:$output"
            },
        )
        resources.refreshRealtimeInstructions(
            refreshInstructions = { events += "refresh:$it" },
        )

        assertEquals(
            listOf(
                """tool_output:realtime:call_123:{"ok":true}""",
                "refresh:realtime",
            ),
            events,
        )
    }

    @Test
    fun activeResourcesRoutesAssistantAudioThroughCurrentPlayer() {
        val resources = SessionActiveResources<String, String, String, String>()
        val events = mutableListOf<String>()
        resources.set(
            SessionResourceStartResult(
                realtimeSession = "realtime",
                microphoneStreamer = "microphone",
                assistantAudioPlayer = "assistant",
                cameraSessionRecorder = "camera",
            ),
        )

        resources.playAssistantAudio(
            bytes = byteArrayOf(1, 2, 3),
            playAssistantAudio = { resource, bytes -> events += "$resource:${bytes.joinToString(",")}" },
        )

        assertEquals(listOf("assistant:1,2,3"), events)
    }

    @Test
    fun runtimeStateTracksActiveSessionReferences() {
        val state = SessionRuntimeState<String, String, String>()

        state.startActiveSession(
            paths = "paths",
            recorder = "recorder",
            useCaseSession = "use_case_session",
        )

        assertEquals("paths", state.paths)
        assertEquals("recorder", state.recorder)
        assertEquals("use_case_session", state.useCaseSession)

        state.clear()

        assertEquals(null, state.paths)
        assertEquals(null, state.recorder)
        assertEquals(null, state.useCaseSession)
    }

    @Test
    fun runtimeStateKeepsLatestPathsWhenClearingActiveSession() {
        val state = SessionRuntimeState<String, String, String>()
        state.startActiveSession(
            paths = "last_paths",
            recorder = "recorder",
            useCaseSession = "use_case_session",
        )

        state.clearActiveSession()

        assertEquals("last_paths", state.paths)
        assertEquals(null, state.recorder)
        assertEquals(null, state.useCaseSession)
    }

    @Test
    fun runtimeStateProvidesLatestSessionPathForDisplay() {
        val state = SessionRuntimeState<String, String, String>()

        assertEquals("", state.currentSessionPathDisplay { path -> "session:$path" })

        state.startActiveSession(
            paths = "last_paths",
            recorder = "recorder",
            useCaseSession = "use_case_session",
        )
        state.clearActiveSession()

        assertEquals("session:last_paths", state.currentSessionPathDisplay { path -> "session:$path" })
    }

    @Test
    fun runtimeStateProvidesCurrentRecorderForResourceProviders() {
        val state = SessionRuntimeState<String, String, String>()

        assertEquals(null, state.currentRecorder())

        state.startActiveSession(
            paths = "paths",
            recorder = "recorder",
            useCaseSession = "use_case_session",
        )

        assertEquals("recorder", state.currentRecorder())
    }

    @Test
    fun runtimeStateProvidesCurrentPathsForResourceProviders() {
        val state = SessionRuntimeState<String, String, String>()

        assertEquals(null, state.currentPaths())

        state.startActiveSession(
            paths = "paths",
            recorder = "recorder",
            useCaseSession = "use_case_session",
        )

        assertEquals("paths", state.currentPaths())
    }

    @Test
    fun runtimeStateStartsActiveSessionAtomically() {
        val state = SessionRuntimeState<String, String, String>()

        state.startActiveSession(
            paths = "paths",
            recorder = "recorder",
            useCaseSession = "use_case_session",
        )

        assertEquals("paths", state.paths)
        assertEquals("recorder", state.recorder)
        assertEquals("use_case_session", state.useCaseSession)
    }

    @Test
    fun runtimeStateEmitsOptionalOutputsThroughCurrentRecorder() {
        val state = SessionRuntimeState<String, MutableList<String>, String>()
        val spoken = mutableListOf<String>()
        state.startActiveSession(
            paths = "paths",
            recorder = mutableListOf(),
            useCaseSession = "use_case_session",
        )

        state.emitOptionalOutputs(
            speech = "開始します",
            utteranceId = "session_start",
            timelineJson = """{"event":"started"}""",
            speak = { speech, utteranceId -> spoken += "$utteranceId:$speech" },
            appendTimeline = { recorder, json -> recorder += json },
            onTimelineFailure = { error -> throw error },
        )

        assertEquals(listOf("session_start:開始します"), spoken)
        assertEquals(listOf("""{"event":"started"}"""), state.recorder)
    }

    @Test
    fun runtimeStateAppendsUsageThroughCurrentRecorder() {
        val state = SessionRuntimeState<String, MutableList<String>, String>()
        state.startActiveSession(
            paths = "paths",
            recorder = mutableListOf(),
            useCaseSession = "use_case_session",
        )

        state.appendUsage(
            json = """{"estimated_cost_usd":0.1}""",
            appendUsage = { recorder, json -> recorder += json },
        )

        assertEquals(listOf("""{"estimated_cost_usd":0.1}"""), state.recorder)
    }

    @Test
    fun runtimeStateRecordsSessionLifecycleThroughCurrentRecorder() {
        val state = SessionRuntimeState<String, MutableList<String>, String>()
        state.startActiveSession(
            paths = "paths",
            recorder = mutableListOf(),
            useCaseSession = "use_case_session",
        )

        state.recordStartRequested(
            manual = true,
            nowMillis = 123L,
            recordStartRequested = { recorder, manual, nowMillis ->
                recorder += "start:$manual:$nowMillis"
            },
        )
        state.recordStopped(
            reason = "manual_stop",
            nowMillis = 456L,
            recordStopped = { recorder, reason, nowMillis ->
                recorder += "stop:$reason:$nowMillis"
            },
        )

        assertEquals(
            listOf(
                "start:true:123",
                "stop:manual_stop:456",
            ),
            state.recorder,
        )
    }

    @Test
    fun runtimeStateDelegatesUseCaseSessionPersistence() {
        val state = SessionRuntimeState<String, String, FakeUseCaseSession>()
        val session = FakeUseCaseSession()
        state.startActiveSession(
            paths = "paths",
            recorder = "recorder",
            useCaseSession = session,
        )

        state.persistUseCaseSession(
            context = "session_stop",
            persist = FakeUseCaseSession::persist,
            appendTimeline = { session.timeline += it },
            currentTimeMillis = { 123L },
            onFailure = { error -> throw error },
        )

        assertEquals(listOf("session_stop:123"), session.persistedContexts)
        assertEquals(listOf("""{"type":"persisted"}"""), session.timeline)
    }

    @Test
    fun runtimeStateDelegatesUseCaseToolRequests() {
        val state = SessionRuntimeState<String, String, FakeUseCaseSession>()
        val session = FakeUseCaseSession()
        state.startActiveSession(
            paths = "paths",
            recorder = "recorder",
            useCaseSession = session,
        )
        val events = mutableListOf<String>()

        state.handleUseCaseToolRequest(
            request = CookingMemoryUpdateRequest(
                callId = "call_123",
                patch = CookingMemoryPatch(),
            ),
            handleToolRequest = FakeUseCaseSession::handleToolRequest,
            sendFunctionCallOutput = { callId, output -> events += "output:$callId:$output" },
            refreshInstructions = { events += "refresh" },
            appendTimeline = { events += "timeline:$it" },
            currentTimeMillis = { 456L },
            onPersistFailure = { error -> throw error },
        )

        assertEquals(listOf("call_123:456"), session.handledToolRequests)
        assertEquals(
            listOf(
                """output:call_123:{"ok":true}""",
                "refresh",
                """timeline:{"type":"tool_handled"}""",
            ),
            events,
        )
    }

    @Test
    fun runtimeStateProvidesCurrentUseCasePromptContext() {
        val state = SessionRuntimeState<String, String, FakeUseCaseSession>()
        val session = FakeUseCaseSession()
        state.startActiveSession(
            paths = "paths",
            recorder = "recorder",
            useCaseSession = session,
        )

        assertEquals(
            "fake prompt context",
            state.currentUseCasePromptContext(FakeUseCaseSession::promptContext),
        )

        state.clearActiveSession()

        assertEquals("", state.currentUseCasePromptContext(FakeUseCaseSession::promptContext))
    }
}

private class FakeUseCaseSession : RealtimeCompanionUseCaseSession {
    val persistedContexts = mutableListOf<String>()
    val handledToolRequests = mutableListOf<String>()
    val timeline = mutableListOf<String>()

    override fun promptContext(): String = "fake prompt context"

    override fun persist(
        context: String,
        appendTimeline: (String) -> Unit,
        currentTimeMillis: () -> Long,
        onFailure: (Throwable) -> Unit,
    ): Boolean {
        persistedContexts += "$context:${currentTimeMillis()}"
        appendTimeline("""{"type":"persisted"}""")
        return true
    }

    override fun handleToolRequest(
        request: com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionToolRequest,
        sendFunctionCallOutput: (callId: String, output: String) -> Unit,
        refreshInstructions: () -> Unit,
        appendTimeline: (String) -> Unit,
        currentTimeMillis: () -> Long,
        onPersistFailure: (Throwable) -> Unit,
    ) {
        handledToolRequests += "${request.callId}:${currentTimeMillis()}"
        sendFunctionCallOutput(request.callId, """{"ok":true}""")
        refreshInstructions()
        appendTimeline("""{"type":"tool_handled"}""")
    }
}
