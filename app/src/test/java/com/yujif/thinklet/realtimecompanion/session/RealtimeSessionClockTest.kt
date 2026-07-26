package com.yujif.thinklet.realtimecompanion.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

class RealtimeSessionClockTest {
    @Test
    fun announcesRoughElapsedTimeAtTenMinutesWithoutStopping() {
        val clock = RealtimeSessionClock(startedAtMillis = 0L)

        assertEquals(
            RealtimeClockAction.AnnounceElapsed("10分経過しました"),
            clock.actionAt(nowMillis = 10 * 60 * 1_000L),
        )
        assertNull(clock.actionAt(nowMillis = 10 * 60 * 1_000L + 1_000L))
    }

    @Test
    fun asksForExtensionAtThirtyMinutes() {
        val clock = RealtimeSessionClock(startedAtMillis = 0L)

        assertEquals(
            RealtimeClockAction.RequestExtension("30分経過しました。延長するには中央ボタンを押してください"),
            clock.actionAt(nowMillis = 30 * 60 * 1_000L),
        )
    }

    @Test
    fun asksForExtensionOnFirstThirtyMinuteTick() {
        val clock = RealtimeSessionClock(startedAtMillis = 0L)

        assertEquals(
            RealtimeClockAction.RequestExtension("30分経過しました。延長するには中央ボタンを押してください"),
            clock.actionAt(nowMillis = 30 * 60 * 1_000L),
        )
        assertNull(clock.actionAt(nowMillis = 30 * 60 * 1_000L + 1_000L))
    }

    @Test
    fun stopsWhenExtensionPromptTimesOut() {
        val clock = RealtimeSessionClock(startedAtMillis = 0L)
        clock.actionAt(nowMillis = 30 * 60 * 1_000L)

        assertNull(clock.actionAt(nowMillis = 30 * 60 * 1_000L + 29_000L))
        assertEquals(
            RealtimeClockAction.Stop("extension_timeout"),
            clock.actionAt(nowMillis = 30 * 60 * 1_000L + 30_000L),
        )
    }

    @Test
    fun centralButtonExtensionCancelsTimeoutAndStopsAtTheSixtyMinuteProviderLimit() {
        val clock = RealtimeSessionClock(startedAtMillis = 0L)
        clock.actionAt(nowMillis = 30 * 60 * 1_000L)

        assertEquals(
            RealtimeClockAction.Extended("延長しました"),
            clock.extend(nowMillis = 30 * 60 * 1_000L + 10_000L),
        )
        assertNull(clock.actionAt(nowMillis = 30 * 60 * 1_000L + 30_000L))
        assertEquals(
            RealtimeClockAction.Stop("session_limit"),
            clock.actionAt(nowMillis = 60 * 60 * 1_000L),
        )
    }

    @Test
    fun extendAfterExtensionWindowDoesNotExtendAndStopsAwaiting() {
        val clock = RealtimeSessionClock(startedAtMillis = 0L)
        clock.actionAt(nowMillis = 30 * 60 * 1_000L)

        assertEquals(
            RealtimeClockAction.Stop("extension_timeout"),
            clock.extend(nowMillis = 30 * 60 * 1_000L + 31_000L),
        )
        assertNull(clock.actionAt(nowMillis = 30 * 60 * 1_000L + 31_000L))
        assertFalse(clock.isAwaitingExtension)
    }

    @Test
    fun controllerStartsClockAndSchedulesTickThroughScheduler() {
        val scheduler = FakeRealtimeClockScheduler()
        var now = 1_000L
        val actions = mutableListOf<RealtimeClockAction>()
        val controller = RealtimeSessionClockController(
            scheduler = scheduler,
            nowMillis = { now },
            tickMillis = 250L,
            onAction = { actions.add(it) },
        )

        controller.start()

        assertEquals(1, scheduler.scheduled.size)
        assertEquals(250L, scheduler.scheduled.single().delayMillis)

        now = 10 * 60 * 1_000L + 1_000L
        scheduler.scheduled.single().runnable.run()

        assertEquals(listOf(RealtimeClockAction.AnnounceElapsed("10分経過しました")), actions)
        assertEquals(2, scheduler.scheduled.size)
    }

    @Test
    fun controllerStopsClockAndCancelsScheduledTicks() {
        val scheduler = FakeRealtimeClockScheduler()
        val controller = RealtimeSessionClockController(
            scheduler = scheduler,
            nowMillis = { 0L },
            onAction = {},
        )

        controller.start()
        val scheduledTick = scheduler.scheduled.single().runnable
        controller.stop()

        assertSame(scheduledTick, scheduler.cancelled.single())
        assertFalse(controller.isAwaitingExtension)
    }

    @Test
    fun clockActionPresenterReturnsStatusSpeechAndTimelineForExtensionFlow() {
        assertEquals(
            RealtimeClockActionPresentation(
                statusText = "30分経過しました。延長するには中央ボタンを押してください",
                speech = "30分経過しました。延長するには中央ボタンを押してください",
                utteranceId = "extension_request",
                timelineJson = """{"type":"extension_requested","speech":"30分経過しました。延長するには中央ボタンを押してください","time":1234}""",
                stopReason = null,
            ),
            RealtimeSessionClockActionPresenter.present(
                RealtimeClockAction.RequestExtension("30分経過しました。延長するには中央ボタンを押してください"),
                nowMillis = 1234L,
            ),
        )

        assertEquals(
            RealtimeClockActionPresentation(
                statusText = "延長しました",
                speech = "延長しました",
                utteranceId = "extension_accepted",
                timelineJson = """{"type":"extension_accepted","time":5678}""",
                stopReason = null,
            ),
            RealtimeSessionClockActionPresenter.present(
                RealtimeClockAction.Extended("延長しました"),
                nowMillis = 5678L,
            ),
        )
    }

    @Test
    fun clockActionPresenterReturnsStopReasonWithoutPresentationSideEffects() {
        assertEquals(
            RealtimeClockActionPresentation(
                statusText = null,
                speech = null,
                utteranceId = null,
                timelineJson = null,
                stopReason = "extension_timeout",
            ),
            RealtimeSessionClockActionPresenter.present(
                RealtimeClockAction.Stop("extension_timeout"),
                nowMillis = 1234L,
            ),
        )
    }

    private class FakeRealtimeClockScheduler : RealtimeClockScheduler {
        data class Scheduled(val runnable: Runnable, val delayMillis: Long)

        val scheduled = mutableListOf<Scheduled>()
        val cancelled = mutableListOf<Runnable>()

        override fun postDelayed(runnable: Runnable, delayMillis: Long) {
            scheduled.add(Scheduled(runnable, delayMillis))
        }

        override fun removeCallbacks(runnable: Runnable) {
            cancelled.add(runnable)
        }
    }
}
