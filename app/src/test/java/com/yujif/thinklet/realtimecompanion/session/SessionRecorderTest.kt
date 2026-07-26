package com.yujif.thinklet.realtimecompanion.session

import com.yujif.thinklet.realtimecompanion.core.SessionPaths
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionUseCase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

class SessionRecorderTest {
    @Test
    fun metadataFactoryBuildsRecorderMetadataFromSessionPathsAndUseCase() {
        val root = createTempDir(prefix = "session-recorder-test")
        val paths = SessionPaths.create(root, "2026-06-07T18-30-00Z")

        val metadata = SessionMetadataFactory.create(
            paths = paths,
            model = "gpt-realtime-2",
            useCase = RealtimeCompanionUseCase.CookingSupport,
            frameCadenceMillis = 2_000,
            maxCostUsd = 2.0,
            elapsedNoticeMillis = 10 * 60 * 1_000L,
            extensionPromptMillis = 30 * 60 * 1_000L,
            extensionTimeoutMillis = 30 * 1_000L,
            minFreeBytesRequired = 1_000_000_000,
        )

        assertEquals("2026-06-07T18-30-00Z", metadata.sessionId)
        assertEquals("gpt-realtime-2", metadata.model)
        assertEquals("cooking_support", metadata.useCaseId)
        assertEquals("Cooking Support", metadata.useCaseLabel)
        assertEquals(2_000L, metadata.frameCadenceMillis)
        assertEquals(2.0, metadata.maxCostUsd, 0.0)
        assertEquals(10 * 60 * 1_000L, metadata.elapsedNoticeMillis)
        assertEquals(30 * 60 * 1_000L, metadata.extensionPromptMillis)
        assertEquals(30 * 1_000L, metadata.extensionTimeoutMillis)
        assertEquals(1_000_000_000L, metadata.minFreeBytesRequired)
    }

    @Test
    fun recordingStarterCreatesPathsRecorderAndMetadata() {
        val root = createTempDir(prefix = "session-recorder-test")

        val result = SessionRecordingStarter.start(
            root = root,
            sessionId = "2026-06-07T18-30-00Z",
            config = SessionRecordingConfig(
                model = "gpt-realtime-2",
                useCase = RealtimeCompanionUseCase.CookingSupport,
                frameCadenceMillis = 2_000,
                maxCostUsd = 2.0,
                elapsedNoticeMillis = 10 * 60 * 1_000L,
                extensionPromptMillis = 30 * 60 * 1_000L,
                extensionTimeoutMillis = 30 * 1_000L,
                minFreeBytesRequired = 1_000_000_000,
            ),
        ) as SessionRecordingStartResult.Started

        assertEquals("2026-06-07T18-30-00Z", result.paths.sessionDir.name)
        assertTrue(result.paths.sessionDir.exists())
        result.recorder.appendTimeline("""{"type":"starter_test"}""")
        assertEquals("""{"type":"starter_test"}""" + "\n", result.paths.timelineFile.readText())

        val metadataJson = JSONObject(result.paths.metadataFile.readText())
        assertEquals("gpt-realtime-2", metadataJson.getString("model"))
        assertEquals("cooking_support", metadataJson.getString("use_case_id"))
        assertEquals("Cooking Support", metadataJson.getString("use_case_label"))
    }

    @Test
    fun stopRecorderAppendsStoppedTimelineEvent() {
        val root = createTempDir(prefix = "session-recorder-test")
        val paths = SessionPaths.create(root, "2026-06-07T18-30-00Z")
        val recorder = SessionRecorder(paths)

        SessionStopRecorder.recordStopped(
            recorder = recorder,
            reason = "manual_stop",
            nowMillis = 1_234L,
        )

        assertEquals(
            """{"type":"stopped","reason":"manual_stop","time":1234}""" + "\n",
            paths.timelineFile.readText(),
        )
    }

    @Test
    fun startRecorderAppendsStartRequestedTimelineEvent() {
        val root = createTempDir(prefix = "session-recorder-test")
        val paths = SessionPaths.create(root, "2026-06-07T18-30-00Z")
        val recorder = SessionRecorder(paths)

        SessionStartRecorder.recordStartRequested(
            recorder = recorder,
            manual = true,
            nowMillis = 1_234L,
        )

        assertEquals(
            """{"type":"start_requested","manual":true,"time":1234}""" + "\n",
            paths.timelineFile.readText(),
        )
    }

    @Test
    fun createsSessionFilesAndRedactsEvents() {
        val root = createTempDir(prefix = "session-recorder-test")
        val paths = SessionPaths.create(root, "2026-06-07T18-30-00Z")
        val recorder = SessionRecorder(paths)

        recorder.start(
            SessionMetadata(
                sessionId = "2026-06-07T18-30-00Z",
                model = "gpt-realtime-2",
                useCaseId = "generic_realtime_conversation",
                useCaseLabel = "Generic Realtime Conversation",
                frameCadenceMillis = 2_000,
                maxCostUsd = 2.0,
                elapsedNoticeMillis = 10 * 60 * 1_000L,
                extensionPromptMillis = 30 * 60 * 1_000L,
                extensionTimeoutMillis = 30 * 1_000L,
                minFreeBytesRequired = 1_000_000_000,
            ),
        )
        recorder.appendEvent("""{"Authorization":"Bearer sk-secret","type":"session.created"}""")
        recorder.appendTimeline("""{"type":"connected"}""")
        recorder.appendUsage("""{"estimated_cost_usd":0.1}""")
        recorder.appendMicPcm(byteArrayOf(1, 2, 3))
        recorder.appendAssistantPcm(byteArrayOf(4, 5))
        val frame = recorder.writeFrame(123L, byteArrayOf(9, 8, 7))

        val metadataJson = JSONObject(paths.metadataFile.readText())
        assertEquals("gpt-realtime-2", metadataJson.getString("model"))
        assertEquals("generic_realtime_conversation", metadataJson.getString("use_case_id"))
        assertEquals("Generic Realtime Conversation", metadataJson.getString("use_case_label"))
        assertEquals(10 * 60 * 1_000L, metadataJson.getLong("elapsed_notice_millis"))
        assertEquals(30 * 60 * 1_000L, metadataJson.getLong("extension_prompt_millis"))
        assertEquals(30 * 1_000L, metadataJson.getLong("extension_timeout_millis"))
        assertFalse(paths.eventsFile.readText().contains("sk-secret"))
        assertTrue(paths.eventsFile.readText().contains("Bearer [REDACTED]"))
        assertEquals("""{"type":"connected"}""" + "\n", paths.timelineFile.readText())
        assertEquals("""{"estimated_cost_usd":0.1}""" + "\n", paths.usageFile.readText())
        assertEquals(listOf<Byte>(1, 2, 3), paths.micPcmFile.readBytes().toList())
        assertEquals(listOf<Byte>(4, 5), paths.assistantPcmFile.readBytes().toList())
        assertEquals(listOf<Byte>(9, 8, 7), frame.readBytes().toList())
    }

    @Test
    fun refusesToStartWhenFreeSpaceIsBelowRequirement() {
        val root = createTempDir(prefix = "session-recorder-test")
        val paths = SessionPaths.create(root, "low-space")
        val recorder = SessionRecorder(paths) { 100L }

        val result = recorder.start(
            SessionMetadata(
                sessionId = "low-space",
                model = "gpt-realtime-2",
                useCaseId = "generic_realtime_conversation",
                useCaseLabel = "Generic Realtime Conversation",
                frameCadenceMillis = 2_000,
                maxCostUsd = 2.0,
                elapsedNoticeMillis = 10 * 60 * 1_000L,
                extensionPromptMillis = 30 * 60 * 1_000L,
                extensionTimeoutMillis = 30 * 1_000L,
                minFreeBytesRequired = 1_000L,
            ),
        )

        assertEquals(SessionRecorderStartResult.NotEnoughStorage, result)
        assertFalse(paths.sessionDir.exists())
    }

    @Test
    fun flagsCapacityExceededOnceWrittenBytesReachTheSessionBudget() {
        val root = createTempDir(prefix = "session-recorder-test")
        val paths = SessionPaths.create(root, "capacity-test")
        val recorder = SessionRecorder(paths, maxSessionBytes = 10L, maxExternalBytes = 6L)

        assertFalse(recorder.shouldStopForStorage)

        recorder.appendMicPcm(byteArrayOf(1, 2, 3, 4, 5))
        assertFalse(recorder.shouldStopForStorage)

        recorder.appendMicPcm(byteArrayOf(6, 7, 8, 9, 10))
        assertTrue(recorder.hasExceededCapacity)
        assertTrue(recorder.shouldStopForStorage)
    }

    @Test
    fun countsUtf8BytesAndExternalCameraBytesInTheSessionBudget() {
        val root = createTempDir(prefix = "session-recorder-test")
        val paths = SessionPaths.create(root, "combined-capacity-test")
        val recorder = SessionRecorder(paths, maxSessionBytes = 10L, maxExternalBytes = 6L)

        recorder.appendTimeline("あ") // three UTF-8 bytes plus the newline

        assertEquals(4L, recorder.currentWrittenBytes)
        assertFalse(recorder.shouldStopForStorage(externalBytes = 5L))
        assertTrue(recorder.shouldStopForStorage(externalBytes = 6L))
    }

    @Test
    fun flagsWriteFailureWithoutThrowingWhenTargetDirectoryIsUnwritable() {
        val root = createTempDir(prefix = "session-recorder-test")
        val paths = SessionPaths.create(root, "write-failure-test")
        val recorder = SessionRecorder(paths)

        // sessionDir is never created (start() not called) and its parent doesn't exist either,
        // so appendBytes' mkdirs()+appendBytes() sequence still succeeds in creating it — force
        // a real failure by making the target path a file instead of the directory it expects.
        paths.sessionDir.parentFile?.mkdirs()
        paths.sessionDir.writeText("not a directory")

        recorder.appendMicPcm(byteArrayOf(1, 2, 3))

        assertTrue(recorder.hasWriteFailed)
        assertTrue(recorder.shouldStopForStorage)
    }

    @Test
    fun serializesEventAppendsAcrossThreads() {
        val appendEvent = SessionRecorder::class.java.getDeclaredMethod(
            "appendEvent",
            String::class.java,
        )

        assertTrue(Modifier.isSynchronized(appendEvent.modifiers))
    }

    private fun createTempDir(prefix: String): File {
        return kotlin.io.path.createTempDirectory(prefix).toFile()
    }
}
