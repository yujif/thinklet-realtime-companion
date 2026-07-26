package com.yujif.thinklet.realtimecompanion.session

import com.yujif.thinklet.realtimecompanion.core.speechForStopReason

data class SessionStopPresentation(
    val statusText: String,
    val speech: String?,
    val utteranceId: String?,
)

object SessionStopPresenter {
    fun presentStopped(reason: String): SessionStopPresentation {
        val speech = speechForStopReason(reason)
        return SessionStopPresentation(
            statusText = "停止しました",
            speech = speech,
            utteranceId = speech?.let { "stopped_$reason" },
        )
    }
}
