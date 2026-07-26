package com.yujif.thinklet.realtimecompanion.session

object SessionOutputSink {
    fun emitOptionalOutputs(
        speech: String?,
        utteranceId: String?,
        timelineJson: String?,
        speak: (String, String) -> Unit,
        appendTimeline: (String) -> Unit,
        onTimelineFailure: (Throwable) -> Unit,
    ) {
        speakIfPresent(speech, utteranceId, speak)
        timelineJson?.let {
            appendTimelineSafely(
                json = it,
                appendTimeline = appendTimeline,
                onFailure = onTimelineFailure,
            )
        }
    }

    fun speakIfPresent(
        speech: String?,
        utteranceId: String?,
        speak: (String, String) -> Unit,
    ) {
        if (speech != null && utteranceId != null) {
            speak(speech, utteranceId)
        }
    }

    fun appendTimelineSafely(
        json: String,
        appendTimeline: (String) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        runCatching {
            appendTimeline(json)
        }.onFailure(onFailure)
    }
}
