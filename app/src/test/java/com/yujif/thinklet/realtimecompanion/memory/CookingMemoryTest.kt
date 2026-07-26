package com.yujif.thinklet.realtimecompanion.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookingMemoryTest {
    @Test
    fun promptContextContainsConciseCookingState() {
        val memory = CookingMemory(
            recipe = "きのこリゾット",
            currentTask = "玉ねぎを弱火で炒めている",
            currentPhase = "soffritto",
            ingredientsUsed = listOf("玉ねぎ", "オリーブオイル"),
            userPreferences = listOf("米は少し硬めが好み"),
            openQuestions = listOf("塩を入れたか未確認"),
            lastAssistantGuidance = "焦げそうなら火を少し弱めてください",
        )

        val prompt = memory.toPromptContext()

        assertTrue(prompt.contains("作っているもの: きのこリゾット"))
        assertTrue(prompt.contains("現在の作業: 玉ねぎを弱火で炒めている"))
        assertTrue(prompt.contains("現在フェーズ: soffritto"))
        assertTrue(prompt.contains("使った材料: 玉ねぎ, オリーブオイル"))
        assertTrue(prompt.contains("ユーザーの好み: 米は少し硬めが好み"))
        assertTrue(prompt.contains("未確認事項: 塩を入れたか未確認"))
        assertTrue(prompt.contains("直近の案内: 焦げそうなら火を少し弱めてください"))
    }

    @Test
    fun promptContextReturnsBlankWhenMemoryIsEmpty() {
        val prompt = CookingMemory().toPromptContext()

        assertEquals("", prompt)
    }

    @Test
    fun promptContextSanitizesListValues() {
        val prompt = CookingMemory(
            ingredientsUsed = listOf("  玉ねぎ  ", "", "unknown", "不明", "  オリーブオイル"),
            userPreferences = listOf("  ", "米は少し硬めが好み", "UNKNOWN", "不明"),
            openQuestions = listOf(" ", "塩を入れたか未確認", " unknown "),
        ).toPromptContext()

        assertTrue(prompt.contains("使った材料: 玉ねぎ, オリーブオイル"))
        assertTrue(prompt.contains("ユーザーの好み: 米は少し硬めが好み"))
        assertTrue(prompt.contains("未確認事項: 塩を入れたか未確認"))
        assertFalse(prompt.contains("unknown"))
        assertFalse(prompt.contains("UNKNOWN"))
        assertFalse(prompt.contains("不明"))
    }
}
