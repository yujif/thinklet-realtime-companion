package com.yujif.thinklet.realtimecompanion.memory

import com.yujif.thinklet.realtimecompanion.core.SessionPaths
import com.yujif.thinklet.realtimecompanion.openai.CookingMemoryUpdateRequest
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionUseCase
import com.yujif.thinklet.realtimecompanion.session.RealtimeCompanionUseCaseSessionFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CookingMemorySessionTest {
    @Test
    fun cookingSupportLoadsMemoryAndBuildsPromptContext() {
        val memoryFile = tempMemoryFile()
        CookingMemoryStore(memoryFile).save(CookingMemory(recipe = "きのこリゾット"))

        val session = CookingMemorySession.start(
            useCase = RealtimeCompanionUseCase.CookingSupport,
            memoryFile = memoryFile,
        )

        assertTrue(session.promptContext().contains("作っているもの: きのこリゾット"))
    }

    @Test
    fun genericConversationDoesNotCreateOrPersistCookingMemory() {
        val memoryFile = tempMemoryFile()
        val session = CookingMemorySession.start(
            useCase = RealtimeCompanionUseCase.GenericRealtimeConversation,
            memoryFile = memoryFile,
        )

        assertEquals("", session.promptContext())
        assertFalse(session.persist("session_stop"))
        assertFalse(memoryFile.exists())
    }

    @Test
    fun startsFromSessionPathsMemoryFile() {
        val root = kotlin.io.path.createTempDirectory("memory-session-paths").toFile()
        val paths = SessionPaths.create(root, "session-1")
        CookingMemoryStore(paths.memoryFile).save(CookingMemory(recipe = "味噌汁"))

        val session = RealtimeCompanionUseCaseSessionFactory.start(
            useCase = RealtimeCompanionUseCase.CookingSupport,
            paths = paths,
        )

        assertTrue(session.promptContext().contains("作っているもの: 味噌汁"))
    }

    @Test
    fun genericSessionFactoryDoesNotCreateCookingMemorySession() {
        val root = kotlin.io.path.createTempDirectory("generic-session-paths").toFile()
        val paths = SessionPaths.create(root, "session-1")

        val session = RealtimeCompanionUseCaseSessionFactory.start(
            useCase = RealtimeCompanionUseCase.GenericRealtimeConversation,
            paths = paths,
        )

        assertFalse(session is CookingMemorySession)
        assertEquals("", session.promptContext())
        assertFalse(session.persist("session_stop"))
        assertFalse(paths.memoryFile.exists())
    }

    @Test
    fun persistFailureAppendsTimelineEvent() {
        val memoryFile = kotlin.io.path.createTempDirectory("memory-session").toFile()
        val session = CookingMemorySession.start(
            useCase = RealtimeCompanionUseCase.CookingSupport,
            memoryFile = memoryFile,
        )
        val timeline = mutableListOf<String>()

        assertFalse(
            session.persist(
                context = "session_stop",
                appendTimeline = { timeline += it },
                currentTimeMillis = { 456L },
            ),
        )

        assertEquals(
            """{"type":"memory_persist_failed","context":"session_stop","error":"FileNotFoundException","time":456}""",
            timeline.single(),
        )
    }

    @Test
    fun toolRequestUpdatesAndPersistsCookingMemoryForCookingSupport() {
        val memoryFile = tempMemoryFile()
        val session = CookingMemorySession.start(
            useCase = RealtimeCompanionUseCase.CookingSupport,
            memoryFile = memoryFile,
        )
        val outputs = mutableListOf<String>()
        val timeline = mutableListOf<String>()

        session.handleToolRequest(
            request = CookingMemoryUpdateRequest(
                callId = "call-1",
                patch = CookingMemoryPatch(recipe = "味噌汁"),
            ),
            sendFunctionCallOutput = { _, output -> outputs += output },
            refreshInstructions = {},
            appendTimeline = { timeline += it },
            currentTimeMillis = { 123L },
        )

        assertEquals(listOf("""{"status":"ok"}"""), outputs)
        assertTrue(timeline.single().contains("memory_updated"))
        assertEquals(CookingMemory(recipe = "味噌汁"), CookingMemoryStore(memoryFile).load())
    }

    private fun tempMemoryFile(): File {
        val root = kotlin.io.path.createTempDirectory("memory-session").toFile()
        return File(root, "sessions/session-1/cooking-memory.json")
    }
}
