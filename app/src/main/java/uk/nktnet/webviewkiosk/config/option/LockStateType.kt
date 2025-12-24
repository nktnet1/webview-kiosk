package com.nktnet.webview_kiosk.config.option

import android.app.ActivityManager

enum class LockStateType {
    NONE,
    LOCK_TASK,
    SCREEN_PINNING,
    UNKNOWN;

    companion object {
        fun fromActivityManager(am: ActivityManager): LockStateType {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                when (am.lockTaskModeState) {
                    ActivityManager.LOCK_TASK_MODE_NONE -> NONE
                    ActivityManager.LOCK_TASK_MODE_LOCKED -> LOCK_TASK
                    ActivityManager.LOCK_TASK_MODE_PINNED -> SCREEN_PINNING
                    else -> UNKNOWN
                }
            } else {
                UNKNOWN
            }
        }
    }
}
