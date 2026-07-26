package com.yujif.thinklet.realtimecompanion.session

import com.yujif.thinklet.realtimecompanion.audio.AssistantAudioPlayer
import com.yujif.thinklet.realtimecompanion.audio.MicrophoneStreamer
import com.yujif.thinklet.realtimecompanion.camera.CameraSessionRecorder
import com.yujif.thinklet.realtimecompanion.core.SessionPaths
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionUseCase
import com.yujif.thinklet.realtimecompanion.openai.RealtimeSession
import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LifecycleOwner
import java.io.File

@RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA])
fun SessionActiveResources<RealtimeSession, MicrophoneStreamer, AssistantAudioPlayer, CameraSessionRecorder>.startActiveResources(
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
) {
    set(
        SessionResourceStarter.startActiveResources(
            context = context,
            lifecycleOwner = lifecycleOwner,
            fallbackFilesDir = fallbackFilesDir,
            apiKeyProvider = apiKeyProvider,
            recorderProvider = recorderProvider,
            listener = listener,
            model = model,
            useCaseContextProvider = useCaseContextProvider,
            useCase = useCase,
            pathsProvider = pathsProvider,
            frameCadenceMillis = frameCadenceMillis,
        ),
    )
}
