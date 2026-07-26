package com.yujif.thinklet.realtimecompanion.memory

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.File

class CookingMemoryStore(private val file: File) {
    fun load(): CookingMemory {
        if (!file.exists()) return CookingMemory()
        return try {
            parseMemoryFromJsonText(file.readText())
        } catch (e: IOException) {
            CookingMemory()
        } catch (e: JSONException) {
            CookingMemory()
        }
    }

    fun save(memory: CookingMemory) {
        file.parentFile?.mkdirs()
        val json = JSONObject()
            .put("recipe", memory.recipe)
            .put("current_task", memory.currentTask)
            .put("current_phase", memory.currentPhase)
            .put("ingredients_used", JSONArray(memory.ingredientsUsed))
            .put("user_preferences", JSONArray(memory.userPreferences))
            .put("open_questions", JSONArray(memory.openQuestions))
            .put("last_assistant_guidance", memory.lastAssistantGuidance)
        file.writeText(json.toString(2))
    }

    private fun parseMemoryFromJsonText(text: String): CookingMemory =
        parseMemoryFromJSONObject(JSONObject(text))

    private fun parseMemoryFromJSONObject(json: JSONObject): CookingMemory = CookingMemory(
        recipe = json.optString("recipe"),
        currentTask = json.optString("current_task"),
        currentPhase = json.optString("current_phase"),
        ingredientsUsed = json.optJSONArray("ingredients_used").toStringList(),
        userPreferences = json.optJSONArray("user_preferences").toStringList(),
        openQuestions = json.optJSONArray("open_questions").toStringList(),
        lastAssistantGuidance = json.optString("last_assistant_guidance"),
    )

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optString(index).takeIf { it.isNotBlank() }
        }
    }
}
