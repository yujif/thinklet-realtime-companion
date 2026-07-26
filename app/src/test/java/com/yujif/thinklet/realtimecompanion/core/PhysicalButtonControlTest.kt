package com.yujif.thinklet.realtimecompanion.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalButtonControlTest {
    @Test
    fun cameraKeyShortReleaseStartsWhenSessionIsInactive() {
        assertEquals(
            PhysicalButtonCommand.Start,
            physicalButtonCommand(
                keyCode = CAMERA_KEY,
                action = KEY_UP,
                repeatCount = 0,
                isLongPress = false,
                isSessionActive = false,
            ),
        )
    }

    @Test
    fun cameraKeyShortReleaseStopsWhenSessionIsActive() {
        assertEquals(
            PhysicalButtonCommand.Stop,
            physicalButtonCommand(
                keyCode = CAMERA_KEY,
                action = KEY_UP,
                repeatCount = 0,
                isLongPress = false,
                isSessionActive = true,
                isAwaitingExtension = false,
            ),
        )
    }

    @Test
    fun cameraKeyShortReleaseDefaultsToStopWhenAwaitingExtensionNotProvided() {
        assertEquals(
            PhysicalButtonCommand.Stop,
            physicalButtonCommand(
                keyCode = CAMERA_KEY,
                action = KEY_UP,
                repeatCount = 0,
                isLongPress = false,
                isSessionActive = true,
            ),
        )
    }

    @Test
    fun cameraKeyShortReleaseExtendsWhenExtensionPromptIsActive() {
        assertEquals(
            PhysicalButtonCommand.Extend,
            physicalButtonCommand(
                keyCode = CAMERA_KEY,
                action = KEY_UP,
                repeatCount = 0,
                isLongPress = false,
                isSessionActive = true,
                isAwaitingExtension = true,
            ),
        )
    }

    @Test
    fun cameraKeyLongPressReleaseIsIgnoredAsLauncherLaunchGesture() {
        assertEquals(
            PhysicalButtonCommand.Ignore,
            physicalButtonCommand(
                keyCode = CAMERA_KEY,
                action = KEY_UP,
                repeatCount = 0,
                isLongPress = true,
                isSessionActive = false,
            ),
        )
        assertEquals(
            PhysicalButtonCommand.Ignore,
            physicalButtonCommand(
                keyCode = CAMERA_KEY,
                action = KEY_UP,
                repeatCount = 0,
                isLongPress = true,
                isSessionActive = true,
            ),
        )
    }

    @Test
    fun ignoresCameraKeyDownRepeatsAndOtherKeys() {
        assertNull(physicalButtonCommand(CAMERA_KEY, KEY_DOWN, repeatCount = 0, isLongPress = false, isSessionActive = false))
        assertNull(physicalButtonCommand(CAMERA_KEY, KEY_UP, repeatCount = 1, isLongPress = false, isSessionActive = false))
        assertNull(physicalButtonCommand(VOLUME_UP_KEY, KEY_DOWN, repeatCount = 1, isLongPress = true, isSessionActive = false))
        assertNull(physicalButtonCommand(82, KEY_UP, repeatCount = 0, isLongPress = false, isSessionActive = false))
    }

    @Test
    fun sideButtonPressesSelectPreviousAndNextUseCaseWhenSessionIsInactive() {
        assertEquals(
            PhysicalButtonCommand.PreviousUseCase,
            physicalButtonCommand(
                keyCode = VOLUME_DOWN_KEY,
                action = KEY_DOWN,
                repeatCount = 0,
                isLongPress = false,
                isSessionActive = false,
            ),
        )
        assertEquals(
            PhysicalButtonCommand.NextUseCase,
            physicalButtonCommand(
                keyCode = VOLUME_UP_KEY,
                action = KEY_DOWN,
                repeatCount = 0,
                isLongPress = false,
                isSessionActive = false,
            ),
        )
    }

    @Test
    fun sideButtonReleasesAreSwallowedWhenSessionIsInactive() {
        assertEquals(
            PhysicalButtonCommand.Ignore,
            physicalButtonCommand(
                keyCode = VOLUME_DOWN_KEY,
                action = KEY_UP,
                repeatCount = 0,
                isLongPress = false,
                isSessionActive = false,
            ),
        )
        assertEquals(
            PhysicalButtonCommand.Ignore,
            physicalButtonCommand(
                keyCode = VOLUME_UP_KEY,
                action = KEY_UP,
                repeatCount = 0,
                isLongPress = false,
                isSessionActive = false,
            ),
        )
    }

    @Test
    fun sideButtonsAreLeftToSystemVolumeWhileSessionIsActive() {
        assertNull(
            physicalButtonCommand(
                keyCode = VOLUME_DOWN_KEY,
                action = KEY_DOWN,
                repeatCount = 0,
                isLongPress = false,
                isSessionActive = true,
            ),
        )
        assertNull(
            physicalButtonCommand(
                keyCode = VOLUME_UP_KEY,
                action = KEY_UP,
                repeatCount = 0,
                isLongPress = false,
                isSessionActive = true,
            ),
        )
    }

    @Test
    fun speaksForStartStopAndExtensionTimeout() {
        assertEquals("リアルタイム相談を開始します", speechForSessionStart())
        assertEquals("停止しました", speechForStopReason("physical_button"))
        assertEquals("延長されなかったので停止しました", speechForStopReason("extension_timeout"))
        assertEquals("上限に近づいています。停止します", speechForStopReason("budget_guard"))
        assertEquals("保存容量の上限に達したため停止しました", speechForStopReason("storage_limit"))
        assertNull(speechForStopReason("activity_destroy"))
    }

    private companion object {
        const val CAMERA_KEY = 27
        const val VOLUME_UP_KEY = 24
        const val VOLUME_DOWN_KEY = 25
        const val KEY_DOWN = 0
        const val KEY_UP = 1
    }
}
