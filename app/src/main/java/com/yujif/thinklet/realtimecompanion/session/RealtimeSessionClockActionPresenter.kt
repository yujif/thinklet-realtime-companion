package com.yujif.thinklet.realtimecompanion.session

import org.json.JSONObject

data class RealtimeClockActionPresentation(
    val statusText: String?,
    val speech: String?,
    val utteranceId: String?,
    val timelineJson: String?,
    val stopReason: String?,
)

object RealtimeSessionClockActionPresenter {
    fun present(
        action: RealtimeClockAction,
        nowMillis: Long,
    ): RealtimeClockActionPresentation {
        return when (action) {
            is RealtimeClockAction.AnnounceElapsed -> RealtimeClockActionPresentation(
                statusText = action.speech,
                speech = action.speech,
                utteranceId = "elapsed_notice",
                timelineJson = """{"type":"elapsed_notice","speech":${JSONObject.quote(action.speech)},"time":$nowMillis}""",
                stopReason = null,
            )
            is RealtimeClockAction.RequestExtension -> RealtimeClockActionPresentation(
                statusText = action.speech,
                speech = action.speech,
                utteranceId = "extension_request",
                timelineJson = """{"type":"extension_requested","speech":${JSONObject.quote(action.speech)},"time":$nowMillis}""",
                stopReason = null,
            )
            is RealtimeClockAction.Extended -> RealtimeClockActionPresentation(
                statusText = action.speech,
                speech = action.speech,
                utteranceId = "extension_accepted",
                timelineJson = """{"type":"extension_accepted","time":$nowMillis}""",
                stopReason = null,
            )
            is RealtimeClockAction.Stop -> RealtimeClockActionPresentation(
                statusText = null,
                speech = null,
                utteranceId = null,
                timelineJson = null,
                stopReason = action.reason,
            )
        }
    }
}
