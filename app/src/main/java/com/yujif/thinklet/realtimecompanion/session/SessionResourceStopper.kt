package com.yujif.thinklet.realtimecompanion.session

object SessionResourceStopper {
    fun stopActiveResources(
        reason: String,
        disconnectRealtime: (String) -> Unit,
        stopMicrophone: () -> Unit,
        stopAssistantAudio: () -> Unit,
        stopCamera: () -> Unit,
    ) {
        disconnectRealtime(reason)
        stopMicrophone()
        stopAssistantAudio()
        stopCamera()
    }
}
