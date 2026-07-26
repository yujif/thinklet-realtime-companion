package com.yujif.thinklet.realtimecompanion.openai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeSessionLifecycleTest {
    @Test
    fun identifiesAudioDeltaEventsSoTheEventLogDoesNotDuplicateAssistantPcm() {
        assertTrue(
            RealtimeSession.isAudioDeltaEvent(
                """{"type":"response.output_audio.delta","delta":"AAECAw=="}""",
            ),
        )
        assertFalse(
            RealtimeSession.isAudioDeltaEvent(
                """{"type":"response.output_text.delta","delta":"hello"}""",
            ),
        )
        assertFalse(
            RealtimeSession.isAudioDeltaEvent("""{"type":"response.done"}"""),
        )
        assertFalse(RealtimeSession.isAudioDeltaEvent("not json"))
    }

    @Test
    fun doesNotTreatAudioTranscriptsAsAssistantAudio() {
        val transcript = "{\"type\":\"response.output_audio_transcript.delta\",\"delta\":\"こんにちは\"}"

        assertFalse(RealtimeSession.isAudioDeltaEvent(transcript))
        assertEquals(null, RealtimeSession.extractAssistantAudio(transcript))
    }

    @Test
    fun recordsMicrophoneAppendAsMetadataInsteadOfBase64Payload() {
        val event = clientEventLogEntry(
            json = """{"type":"input_audio_buffer.append","audio":"AAECAw=="}""",
            persistedEventType = "input_audio_buffer.append.sent",
        )

        assertEquals("""{"direction":"client","type":"input_audio_buffer.append.sent"}""", event)
    }

    @Test
    fun disconnectNotifiesListenerOnlyOnce() {
        val disconnectedReasons = mutableListOf<String>()
        val session = RealtimeSession(
            apiKeyProvider = { "test-key" },
            recorderProvider = { null },
            listener = object : RealtimeSession.Listener {
                override fun onConnected() = Unit
                override fun onDisconnected(reason: String) {
                    disconnectedReasons += reason
                }
                override fun onAssistantAudio(bytes: ByteArray) = Unit
                override fun onError(message: String) = Unit
                override fun onUsageEvent(json: String) = Unit
            },
        )

        session.disconnect("manual_stop")
        session.disconnect("manual_stop")
        session.disconnect("extension_timeout")

        assertEquals(listOf("manual_stop"), disconnectedReasons)
    }
}
