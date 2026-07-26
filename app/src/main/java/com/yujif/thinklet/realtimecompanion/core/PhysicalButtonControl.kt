package com.yujif.thinklet.realtimecompanion.core

enum class PhysicalButtonCommand {
    Start,
    Stop,
    Extend,
    PreviousUseCase,
    NextUseCase,
    Ignore,
}

fun physicalButtonCommand(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    isLongPress: Boolean,
    isSessionActive: Boolean,
    isAwaitingExtension: Boolean = false,
    cameraKeyCode: Int = 27,
    volumeUpKeyCode: Int = 24,
    volumeDownKeyCode: Int = 25,
    keyDownAction: Int = 0,
    keyUpAction: Int = 1,
): PhysicalButtonCommand? {
    if (repeatCount > 0) {
        return null
    }
    return when (keyCode) {
        cameraKeyCode -> when {
            action != keyUpAction -> null
            // A long center press is the THINKLET Launcher's app-launch gesture. Its release
            // is also delivered to the foreground app, so treating it as a short press would
            // start a session right after launching the app.
            isLongPress -> PhysicalButtonCommand.Ignore
            isSessionActive && isAwaitingExtension -> PhysicalButtonCommand.Extend
            isSessionActive -> PhysicalButtonCommand.Stop
            else -> PhysicalButtonCommand.Start
        }
        volumeDownKeyCode, volumeUpKeyCode -> when {
            // Mid-session, leave the volume keys to the system so they adjust playback volume.
            isSessionActive -> null
            // Fire on key down like the official THINKLET samples; volume key up events are
            // not reliably delivered to the app on the device.
            action == keyDownAction && keyCode == volumeDownKeyCode -> PhysicalButtonCommand.PreviousUseCase
            action == keyDownAction && keyCode == volumeUpKeyCode -> PhysicalButtonCommand.NextUseCase
            // Swallow the paired key up when it does arrive so the system does not also react.
            else -> PhysicalButtonCommand.Ignore
        }
        else -> null
    }
}

fun speechForSessionStart(): String = "リアルタイム相談を開始します"

fun speechForStopReason(reason: String): String? {
    return when (reason) {
        "manual_stop", "physical_button" -> "停止しました"
        "extension_timeout" -> "延長されなかったので停止しました"
        "budget_guard" -> "上限に近づいています。停止します"
        "storage_limit" -> "保存容量の上限に達したため停止しました"
        else -> null
    }
}
