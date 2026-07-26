package com.yujif.thinklet.realtimecompanion.memory

import org.json.JSONArray
import org.json.JSONObject

data class CookingMemoryPatch(
    val recipe: String? = null,
    val currentTask: String? = null,
    val currentPhase: String? = null,
    val addIngredientsUsed: List<String> = emptyList(),
    val addUserPreferences: List<String> = emptyList(),
    val addOpenQuestions: List<String> = emptyList(),
    val lastAssistantGuidance: String? = null,
) {
    fun applyTo(memory: CookingMemory): CookingMemory {
        return memory.copy(
            recipe = recipe?.takeIf { it.isNotBlank() } ?: memory.recipe,
            currentTask = currentTask?.takeIf { it.isNotBlank() } ?: memory.currentTask,
            currentPhase = currentPhase?.takeIf { it.isNotBlank() } ?: memory.currentPhase,
            ingredientsUsed = appendUnique(memory.ingredientsUsed, addIngredientsUsed),
            userPreferences = appendUnique(memory.userPreferences, addUserPreferences),
            openQuestions = appendUnique(memory.openQuestions, addOpenQuestions),
            lastAssistantGuidance = lastAssistantGuidance?.takeIf { it.isNotBlank() } ?: memory.lastAssistantGuidance,
        )
    }

    private fun appendUnique(existing: List<String>, additions: List<String>): List<String> {
        return (existing + additions)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    companion object {
        fun fromJson(arguments: String): CookingMemoryPatch {
            val json = JSONObject(arguments)
            return CookingMemoryPatch(
                recipe = json.optNullableString("recipe"),
                currentTask = json.optNullableString("current_task"),
                currentPhase = json.optNullableString("current_phase"),
                addIngredientsUsed = json.optJSONArray("add_ingredients_used").toStringList(),
                addUserPreferences = json.optJSONArray("add_user_preferences").toStringList(),
                addOpenQuestions = json.optJSONArray("add_open_questions").toStringList(),
                lastAssistantGuidance = json.optNullableString("last_assistant_guidance"),
            )
        }

        private fun JSONObject.optNullableString(name: String): String? {
            return if (has(name)) optString(name).takeIf { it.isNotBlank() } else null
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { index ->
                optString(index).trim().takeIf { it.isNotBlank() }
            }
        }
    }
}
