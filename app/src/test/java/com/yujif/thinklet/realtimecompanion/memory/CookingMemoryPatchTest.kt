package com.yujif.thinklet.realtimecompanion.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class CookingMemoryPatchTest {
    @Test
    fun appliesExplicitMemoryFieldsAndAppendsListItems() {
        val original = CookingMemory(
            recipe = "料理未確定",
            currentTask = "相談中",
            ingredientsUsed = listOf("米"),
            userPreferences = listOf("硬めが好み"),
        )
        val patch = CookingMemoryPatch(
            recipe = "きのこリゾット",
            currentTask = "ブロードを1杯加えた",
            currentPhase = "cook_rice",
            addIngredientsUsed = listOf("ブロード"),
            addUserPreferences = listOf("チーズは少なめ"),
            addOpenQuestions = listOf("塩を入れたか未確認"),
            lastAssistantGuidance = "次は混ぜながら様子を見てください",
        )

        val updated = patch.applyTo(original)

        assertEquals("きのこリゾット", updated.recipe)
        assertEquals("ブロードを1杯加えた", updated.currentTask)
        assertEquals("cook_rice", updated.currentPhase)
        assertEquals(listOf("米", "ブロード"), updated.ingredientsUsed)
        assertEquals(listOf("硬めが好み", "チーズは少なめ"), updated.userPreferences)
        assertEquals(listOf("塩を入れたか未確認"), updated.openQuestions)
        assertEquals("次は混ぜながら様子を見てください", updated.lastAssistantGuidance)
    }

    @Test
    fun trimsListAdditionsDropsBlanksAndDeduplicatesWhitespaceVariants() {
        val original = CookingMemory(
            ingredientsUsed = listOf("ブロード", " 米 "),
            userPreferences = listOf("硬めが好み"),
            openQuestions = listOf("塩を入れたか未確認"),
        )
        val patch = CookingMemoryPatch(
            addIngredientsUsed = listOf(" ブロード ", "", "  ", "米", " きのこ "),
            addUserPreferences = listOf(" 硬めが好み ", "  ", "チーズは少なめ"),
            addOpenQuestions = listOf(" 塩を入れたか未確認 ", "", "チーズを入れるか未確認 "),
        )

        val updated = patch.applyTo(original)

        assertEquals(listOf("ブロード", "米", "きのこ"), updated.ingredientsUsed)
        assertEquals(listOf("硬めが好み", "チーズは少なめ"), updated.userPreferences)
        assertEquals(listOf("塩を入れたか未確認", "チーズを入れるか未確認"), updated.openQuestions)
    }

    @Test
    fun parsesPatchFromRealtimeToolArgumentsJson() {
        val patch = CookingMemoryPatch.fromJson(
            """
            {
              "recipe": "きのこリゾット",
              "current_task": "きのこを炒めている",
              "add_ingredients_used": ["きのこ", "オリーブオイル"]
            }
            """.trimIndent(),
        )

        assertEquals("きのこリゾット", patch.recipe)
        assertEquals("きのこを炒めている", patch.currentTask)
        assertEquals(listOf("きのこ", "オリーブオイル"), patch.addIngredientsUsed)
    }
}
