package uk.nktnet.webviewkiosk.config.data

import android.content.ComponentName
import android.graphics.drawable.Drawable

open class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable
)

class AdminAppInfo(
    packageName: String,
    name: String,
    icon: Drawable,
    val admin: ComponentName
) : AppInfo(packageName, name, icon)

class LaunchableAppInfo(
    packageName: String,
    name: String,
    icon: Drawable,
    val activities: List<Activity>,
    val isLockTaskPermitted: Boolean,
) : AppInfo(packageName, name, icon) {
    data class Activity(
        val label: String,
        val name: String
    )
}

data class AppLoadState<T : AppInfo>(
    val apps: List<T>,
    val progress: Float
)

enum class AppType(val label: String) {
    USER_APPS("User apps"),
    SYSTEM_APPS("System apps"),
    ALL_APPS("All apps")
}
