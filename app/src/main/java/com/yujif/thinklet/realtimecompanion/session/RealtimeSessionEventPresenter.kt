package com.yujif.thinklet.realtimecompanion.session

import org.json.JSONObject

data class RealtimeSessionEventPresentation(
    val statusText: String,
    val speech: String?,
    val utteranceId: String?,
    val timelineJson: String,
)

object RealtimeSessionEventPresenter {
    fun presentConnected(nowMillis: Long): RealtimeSessionEventPresentation {
        return RealtimeSessionEventPresentation(
            statusText = "接続しました",
            speech = "接続しました",
            utteranceId = "connected",
            timelineJson = """{"type":"connected","time":$nowMillis}""",
        )
    }

    fun presentDisconnected(
        reason: String,
        nowMillis: Long,
    ): RealtimeSessionEventPresentation {
        return RealtimeSessionEventPresentation(
            statusText = "停止しました: $reason",
            speech = null,
            utteranceId = null,
            timelineJson = """{"type":"disconnected","reason":${JSONObject.quote(reason)},"time":$nowMillis}""",
        )
    }

    fun presentError(message: String): RealtimeSessionEventPresentation {
        return RealtimeSessionEventPresentation(
            statusText = "エラー: $message",
            speech = "接続に失敗しました",
            utteranceId = "connection_error",
            timelineJson = """{"type":"error","message":${JSONObject.quote(message)}}""",
        )
    }
}
