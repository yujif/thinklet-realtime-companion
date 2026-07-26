package com.yujif.thinklet.realtimecompanion.session

sealed class RealtimeClockAction {
    data class AnnounceElapsed(val speech: String) : RealtimeClockAction()
    data class RequestExtension(val speech: String) : RealtimeClockAction()
    data class Extended(val speech: String) : RealtimeClockAction()
    data class Stop(val reason: String) : RealtimeClockAction()
}

class RealtimeSessionClock(
    val startedAtMillis: Long,
    val noticeMillis: Long = 10 * 60 * 1_000L,
    val extensionIntervalMillis: Long = 30 * 60 * 1_000L,
    val extensionTimeoutMillis: Long = 30 * 1_000L,
    val maxSessionDurationMillis: Long = 60 * 60 * 1_000L,
) {
    private var noticeAnnounced = false
    private var nextExtensionPromptElapsedMillis = extensionIntervalMillis
    private var extensionPromptedAtMillis: Long? = null
    private var timeoutFired = false

    val isAwaitingExtension: Boolean
        get() = extensionPromptedAtMillis != null && !timeoutFired

    fun actionAt(nowMillis: Long): RealtimeClockAction? {
        if (timeoutFired) return null
        val elapsedMillis = nowMillis - startedAtMillis
        if (elapsedMillis >= maxSessionDurationMillis) {
            timeoutFired = true
            extensionPromptedAtMillis = null
            return RealtimeClockAction.Stop("session_limit")
        }
        extensionPromptedAtMillis?.let { promptedAt ->
            if (!timeoutFired && nowMillis - promptedAt >= extensionTimeoutMillis) {
                timeoutFired = true
                return RealtimeClockAction.Stop("extension_timeout")
            }
            return null
        }
        if (elapsedMillis >= nextExtensionPromptElapsedMillis) {
            noticeAnnounced = true
            extensionPromptedAtMillis = nowMillis
            return RealtimeClockAction.RequestExtension(
                "${elapsedMinutes(elapsedMillis)}分経過しました。延長するには中央ボタンを押してください",
            )
        }
        if (!noticeAnnounced && elapsedMillis >= noticeMillis) {
            noticeAnnounced = true
            return RealtimeClockAction.AnnounceElapsed("${elapsedMinutes(elapsedMillis)}分経過しました")
        }
        return null
    }

    fun extend(nowMillis: Long): RealtimeClockAction? {
        val promptedAt = extensionPromptedAtMillis ?: return null
        if (nowMillis - startedAtMillis >= maxSessionDurationMillis) {
            timeoutFired = true
            extensionPromptedAtMillis = null
            return RealtimeClockAction.Stop("session_limit")
        }
        if (nowMillis - promptedAt >= extensionTimeoutMillis) {
            timeoutFired = true
            extensionPromptedAtMillis = null
            return RealtimeClockAction.Stop("extension_timeout")
        }
        extensionPromptedAtMillis = null
        timeoutFired = false
        val elapsedMillis = nowMillis - startedAtMillis
        while (nextExtensionPromptElapsedMillis <= elapsedMillis) {
            nextExtensionPromptElapsedMillis += extensionIntervalMillis
        }
        return RealtimeClockAction.Extended("延長しました")
    }

    private fun elapsedMinutes(elapsedMillis: Long): Long {
        return (elapsedMillis / 60_000L).coerceAtLeast(0L)
    }
}
