package com.yujif.thinklet.realtimecompanion.session

object SessionPermissionPresenter {
    fun presentPermissionResult(hasRequiredPermissions: Boolean): String {
        return if (hasRequiredPermissions) {
            "権限OK。中央ボタン短押しで開始できます。"
        } else {
            "カメラとマイク権限が必要です"
        }
    }
}
