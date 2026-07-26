package com.yujif.thinklet.realtimecompanion.memory

data class CookingMemory(
    val recipe: String = "",
    val currentTask: String = "",
    val currentPhase: String = "",
    val ingredientsUsed: List<String> = emptyList(),
    val userPreferences: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val lastAssistantGuidance: String = "",
) {
    fun toPromptContext(): String {
        val lines = mutableListOf<String>()
        recipe.takeIf { it.isNotBlank() }?.let { lines += "作っているもの: $it" }
        currentTask.takeIf { it.isNotBlank() }?.let { lines += "現在の作業: $it" }
        currentPhase.takeIf { it.isNotBlank() }?.let { lines += "現在フェーズ: $it" }
        sanitizeListValues(ingredientsUsed)
            .takeIf { it.isNotEmpty() }
            ?.let { lines += "使った材料: ${it.joinToString(", ")}" }
        sanitizeListValues(userPreferences)
            .takeIf { it.isNotEmpty() }
            ?.let { lines += "ユーザーの好み: ${it.joinToString(", ")}" }
        sanitizeListValues(openQuestions)
            .takeIf { it.isNotEmpty() }
            ?.let { lines += "未確認事項: ${it.joinToString(", ")}" }
        lastAssistantGuidance.takeIf { it.isNotBlank() }?.let { lines += "直近の案内: $it" }
        if (lines.isEmpty()) return ""
        lines.add(0, "調理メモリー:")
        return lines.joinToString("\n")
    }

    private fun sanitizeListValues(values: List<String>): List<String> = values
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) && it != "不明" }
}
