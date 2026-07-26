package com.yujif.thinklet.realtimecompanion.session

import com.yujif.thinklet.realtimecompanion.audio.AssistantAudioPlayer
import com.yujif.thinklet.realtimecompanion.audio.MicrophoneStreamer
import com.yujif.thinklet.realtimecompanion.camera.CameraFrameSampler
import com.yujif.thinklet.realtimecompanion.camera.CameraSessionRecorder
import com.yujif.thinklet.realtimecompanion.core.SessionPaths
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionUseCase
import com.yujif.thinklet.realtimecompanion.openai.RealtimeSession
import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LifecycleOwner
import java.io.File

data class SessionResourceStartResult<RealtimeResource, MicrophoneResource, AssistantAudioResource, CameraResource>(
    val realtimeSession: RealtimeResource,
    val microphoneStreamer: MicrophoneResource,
    val assistantAudioPlayer: AssistantAudioResource,
    val cameraSessionRecorder: CameraResource,
)

object SessionResourceStarter {
    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA])
    fun startActiveResources(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        fallbackFilesDir: File,
        apiKeyProvider: () -> String,
        recorderProvider: () -> SessionRecorder?,
        listener: RealtimeSession.Listener,
        model: String,
        useCaseContextProvider: () -> String,
        useCase: RealtimeCompanionUseCase,
        pathsProvider: () -> SessionPaths?,
        frameCadenceMillis: Long,
    ): SessionResourceStartResult<RealtimeSession, MicrophoneStreamer, AssistantAudioPlayer, CameraSessionRecorder> {
        return startActiveResources(
            startAssistantAudio = {
                AssistantAudioPlayer().also { it.start() }
            },
            stopAssistantAudio = { it.stop() },
            createRealtimeSession = {
                RealtimeSession(
                    apiKeyProvider = apiKeyProvider,
                    recorderProvider = recorderProvider,
                    listener = listener,
                    model = model,
                    useCaseContextProvider = useCaseContextProvider,
                    useCase = useCase,
                )
            },
            connectRealtime = { it.connect() },
            disconnectRealtime = { it.disconnect("startup_rollback") },
            startMicrophone = { realtime ->
                MicrophoneStreamer(
                    recorderProvider = recorderProvider,
                    onAudioChunk = { realtime.sendAudioPcm16(it) },
                ).also { it.start() }
            },
            stopMicrophone = { it.stop() },
            startCamera = { realtime ->
                val frameSampler = CameraFrameSampler(
                    cadenceMillis = frameCadenceMillis,
                    recorderProvider = recorderProvider,
                    onFrame = { realtime.sendImage(it) },
                )
                CameraSessionRecorder(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    outputFileProvider = { pathsProvider()?.cameraFile ?: File(fallbackFilesDir, "camera.mp4") },
                    analyzer = frameSampler,
                ).also { it.start() }
            },
            stopCamera = { it.stop() },
        )
    }

    /**
     * Starts resources in dependency order (assistant audio, realtime session, then the
     * realtime connection) before starting local input capture (microphone, camera), so mic
     * and camera data isn't produced before there's a connection to send it to.
     *
     * If any step throws, whatever already started is rolled back (in reverse order) before
     * the exception is rethrown, so a partial failure can't leave a live microphone, camera
     * recording, or open connection with no reference anyone can stop.
     */
    internal fun <RealtimeResource, MicrophoneResource, AssistantAudioResource, CameraResource> startActiveResources(
        startAssistantAudio: () -> AssistantAudioResource,
        stopAssistantAudio: (AssistantAudioResource) -> Unit,
        createRealtimeSession: () -> RealtimeResource,
        connectRealtime: (RealtimeResource) -> Unit,
        disconnectRealtime: (RealtimeResource) -> Unit,
        startMicrophone: (RealtimeResource) -> MicrophoneResource,
        stopMicrophone: (MicrophoneResource) -> Unit,
        startCamera: (RealtimeResource) -> CameraResource,
        stopCamera: (CameraResource) -> Unit,
    ): SessionResourceStartResult<RealtimeResource, MicrophoneResource, AssistantAudioResource, CameraResource> {
        var assistantAudioPlayer: AssistantAudioResource? = null
        var realtimeSession: RealtimeResource? = null
        var microphoneStreamer: MicrophoneResource? = null
        var cameraSessionRecorder: CameraResource? = null
        try {
            assistantAudioPlayer = startAssistantAudio()
            realtimeSession = createRealtimeSession()
            connectRealtime(realtimeSession)
            microphoneStreamer = startMicrophone(realtimeSession)
            cameraSessionRecorder = startCamera(realtimeSession)
            return SessionResourceStartResult(
                realtimeSession = realtimeSession,
                microphoneStreamer = microphoneStreamer,
                assistantAudioPlayer = assistantAudioPlayer,
                cameraSessionRecorder = cameraSessionRecorder,
            )
        } catch (t: Throwable) {
            cameraSessionRecorder?.let(stopCamera)
            microphoneStreamer?.let(stopMicrophone)
            realtimeSession?.let(disconnectRealtime)
            assistantAudioPlayer?.let(stopAssistantAudio)
            throw t
        }
    }
}
