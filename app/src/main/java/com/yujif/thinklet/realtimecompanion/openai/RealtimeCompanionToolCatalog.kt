package com.yujif.thinklet.realtimecompanion.openai

import com.yujif.thinklet.realtimecompanion.memory.CookingMemoryPatch
import org.json.JSONObject

data class CookingMemoryUpdateRequest(
    override val callId: String,
    val patch: CookingMemoryPatch,
) : RealtimeCompanionToolRequest

sealed interface RealtimeCompanionToolRequest {
    val callId: String
}

internal object RealtimeCompanionToolCatalog {
    private const val CookingMemoryUpdateToolName = "update_cooking_memory"

    fun toolsJson(useCase: RealtimeCompanionUseCase): String {
        return if (useCase.hasCapability(RealtimeCompanionCapability.CookingMemory)) {
            cookingMemoryUpdateToolJson()
        } else {
            "[]"
        }
    }

    fun extractToolRequest(text: String): RealtimeCompanionToolRequest? {
        return extractCookingMemoryUpdateRequest(text)
    }

    private fun extractCookingMemoryUpdateRequest(text: String): CookingMemoryUpdateRequest? {
        return runCatching {
            val json = JSONObject(text)
            if (json.optString("type") != "response.function_call_arguments.done") return null
            if (json.optString("name") != CookingMemoryUpdateToolName) return null
            val callId = json.optString("call_id").takeIf { it.isNotBlank() } ?: return null
            val arguments = json.optString("arguments").takeIf { it.isNotBlank() } ?: return null
            CookingMemoryUpdateRequest(
                callId = callId,
                patch = CookingMemoryPatch.fromJson(arguments),
            )
        }.getOrNull()
    }

    private fun cookingMemoryUpdateToolJson(): String {
        return """
            [
              {
                "type":"function",
                "name":"$CookingMemoryUpdateToolName",
                "description":${jsonStringLiteral("現在の料理、工程、材料、好み、未確認事項、直近の案内を、ユーザー発話と映像から明確に分かった場合だけ更新する。")},
                "parameters":{
                  "type":"object",
                  "properties":{
                    "recipe":{"type":"string","description":${jsonStringLiteral("作っている料理名。訂正や確定があった場合だけ入れる。")}},
                    "current_task":{"type":"string","description":${jsonStringLiteral("ユーザーが今取り組んでいる作業。")}},
                    "current_phase":{"type":"string","description":${jsonStringLiteral("現在の工程名。分かる場合だけ入れる。")}},
                    "add_ingredients_used":{"type":"array","items":{"type":"string"},"description":${jsonStringLiteral("新たに使ったと分かった材料。")}},
                    "add_user_preferences":{"type":"array","items":{"type":"string"},"description":${jsonStringLiteral("会話中に分かったユーザーの好み。")}},
                    "add_open_questions":{"type":"array","items":{"type":"string"},"description":${jsonStringLiteral("まだ確認が必要な短い事項。")}},
                    "last_assistant_guidance":{"type":"string","description":${jsonStringLiteral("直近でユーザーに案内した要点。")}}
                  },
                  "additionalProperties":false
                }
              }
            ]
        """.trimIndent()
    }
}
