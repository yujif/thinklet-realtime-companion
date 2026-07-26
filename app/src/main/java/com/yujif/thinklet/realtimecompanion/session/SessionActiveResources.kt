package com.yujif.thinklet.realtimecompanion.session

import android.Manifest
import androidx.annotation.RequiresPermission

class SessionActiveResources<RealtimeResource, MicrophoneResource, AssistantAudioResource, CameraResource> {
    var realtimeSession: RealtimeResource? = null
        private set
    var microphoneStreamer: MicrophoneResource? = null
        private set
    var assistantAudioPlayer: AssistantAudioResource? = null
        private set
    var cameraSessionRecorder: CameraResource? = null
        private set
    val isSessionActive: Boolean
        get() = realtimeSession != null

    fun set(result: SessionResourceStartResult<RealtimeResource, MicrophoneResource, AssistantAudioResource, CameraResource>) {
        realtimeSession = result.realtimeSession
        microphoneStreamer = result.microphoneStreamer
        assistantAudioPlayer = result.assistantAudioPlayer
        cameraSessionRecorder = result.cameraSessionRecorder
    }

    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA])
    fun startActiveResources(
        startAssistantAudio: () -> AssistantAudioResource,
        stopAssistantAudio: (AssistantAudioResource) -> Unit,
        createRealtimeSession: () -> RealtimeResource,
        connectRealtime: (RealtimeResource) -> Unit,
        disconnectRealtime: (RealtimeResource) -> Unit,
        startMicrophone: (RealtimeResource) -> MicrophoneResource,
        stopMicrophone: (MicrophoneResource) -> Unit,
        startCamera: (RealtimeResource) -> CameraResource,
        stopCamera: (CameraResource) -> Unit,
    ) {
        set(
            SessionResourceStarter.startActiveResources(
                startAssistantAudio = startAssistantAudio,
                stopAssistantAudio = stopAssistantAudio,
                createRealtimeSession = createRealtimeSession,
                connectRealtime = connectRealtime,
                disconnectRealtime = disconnectRealtime,
                startMicrophone = startMicrophone,
                stopMicrophone = stopMicrophone,
                startCamera = startCamera,
                stopCamera = stopCamera,
            ),
        )
    }

    fun stopActiveResources(
        reason: String,
        disconnectRealtime: (RealtimeResource, String) -> Unit,
        stopMicrophone: (MicrophoneResource) -> Unit,
        stopAssistantAudio: (AssistantAudioResource) -> Unit,
        stopCamera: (CameraResource) -> Unit,
    ) {
        SessionResourceStopper.stopActiveResources(
            reason = reason,
            disconnectRealtime = { stopReason ->
                realtimeSession?.let { disconnectRealtime(it, stopReason) }
            },
            stopMicrophone = {
                microphoneStreamer?.let(stopMicrophone)
            },
            stopAssistantAudio = {
                assistantAudioPlayer?.let(stopAssistantAudio)
            },
            stopCamera = {
                cameraSessionRecorder?.let(stopCamera)
            },
        )
        clear()
    }

    fun sendFunctionCallOutput(
        callId: String,
        output: String,
        sendFunctionCallOutput: (RealtimeResource, String, String) -> Unit,
    ) {
        realtimeSession?.let { sendFunctionCallOutput(it, callId, output) }
    }

    fun refreshRealtimeInstructions(
        refreshInstructions: (RealtimeResource) -> Unit,
    ) {
        realtimeSession?.let(refreshInstructions)
    }

    fun playAssistantAudio(
        bytes: ByteArray,
        playAssistantAudio: (AssistantAudioResource, ByteArray) -> Unit,
    ) {
        assistantAudioPlayer?.let { playAssistantAudio(it, bytes) }
    }

    fun clear() {
        realtimeSession = null
        microphoneStreamer = null
        assistantAudioPlayer = null
        cameraSessionRecorder = null
    }
}
