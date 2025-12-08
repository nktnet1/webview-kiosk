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
