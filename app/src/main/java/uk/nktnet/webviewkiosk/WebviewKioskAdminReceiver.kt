package uk.nktnet.webviewkiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.utils.setupLockTaskPackage
import uk.nktnet.webviewkiosk.utils.webview.mutualTlsSiteKey
import uk.nktnet.webviewkiosk.utils.webview.parseMutualTlsRules

class WebviewKioskAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        setupLockTaskPackage(context)
        super.onEnabled(context, intent)
    }

    override fun onChoosePrivateKeyAlias(
        context: Context,
        intent: Intent,
        uid: Int,
        uri: Uri?,
        alias: String?
    ): String? {
        if (uid != context.applicationInfo.uid || alias.isNullOrBlank()) {
            return null
        }
        val host = uri?.host ?: return null
        val port = uri.port.takeIf { it != -1 } ?: 443
        val siteKey = mutualTlsSiteKey(host, port)
        val configuredAlias = parseMutualTlsRules(
            UserSettings(context).mutualTls
        )
            ?.firstOrNull { it.siteKey == siteKey }
            ?.alias

        return alias.takeIf { it == configuredAlias }
    }
}
