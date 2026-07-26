package com.yujif.thinklet.realtimecompanion.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SessionPathsTest {
    @Test
    fun buildsStableSessionFileNames() {
        val root = File("/tmp/root")
        val paths = SessionPaths.create(root, "2026-06-07T18-30-00Z")

        assertEquals(File(root, "sessions/2026-06-07T18-30-00Z"), paths.sessionDir)
        assertEquals("metadata.json", paths.metadataFile.name)
        assertEquals("events.jsonl", paths.eventsFile.name)
        assertEquals("usage.jsonl", paths.usageFile.name)
        assertEquals("timeline.jsonl", paths.timelineFile.name)
        assertEquals("camera.mp4", paths.cameraFile.name)
        assertEquals("mic.pcm", paths.micPcmFile.name)
        assertEquals("assistant.pcm", paths.assistantPcmFile.name)
        assertTrue(paths.frameFile(12_345L).path.endsWith("frames/frame-000000012345.jpg"))
    }

    @Test
    fun createsMemoryFilePathInsideSessionDirectory() {
        val paths = SessionPaths.create(File("/tmp/realtime-companion"), "session-1")

        assertEquals(
            File("/tmp/realtime-companion/sessions/session-1/cooking-memory.json"),
            paths.memoryFile,
        )
    }
}
