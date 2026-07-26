package com.yujif.thinklet.realtimecompanion.session

import org.json.JSONObject

internal object SessionStopRecorder {
    fun recordStopped(
        recorder: SessionRecorder?,
        reason: String,
        nowMillis: Long,
    ) {
        recorder?.appendTimeline(
            """{"type":"stopped","reason":${JSONObject.quote(reason)},"time":$nowMillis}""",
        )
    }
}
