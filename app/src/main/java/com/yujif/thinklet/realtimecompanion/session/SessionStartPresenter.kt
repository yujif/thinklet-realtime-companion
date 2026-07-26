package com.yujif.thinklet.realtimecompanion.session

import com.yujif.thinklet.realtimecompanion.core.speechForSessionStart

data class SessionStartPresentation(
    val statusText: String,
    val speech: String,
    val utteranceId: String,
)

object SessionStartPresenter {
    fun presentMissingApiKey(): SessionStartPresentation {
        return SessionStartPresentation(
            statusText = "APIキーを入力してください",
            speech = "APIキーを入力してください",
            utteranceId = "missing_api_key",
        )
    }

    fun presentNotEnoughStorage(): SessionStartPresentation {
        return SessionStartPresentation(
            statusText = "空き容量が足りません",
            speech = "空き容量が足りません",
            utteranceId = "storage_low",
        )
    }

    fun presentStarting(): SessionStartPresentation {
        return SessionStartPresentation(
            statusText = "リアルタイム相談を開始します",
            speech = speechForSessionStart(),
            utteranceId = "starting",
        )
    }
}
