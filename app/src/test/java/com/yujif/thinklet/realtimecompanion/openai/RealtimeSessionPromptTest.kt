package com.yujif.thinklet.realtimecompanion.openai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class RealtimeSessionPromptTest {
    @Test
    fun useCaseSelectorResolvesKnownIdsAndFallsBackToGenericConversation() {
        assertEquals(
            RealtimeCompanionUseCase.GenericRealtimeConversation,
            RealtimeCompanionUseCase.fromId(""),
        )
        assertEquals(
            RealtimeCompanionUseCase.GenericRealtimeConversation,
            RealtimeCompanionUseCase.fromId("generic_realtime_conversation"),
        )
        assertEquals(
            RealtimeCompanionUseCase.CookingSupport,
            RealtimeCompanionUseCase.fromId("cooking_support"),
        )
        assertEquals(
            RealtimeCompanionUseCase.EnglishConversationLearning,
            RealtimeCompanionUseCase.fromId("english_conversation_learning"),
        )
        assertEquals(
            RealtimeCompanionUseCase.GenericRealtimeConversation,
            RealtimeCompanionUseCase.fromId("unknown"),
        )
    }

    @Test
    fun useCasesCycleInGenericCookingEnglishOrder() {
        assertEquals(
            RealtimeCompanionUseCase.CookingSupport,
            RealtimeCompanionUseCase.nextAfter(RealtimeCompanionUseCase.GenericRealtimeConversation),
        )
        assertEquals(
            RealtimeCompanionUseCase.EnglishConversationLearning,
            RealtimeCompanionUseCase.nextAfter(RealtimeCompanionUseCase.CookingSupport),
        )
        assertEquals(
            RealtimeCompanionUseCase.GenericRealtimeConversation,
            RealtimeCompanionUseCase.nextAfter(RealtimeCompanionUseCase.EnglishConversationLearning),
        )
        assertEquals(
            RealtimeCompanionUseCase.EnglishConversationLearning,
            RealtimeCompanionUseCase.previousBefore(RealtimeCompanionUseCase.GenericRealtimeConversation),
        )
    }

    @Test
    fun useCasesExposeJapaneseSpeechLabels() {
        assertEquals("汎用モード", RealtimeCompanionUseCase.GenericRealtimeConversation.japaneseSpeechName)
        assertEquals("料理モード", RealtimeCompanionUseCase.CookingSupport.japaneseSpeechName)
        assertEquals("英語練習モード", RealtimeCompanionUseCase.EnglishConversationLearning.japaneseSpeechName)
    }

    @Test
    fun sessionUpdateUsesGenericRealtimeConversationByDefault() {
        val update = JSONObject(realtimeSessionUpdateJson(model = "gpt-realtime-2", voice = "marin"))
        val session = update.getJSONObject("session")
        val instructions = session.getString("instructions")

        assertTrue(instructions.contains("Realtime companion"))
        assertTrue(instructions.contains("日本語で短く"))
        assertFalse(instructions.contains("料理に関する質問"))
        assertFalse(instructions.contains("英会話練習"))
        assertEquals(0, session.getJSONArray("tools").length())
    }

    @Test
    fun genericConversationAsksForUserIntentBeforeVisualTaskAdvice() {
        val update = JSONObject(realtimeSessionUpdateJson(model = "gpt-realtime-2", voice = "marin"))
        val instructions = update.getJSONObject("session").getString("instructions")

        assertTrue(instructions.contains("最初に、何を一緒にしたいか"))
        assertTrue(instructions.contains("カメラ画像だけから作業目的を決めつけない"))
        assertTrue(instructions.contains("ユーザーが明示していない目的、意図、次の行動を推測しない"))
        assertTrue(instructions.contains("視覚情報は状況説明と補助情報に留める"))
    }

    @Test
    fun sessionUpdateUsesGeneralCookingAssistantInstructions() {
        val update = realtimeSessionUpdateJson(
            model = "gpt-realtime-2",
            voice = "marin",
            useCase = RealtimeCompanionUseCase.CookingSupport,
        )

        assertTrue(update.contains("\"type\":\"session.update\""))
        assertTrue(update.contains("\"model\":\"gpt-realtime-2\""))
        assertTrue(update.contains("\"voice\":\"marin\""))
        assertTrue(update.contains("\"transcription\""))
        assertTrue(update.contains("\"model\":\"gpt-4o-mini-transcribe\""))
        assertTrue(update.contains("\"language\":\"ja\""))
        assertTrue(update.contains("料理に関する質問に答える汎用的な料理assistantAI"))
        assertTrue(update.contains("レシピを固定せず"))
        assertTrue(update.contains("安全を最優先"))
        assertTrue(update.contains("短く実用的に"))
        assertFalse(update.contains("黒和えそうめん"))
        assertFalse(update.contains("そうめん：1.5束"))
        assertFalse(update.contains("にんにくは、そうめんの茹で時間にプラスして3分"))
        assertFalse(update.contains("ごま油を煙が出るまで熱し"))
        assertFalse(update.contains("冷蔵庫の中身を見ながら、作れる料理"))
    }

    @Test
    fun sessionUpdateUsesSelectedEnglishLearningUseCase() {
        val update = JSONObject(
            realtimeSessionUpdateJson(
                model = "gpt-realtime-2",
                voice = "marin",
                useCase = RealtimeCompanionUseCase.EnglishConversationLearning,
            ),
        )
        val session = update.getJSONObject("session")
        val instructions = session.getString("instructions")

        assertTrue(instructions.contains("英会話練習"))
        assertTrue(instructions.contains("自然な英語表現"))
        assertTrue(instructions.contains("日本語での短い説明"))
        assertTrue(instructions.contains("発音練習"))
        assertFalse(instructions.contains("料理に関する質問"))
        assertEquals(0, session.getJSONArray("tools").length())
    }

    @Test
    fun sessionUpdateIncludesCookingMemoryContextWhenProvided() {
        val update = realtimeSessionUpdateJson(
            model = "gpt-realtime-2",
            voice = "marin",
            useCaseContext = "調理メモリー:\n作っているもの: きのこリゾット\n現在の作業: ブロードを加えている",
            useCase = RealtimeCompanionUseCase.CookingSupport,
        )

        assertTrue(update.contains("調理メモリー"))
        assertTrue(update.contains("作っているもの: きのこリゾット"))
        assertTrue(update.contains("現在の作業: ブロードを加えている"))
    }

    @Test
    fun sessionUpdateMemoryContextIsJsonEscapedButParsedAsOriginal() {
        val memoryContext =
            "調理メモリー:\n内容: \"きのこリゾット\" のメモです。\nメモ内に\\を含みます。"
        val update = realtimeSessionUpdateJson(
            model = "gpt-realtime-2",
            voice = "marin",
            useCaseContext = memoryContext,
            useCase = RealtimeCompanionUseCase.CookingSupport,
        )

        val instructions = JSONObject(update).getJSONObject("session").getString("instructions")

        assertTrue(instructions.contains(memoryContext))
    }

    @Test
    fun sessionUpdateWithoutMemoryContextDoesNotIncludeMemoryGuidance() {
        val update = realtimeSessionUpdateJson(
            model = "gpt-realtime-2",
            voice = "marin",
            useCase = RealtimeCompanionUseCase.CookingSupport,
        )

        val instructions = JSONObject(update).getJSONObject("session").getString("instructions")

        assertFalse(instructions.contains("この調理メモリーを優先して"))
        assertTrue(instructions.startsWith(RealtimeCompanionUseCase.CookingSupport.instructions))
    }

    @Test
    fun sessionUpdateRegistersCookingMemoryUpdateTool() {
        val update = JSONObject(
            realtimeSessionUpdateJson(
                model = "gpt-realtime-2",
                voice = "marin",
                useCase = RealtimeCompanionUseCase.CookingSupport,
            ),
        )
        val session = update.getJSONObject("session")
        val tools = session.getJSONArray("tools")

        assertEquals("auto", session.getString("tool_choice"))
        assertEquals(1, tools.length())

        val tool = tools.getJSONObject(0)
        assertEquals("update_cooking_memory", tool.getString("name"))

        val parameters = tool.getJSONObject("parameters")
        val properties = parameters.getJSONObject("properties")

        assertTrue(properties.has("recipe"))
        assertTrue(properties.has("current_task"))
        assertTrue(properties.has("current_phase"))
        assertTrue(properties.has("add_ingredients_used"))
        assertTrue(properties.has("add_user_preferences"))
        assertTrue(properties.has("add_open_questions"))
        assertTrue(properties.has("last_assistant_guidance"))
        assertFalse(parameters.getBoolean("additionalProperties"))
    }

    @Test
    fun sessionUpdateInstructionsTellModelWhenToUpdateCookingMemory() {
        val update = JSONObject(
            realtimeSessionUpdateJson(
                model = "gpt-realtime-2",
                voice = "marin",
                useCase = RealtimeCompanionUseCase.CookingSupport,
            ),
        )
        val instructions = update.getJSONObject("session").getString("instructions")

        assertTrue(instructions.contains("update_cooking_memory"))
        assertTrue(instructions.contains("料理名"))
        assertTrue(instructions.contains("現在の作業"))
        assertTrue(instructions.contains("使った材料"))
        assertTrue(instructions.contains("直近の案内"))
        assertTrue(instructions.contains("不確かな映像推定"))
    }

    @Test
    fun extractsCookingMemoryPatchFromFunctionCallArgumentsDoneEvent() {
        val event = """
            {
              "type": "response.function_call_arguments.done",
              "name": "update_cooking_memory",
              "call_id": "call_123",
              "arguments": "{\"recipe\":\"きのこリゾット\",\"current_task\":\"ブロードを加えた\"}"
            }
        """.trimIndent()

        val request = RealtimeCompanionToolCatalog.extractToolRequest(event) as? CookingMemoryUpdateRequest

        assertEquals("call_123", request?.callId)
        assertEquals("きのこリゾット", request?.patch?.recipe)
        assertEquals("ブロードを加えた", request?.patch?.currentTask)
    }

    @Test
    fun returnsNullForUnrelatedFunctionCallEventType() {
        val event = """
            {
              "type": "response.output_item.done",
              "name": "update_cooking_memory",
              "call_id": "call_123",
              "arguments": "{\"recipe\":\"きのこリゾット\"}"
            }
        """.trimIndent()

        val request = RealtimeCompanionToolCatalog.extractToolRequest(event)

        assertEquals(null, request)
    }

    @Test
    fun returnsNullForUnrelatedFunctionName() {
        val event = """
            {
              "type": "response.function_call_arguments.done",
              "name": "different_tool",
              "call_id": "call_123",
              "arguments": "{\"recipe\":\"きのこリゾット\"}"
            }
        """.trimIndent()

        val request = RealtimeCompanionToolCatalog.extractToolRequest(event)

        assertEquals(null, request)
    }

    @Test
    fun returnsNullForMalformedFunctionCallArguments() {
        val event = """
            {
              "type": "response.function_call_arguments.done",
              "name": "update_cooking_memory",
              "call_id": "call_123",
              "arguments": "{not-json"
            }
        """.trimIndent()

        val request = RealtimeCompanionToolCatalog.extractToolRequest(event)

        assertEquals(null, request)
    }

    @Test
    fun returnsNullForBlankCallIdOrArguments() {
        val blankCallId = """
            {
              "type": "response.function_call_arguments.done",
              "name": "update_cooking_memory",
              "call_id": " ",
              "arguments": "{\"recipe\":\"きのこリゾット\"}"
            }
        """.trimIndent()
        val blankArguments = """
            {
              "type": "response.function_call_arguments.done",
              "name": "update_cooking_memory",
              "call_id": "call_123",
              "arguments": " "
            }
        """.trimIndent()

        assertEquals(null, RealtimeCompanionToolCatalog.extractToolRequest(blankCallId))
        assertEquals(null, RealtimeCompanionToolCatalog.extractToolRequest(blankArguments))
    }

    @Test
    fun functionCallOutputJsonCreatesFunctionCallOutputOnly() {
        val json = JSONObject(functionCallOutputJson(callId = "call_123", output = "ok"))

        assertEquals("conversation.item.create", json.getString("type"))
        assertFalse(json.toString().contains("response.create"))

        val item = json.getJSONObject("item")
        assertEquals("function_call_output", item.getString("type"))
        assertEquals("call_123", item.getString("call_id"))
        assertEquals("ok", item.getString("output"))
    }
}
