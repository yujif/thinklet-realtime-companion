package com.yujif.thinklet.realtimecompanion.session

internal object SessionStartRecorder {
    fun recordStartRequested(
        recorder: SessionRecorder?,
        manual: Boolean,
        nowMillis: Long,
    ) {
        recorder?.appendTimeline(
            """{"type":"start_requested","manual":$manual,"time":$nowMillis}""",
        )
    }
}
