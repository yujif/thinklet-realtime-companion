package com.yujif.thinklet.realtimecompanion.session

import com.yujif.thinklet.realtimecompanion.core.SessionPaths
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionUseCase
import java.io.File

internal data class SessionRecordingConfig(
    val model: String,
    val useCase: RealtimeCompanionUseCase,
    val frameCadenceMillis: Long,
    val maxCostUsd: Double,
    val elapsedNoticeMillis: Long,
    val extensionPromptMillis: Long,
    val extensionTimeoutMillis: Long,
    val minFreeBytesRequired: Long,
)

internal sealed interface SessionRecordingStartResult {
    data class Started(
        val paths: SessionPaths,
        val recorder: SessionRecorder,
    ) : SessionRecordingStartResult

    data object NotEnoughStorage : SessionRecordingStartResult
}

internal object SessionRecordingStarter {
    fun start(
        root: File,
        sessionId: String,
        config: SessionRecordingConfig,
    ): SessionRecordingStartResult {
        val paths = SessionPaths.create(root, sessionId)
        val recorder = SessionRecorder(paths)
        val result = recorder.start(
            SessionMetadataFactory.create(
                paths = paths,
                model = config.model,
                useCase = config.useCase,
                frameCadenceMillis = config.frameCadenceMillis,
                maxCostUsd = config.maxCostUsd,
                elapsedNoticeMillis = config.elapsedNoticeMillis,
                extensionPromptMillis = config.extensionPromptMillis,
                extensionTimeoutMillis = config.extensionTimeoutMillis,
                minFreeBytesRequired = config.minFreeBytesRequired,
            ),
        )

        return when (result) {
            SessionRecorderStartResult.Started -> SessionRecordingStartResult.Started(
                paths = paths,
                recorder = recorder,
            )
            SessionRecorderStartResult.NotEnoughStorage -> SessionRecordingStartResult.NotEnoughStorage
        }
    }
}
