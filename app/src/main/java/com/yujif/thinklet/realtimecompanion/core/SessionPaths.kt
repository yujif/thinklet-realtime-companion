package com.yujif.thinklet.realtimecompanion.core

import java.io.File
import java.util.Locale

data class SessionPaths(
    val sessionDir: File,
    val metadataFile: File,
    val eventsFile: File,
    val usageFile: File,
    val timelineFile: File,
    val memoryFile: File,
    val framesDir: File,
    val cameraFile: File,
    val micPcmFile: File,
    val assistantPcmFile: File,
) {
    fun frameFile(timestampMillis: Long): File {
        val name = String.format(Locale.US, "frame-%012d.jpg", timestampMillis)
        return File(framesDir, name)
    }

    companion object {
        fun create(root: File, sessionId: String): SessionPaths {
            val sessionDir = File(root, "sessions/$sessionId")
            return SessionPaths(
                sessionDir = sessionDir,
                metadataFile = File(sessionDir, "metadata.json"),
                eventsFile = File(sessionDir, "events.jsonl"),
                usageFile = File(sessionDir, "usage.jsonl"),
                timelineFile = File(sessionDir, "timeline.jsonl"),
                memoryFile = File(sessionDir, "cooking-memory.json"),
                framesDir = File(sessionDir, "frames"),
                cameraFile = File(sessionDir, "camera.mp4"),
                micPcmFile = File(sessionDir, "mic.pcm"),
                assistantPcmFile = File(sessionDir, "assistant.pcm"),
            )
        }
    }
}
