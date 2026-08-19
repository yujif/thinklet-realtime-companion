package com.yujif.thinklet.realtimecompanion.session

import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionResourceStopperTest {
    @Test
    fun outputSinkSpeaksOnlyWhenSpeechAndUtteranceIdArePresent() {
        val spoken = mutableListOf<Pair<String, String>>()

        SessionOutputSink.speakIfPresent(
            speech = "接続しました",
            utteranceId = "connected",
            speak = { speech, utteranceId -> spoken += speech to utteranceId },
        )
        SessionOutputSink.speakIfPresent(
            speech = null,
            utteranceId = "missing_speech",
            speak = { speech, utteranceId -> spoken += speech to utteranceId },
        )
        SessionOutputSink.speakIfPresent(
            speech = "missing utterance",
            utteranceId = null,
            speak = { speech, utteranceId -> spoken += speech to utteranceId },
        )

        assertEquals(listOf("接続しました" to "connected"), spoken)
    }

    @Test
    fun outputSinkAppendsTimelineAndReportsAppendFailures() {
        val appended = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()
        val failure = IllegalStateException("timeline unavailable")

        SessionOutputSink.appendTimelineSafely(
            json = """{"type":"connected"}""",
            appendTimeline = { appended += it },
            onFailure = { failures += it },
        )
        SessionOutputSink.appendTimelineSafely(
            json = """{"type":"error"}""",
            appendTimeline = { throw failure },
            onFailure = { failures += it },
        )

        assertEquals(listOf("""{"type":"connected"}"""), appended)
        assertEquals(listOf(failure), failures)
    }

    @Test
    fun outputSinkEmitsOptionalSpeechAndTimelineTogether() {
        val spoken = mutableListOf<Pair<String, String>>()
        val appended = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()
        val failure = IllegalStateException("timeline unavailable")

        SessionOutputSink.emitOptionalOutputs(
            speech = "接続しました",
            utteranceId = "connected",
            timelineJson = """{"type":"connected"}""",
            speak = { speech, utteranceId -> spoken += speech to utteranceId },
            appendTimeline = { appended += it },
            onTimelineFailure = { failures += it },
        )
        SessionOutputSink.emitOptionalOutputs(
            speech = "timeline failed",
            utteranceId = "timeline_failed",
            timelineJson = """{"type":"error"}""",
            speak = { speech, utteranceId -> spoken += speech to utteranceId },
            appendTimeline = { throw failure },
            onTimelineFailure = { failures += it },
        )
        SessionOutputSink.emitOptionalOutputs(
            speech = null,
            utteranceId = "missing_speech",
            timelineJson = null,
            speak = { speech, utteranceId -> spoken += speech to utteranceId },
            appendTimeline = { appended += it },
            onTimelineFailure = { failures += it },
        )

        assertEquals(
            listOf(
                "接続しました" to "connected",
                "timeline failed" to "timeline_failed",
            ),
            spoken,
        )
        assertEquals(listOf("""{"type":"connected"}"""), appended)
        assertEquals(listOf(failure), failures)
    }

    @Test
    fun stopsRealtimeAndLocalResourcesInShutdownOrder() {
        val events = mutableListOf<String>()

        SessionResourceStopper.stopActiveResources(
            reason = "manual_stop",
            disconnectRealtime = { events += "realtime:$it" },
            stopMicrophone = { events += "microphone" },
            stopAssistantAudio = { events += "assistant_audio" },
            stopCamera = { events += "camera" },
        )

        assertEquals(
            listOf(
                "realtime:manual_stop",
                "microphone",
                "assistant_audio",
                "camera",
            ),
            events,
        )
    }

    @Test
    fun stopPresentationReturnsStatusAndOptionalSpeechForReason() {
        assertEquals(
            SessionStopPresentation(
                statusText = "停止しました",
                speech = "延長されなかったので停止しました",
                utteranceId = "stopped_extension_timeout",
            ),
            SessionStopPresenter.presentStopped("extension_timeout"),
        )

        assertEquals(
            SessionStopPresentation(
                statusText = "停止しました",
                speech = null,
                utteranceId = null,
            ),
            SessionStopPresenter.presentStopped("activity_destroy"),
        )
    }

    @Test
    fun realtimeSessionEventPresentationReturnsStatusSpeechAndTimeline() {
        assertEquals(
            RealtimeSessionEventPresentation(
                statusText = "接続しました",
                speech = "接続しました",
                utteranceId = "connected",
                timelineJson = """{"type":"connected","time":1234}""",
            ),
            RealtimeSessionEventPresenter.presentConnected(nowMillis = 1_234L),
        )

        assertEquals(
            RealtimeSessionEventPresentation(
                statusText = "停止しました: network_closed",
                speech = null,
                utteranceId = null,
                timelineJson = """{"type":"disconnected","reason":"network_closed","time":2345}""",
            ),
            RealtimeSessionEventPresenter.presentDisconnected(
                reason = "network_closed",
                nowMillis = 2_345L,
            ),
        )

        assertEquals(
            RealtimeSessionEventPresentation(
                statusText = "エラー: missing API key",
                speech = "接続に失敗しました",
                utteranceId = "connection_error",
                timelineJson = """{"type":"error","message":"missing API key"}""",
            ),
            RealtimeSessionEventPresenter.presentError("missing API key"),
        )
    }

    @Test
    fun sessionStartPresentationReturnsStatusSpeechAndUtteranceId() {
        assertEquals(
            SessionStartPresentation(
                statusText = "APIキーを入力してください",
                speech = "APIキーを入力してください",
                utteranceId = "missing_api_key",
            ),
            SessionStartPresenter.presentMissingApiKey(),
        )

        assertEquals(
            SessionStartPresentation(
                statusText = "空き容量が足りません",
                speech = "空き容量が足りません",
                utteranceId = "storage_low",
            ),
            SessionStartPresenter.presentNotEnoughStorage(),
        )

        assertEquals(
            SessionStartPresentation(
                statusText = "リアルタイム相談を開始します",
                speech = "リアルタイム相談を開始します",
                utteranceId = "starting",
            ),
            SessionStartPresenter.presentStarting(),
        )
    }

    @Test
    fun permissionPresentationReturnsStatusForGrantResult() {
        assertEquals(
            SessionPermissionPresenter.presentPermissionResult(hasRequiredPermissions = true),
            "権限OK。中央ボタン短押しで開始できます。",
        )

        assertEquals(
            SessionPermissionPresenter.presentPermissionResult(hasRequiredPermissions = false),
            "カメラとマイク権限が必要です",
        )
    }

    @Test
    fun useCaseSelectionPresentationReturnsStatusSpeechAndUtteranceId() {
        assertEquals(
            SessionUseCaseSelectionPresentation(
                statusText = "英語練習モードに切り替えました",
                speech = "英語練習モードに切り替えました",
                utteranceId = "use_case_english_conversation_learning",
            ),
            SessionUseCaseSelectionPresenter.presentSelected(
                RealtimeCompanionUseCase.EnglishConversationLearning,
            ),
        )
    }

    @Test
    fun usageHandlerRecordsUsageAndRequestsBudgetStop() {
        val appendedUsage = mutableListOf<String>()
        val spoken = mutableListOf<Pair<String, String>>()
        val stopReasons = mutableListOf<String>()
        val handler = RealtimeSessionUsageHandler(maxCostUsd = 0.01)
        val usageJson = """
            {
              "response": {
                "usage": {
                  "input_token_details": {
                    "audio_tokens": 1000
                  },
                  "output_token_details": {
                    "audio_tokens": 0
                  }
                }
              }
            }
        """.trimIndent()

        handler.handleUsageEvent(
            json = usageJson,
            appendUsage = { appendedUsage += it },
            speak = { speech, utteranceId -> spoken += speech to utteranceId },
            stop = { stopReasons += it },
        )

        assertEquals(listOf(usageJson), appendedUsage)
        assertEquals(listOf("上限に近づいています。停止します" to "budget_stop"), spoken)
        assertEquals(listOf("budget_guard"), stopReasons)
    }

    @Test
    fun usageHandlerAppliesCachedInputDiscountBeforeBudgetCheck() {
        val spoken = mutableListOf<Pair<String, String>>()
        val stopReasons = mutableListOf<String>()
        val handler = RealtimeSessionUsageHandler(maxCostUsd = 0.01)
        // 1000 audio input tokens at list price ($32/1M) would be $0.032 and trip the
        // $0.01 guard; fully cached ($0.40/1M) they are $0.0004 and must not.
        val usageJson = """
            {
              "response": {
                "usage": {
                  "input_token_details": {
                    "audio_tokens": 1000,
                    "cached_tokens": 1000,
                    "cached_tokens_details": {
                      "audio_tokens": 1000
                    }
                  },
                  "output_token_details": {
                    "audio_tokens": 0
                  }
                }
              }
            }
        """.trimIndent()

        handler.handleUsageEvent(
            json = usageJson,
            appendUsage = {},
            speak = { speech, utteranceId -> spoken += speech to utteranceId },
            stop = { stopReasons += it },
        )

        assertEquals(emptyList<Pair<String, String>>(), spoken)
        assertEquals(emptyList<String>(), stopReasons)
    }

    @Test
    fun usageHandlerIgnoresMalformedUsageForBudgetButStillRecordsRawJson() {
        val appendedUsage = mutableListOf<String>()
        val spoken = mutableListOf<Pair<String, String>>()
        val stopReasons = mutableListOf<String>()
        val handler = RealtimeSessionUsageHandler(maxCostUsd = 0.01)

        handler.handleUsageEvent(
            json = """{"type":"response.done"}""",
            appendUsage = { appendedUsage += it },
            speak = { speech, utteranceId -> spoken += speech to utteranceId },
            stop = { stopReasons += it },
        )

        assertEquals(listOf("""{"type":"response.done"}"""), appendedUsage)
        assertEquals(emptyList<Pair<String, String>>(), spoken)
        assertEquals(emptyList<String>(), stopReasons)
    }
}
