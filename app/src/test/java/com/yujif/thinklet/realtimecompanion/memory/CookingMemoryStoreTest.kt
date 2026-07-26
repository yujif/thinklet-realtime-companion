package com.yujif.thinklet.realtimecompanion.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import java.io.File

class CookingMemoryStoreTest {
    @Test
    fun returnsEmptyMemoryWhenFileDoesNotExist() {
        val root = kotlin.io.path.createTempDirectory("memory-store").toFile()
        val store = CookingMemoryStore(File(root, "cooking-memory.json"))

        assertEquals(CookingMemory(), store.load())
    }

    @Test
    fun savesAndLoadsCookingMemoryToNestedPathAndPersistsExpectedJsonKeys() {
        val root = kotlin.io.path.createTempDirectory("memory-store").toFile()
        val memoryFile = File(root, "sessions/session-1/memory/cooking-memory.json")
        val parentDir = requireNotNull(memoryFile.parentFile)
        assertTrue(!parentDir.exists())

        val store = CookingMemoryStore(memoryFile)
        val memory = CookingMemory(
            recipe = "きのこリゾット",
            currentTask = "ブロードを加えている",
            currentPhase = "cook_rice",
            ingredientsUsed = listOf("米", "ブロード"),
            userPreferences = listOf("硬めが好み"),
            openQuestions = listOf("チーズを入れるか未確認"),
            lastAssistantGuidance = "次は1杯だけ加えてください",
        )

        store.save(memory)

        assertTrue(memoryFile.exists())
        assertTrue(parentDir.exists())
        assertTrue(parentDir.isDirectory)

        val saved = JSONObject(memoryFile.readText())
        assertEquals(memory.recipe, saved.getString("recipe"))
        assertEquals(memory.currentTask, saved.getString("current_task"))
        assertEquals(memory.currentPhase, saved.getString("current_phase"))
        assertEquals(memory.lastAssistantGuidance, saved.getString("last_assistant_guidance"))
        assertEquals(2, saved.getJSONArray("ingredients_used").length())
        assertEquals("米", saved.getJSONArray("ingredients_used").getString(0))
        assertEquals("ブロード", saved.getJSONArray("ingredients_used").getString(1))
        assertEquals(1, saved.getJSONArray("user_preferences").length())
        assertEquals("硬めが好み", saved.getJSONArray("user_preferences").getString(0))
        assertEquals(1, saved.getJSONArray("open_questions").length())
        assertEquals("チーズを入れるか未確認", saved.getJSONArray("open_questions").getString(0))

        assertEquals(memory, store.load())
    }

    @Test
    fun returnsEmptyMemoryWhenJsonIsMalformed() {
        val root = kotlin.io.path.createTempDirectory("memory-store").toFile()
        val memoryFile = File(root, "cooking-memory.json")
        val malformedJson = "{ \"recipe\": \"きのこリゾット\"," // trailing comma => invalid JSON
        memoryFile.writeText(malformedJson)
        val store = CookingMemoryStore(memoryFile)

        assertEquals(CookingMemory(), store.load())
    }
}
