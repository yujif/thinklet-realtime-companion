package com.yujif.thinklet.realtimecompanion.session

import com.yujif.thinklet.realtimecompanion.core.ApiLogRedactor
import com.yujif.thinklet.realtimecompanion.core.SessionPaths
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class SessionMetadata(
    val sessionId: String,
    val model: String,
    val useCaseId: String,
    val useCaseLabel: String,
    val frameCadenceMillis: Long,
    val maxCostUsd: Double,
    val elapsedNoticeMillis: Long,
    val extensionPromptMillis: Long,
    val extensionTimeoutMillis: Long,
    val minFreeBytesRequired: Long,
)

enum class SessionRecorderStartResult {
    Started,
    NotEnoughStorage,
}

// The 1.5GB session budget is split between CameraX video and the other recorder outputs.
// A 60-minute mic/assistant PCM stream at 24kHz mono 16-bit is roughly 350MB combined, so
// 500MB leaves room for frames and metadata without relying on unbounded free space.
const val DEFAULT_MAX_SESSION_BYTES = 500_000_000L
const val DEFAULT_MAX_CAMERA_BYTES = 1_000_000_000L

class SessionRecorder(
    private val paths: SessionPaths,
    private val maxSessionBytes: Long = DEFAULT_MAX_SESSION_BYTES,
    private val maxExternalBytes: Long = DEFAULT_MAX_CAMERA_BYTES,
    private val freeBytesProvider: (File) -> Long = { it.usableSpace },
) {
    // Tracks everything this recorder writes itself (events, timeline, usage, mic/assistant
    // PCM, frames). camera.mp4 is excluded: CameraX already caps it independently via
    // FileOutputOptions.setFileSizeLimit, so counting it here would double-count against the
    // session budget.
    private val writtenBytes = AtomicLong(0)
    private val writeFailed = AtomicBoolean(false)

    val hasExceededCapacity: Boolean
        get() = writtenBytes.get() >= maxSessionBytes

    val hasWriteFailed: Boolean
        get() = writeFailed.get()

    val currentWrittenBytes: Long
        get() = writtenBytes.get()

    val shouldStopForStorage: Boolean
        get() = hasExceededCapacity || hasWriteFailed

    /**
     * Includes files written by sibling resources such as CameraX's camera.mp4.
     * The camera file has its own cap, but checking the combined amount makes the
     * session-wide storage guarantee explicit and observable by the main-thread guard.
     */
    fun shouldStopForStorage(externalBytes: Long): Boolean {
        return shouldStopForStorage ||
            externalBytes >= maxExternalBytes ||
            currentWrittenBytes + externalBytes >= maxSessionBytes + maxExternalBytes
    }

    fun start(metadata: SessionMetadata): SessionRecorderStartResult {
        val parent = paths.sessionDir.parentFile ?: paths.sessionDir
        parent.mkdirs()
        if (freeBytesProvider(parent) < metadata.minFreeBytesRequired) {
            return SessionRecorderStartResult.NotEnoughStorage
        }

        paths.sessionDir.mkdirs()
        paths.framesDir.mkdirs()
        paths.metadataFile.writeText(metadata.toJson())
        return SessionRecorderStartResult.Started
    }

    @Synchronized
    fun appendEvent(json: String) {
        appendLine(paths.eventsFile, ApiLogRedactor.redact(json))
    }

    fun appendTimeline(json: String) {
        appendLine(paths.timelineFile, json)
    }

    fun appendUsage(json: String) {
        appendLine(paths.usageFile, json)
    }

    fun appendMicPcm(bytes: ByteArray) {
        appendBytes(paths.micPcmFile, bytes)
    }

    fun appendAssistantPcm(bytes: ByteArray) {
        appendBytes(paths.assistantPcmFile, bytes)
    }

    fun writeFrame(timestampMillis: Long, bytes: ByteArray): File {
        paths.framesDir.mkdirs()
        val file = paths.frameFile(timestampMillis)
        runCatching {
            file.writeBytes(bytes)
            writtenBytes.addAndGet(bytes.size.toLong())
        }.onFailure { writeFailed.set(true) }
        return file
    }

    private fun appendLine(file: File, line: String) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(line + "\n")
            writtenBytes.addAndGet((line + "\n").toByteArray(StandardCharsets.UTF_8).size.toLong())
        }.onFailure { writeFailed.set(true) }
    }

    private fun appendBytes(file: File, bytes: ByteArray) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendBytes(bytes)
            writtenBytes.addAndGet(bytes.size.toLong())
        }.onFailure { writeFailed.set(true) }
    }

    private fun SessionMetadata.toJson(): String {
        return JSONObject()
            .put("session_id", sessionId)
            .put("model", model)
            .put("use_case_id", useCaseId)
            .put("use_case_label", useCaseLabel)
            .put("frame_cadence_millis", frameCadenceMillis)
            .put("max_cost_usd", maxCostUsd)
            .put("elapsed_notice_millis", elapsedNoticeMillis)
            .put("extension_prompt_millis", extensionPromptMillis)
            .put("extension_timeout_millis", extensionTimeoutMillis)
            .put("min_free_bytes_required", minFreeBytesRequired)
            .toString(2)
    }
}
