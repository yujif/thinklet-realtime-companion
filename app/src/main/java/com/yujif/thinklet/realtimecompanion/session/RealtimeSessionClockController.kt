package com.yujif.thinklet.realtimecompanion.session

import android.os.Handler

interface RealtimeClockScheduler {
    fun postDelayed(runnable: Runnable, delayMillis: Long)
    fun removeCallbacks(runnable: Runnable)
}

class HandlerRealtimeClockScheduler(
    private val handler: Handler,
) : RealtimeClockScheduler {
    override fun postDelayed(runnable: Runnable, delayMillis: Long) {
        handler.postDelayed(runnable, delayMillis)
    }

    override fun removeCallbacks(runnable: Runnable) {
        handler.removeCallbacks(runnable)
    }
}

class RealtimeSessionClockController(
    private val scheduler: RealtimeClockScheduler,
    private val nowMillis: () -> Long,
    private val tickMillis: Long = 1_000L,
    private val noticeMillis: Long = 10 * 60 * 1_000L,
    private val extensionIntervalMillis: Long = 30 * 60 * 1_000L,
    private val extensionTimeoutMillis: Long = 30 * 1_000L,
    private val onAction: (RealtimeClockAction) -> Unit,
) {
    private var clock: RealtimeSessionClock? = null

    private val tick = object : Runnable {
        override fun run() {
            clock?.actionAt(nowMillis())?.let(onAction)
            if (clock != null) {
                scheduler.postDelayed(this, tickMillis)
            }
        }
    }

    val isAwaitingExtension: Boolean
        get() = clock?.isAwaitingExtension == true

    fun start() {
        stop()
        clock = RealtimeSessionClock(
            startedAtMillis = nowMillis(),
            noticeMillis = noticeMillis,
            extensionIntervalMillis = extensionIntervalMillis,
            extensionTimeoutMillis = extensionTimeoutMillis,
        )
        scheduler.postDelayed(tick, tickMillis)
    }

    fun stop() {
        if (clock != null) {
            scheduler.removeCallbacks(tick)
        }
        clock = null
    }

    fun extend() {
        clock?.extend(nowMillis())?.let(onAction)
    }
}
