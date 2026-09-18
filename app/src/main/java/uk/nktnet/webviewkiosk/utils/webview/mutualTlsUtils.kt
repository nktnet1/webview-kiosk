package uk.nktnet.webviewkiosk.utils.webview

import android.app.Activity
import android.content.Context
import android.security.KeyChain
import android.webkit.ClientCertRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.config.SystemSettings
import java.net.URI
import java.util.Locale

private const val DEFAULT_HTTPS_PORT = 443

/**
 * A single exact site that is allowed to use a client certificate.
 *
 * [alias] is optional. When omitted, the device-local alias saved in [SystemSettings]
 * is used, or Android's KeyChain picker is shown on the first certificate request.
 */
data class MutualTlsRule(
    val host: String,
    val port: Int,
    val alias: String?,
) {
    val siteKey: String = mutualTlsSiteKey(host, port)
}

fun mutualTlsSiteKey(host: String, port: Int): String {
    val normalizedHost = host
        .trim()
        .removePrefix("[")
        .removeSuffix("]")
        .trimEnd('.')
        .lowercase(Locale.ROOT)
    val displayHost = if (':' in normalizedHost) "[$normalizedHost]" else normalizedHost
    val normalizedPort = if (port == -1) DEFAULT_HTTPS_PORT else port
    return "$displayHost:$normalizedPort"
}

fun parseMutualTlsRules(value: String): List<MutualTlsRule>? {
    val rules = mutableListOf<MutualTlsRule>()
    val seenSites = mutableSetOf<String>()

    for (rawLine in value.lines()) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue

        val (sitePart, aliasPart) = line.split('=', limit = 2).let {
            it.first().trim() to it.getOrNull(1)?.trim()?.takeIf(String::isNotEmpty)
        }
        if (sitePart.isEmpty() || "://" in sitePart) return null

        val uri = try {
            URI("https://$sitePart")
        } catch (_: Exception) {
            return null
        }
        if (
            uri.host.isNullOrBlank()
            || uri.rawUserInfo != null
            || !uri.rawPath.isNullOrEmpty()
            || uri.rawQuery != null
            || uri.rawFragment != null
        ) {
            return null
        }

        val port = if (uri.port == -1) DEFAULT_HTTPS_PORT else uri.port
        if (port !in 1..65535) return null

        val rule = MutualTlsRule(
            host = uri.host,
            port = port,
            alias = aliasPart,
        )
        if (!seenSites.add(rule.siteKey)) return null
        rules.add(rule)
    }

    return rules
}

fun validateMutualTls(value: String): Boolean {
    return parseMutualTlsRules(value) != null
}

fun handleMutualTlsRequest(
    activity: Activity?,
    context: Context,
    request: ClientCertRequest,
    siteRules: List<MutualTlsRule>,
    systemSettings: SystemSettings,
    scope: CoroutineScope,
) {
    val siteKey = mutualTlsSiteKey(request.host, request.port)
    val rule = siteRules.firstOrNull { it.siteKey == siteKey }
    if (rule == null || activity == null) {
        // Do not cache a negative response so a later settings change can take effect.
        request.ignore()
        return
    }

    lateinit var chooseAlias: (String?, String?) -> Unit

    val loadAlias: (String, Boolean) -> Unit = { alias, promptOnFailure ->
        scope.launch {
            val credentials = withContext(Dispatchers.IO) {
                runCatching {
                    val privateKey = KeyChain.getPrivateKey(context, alias)
                    val certificateChain = KeyChain.getCertificateChain(context, alias)
                    if (privateKey == null || certificateChain.isNullOrEmpty()) {
                        null
                    } else {
                        privateKey to certificateChain
                    }
                }.getOrNull()
            }

            if (credentials != null) {
                if (rule.alias == null) {
                    systemSettings.setMutualTlsAlias(siteKey, alias)
                }
                request.proceed(credentials.first, credentials.second)
            } else {
                systemSettings.removeMutualTlsAlias(siteKey)
                if (promptOnFailure) {
                    chooseAlias(alias, rule.alias)
                } else {
                    request.ignore()
                }
            }
        }
    }

    chooseAlias = { preselectedAlias, requiredAlias ->
        runCatching {
            KeyChain.choosePrivateKeyAlias(
                activity,
                { alias ->
                    scope.launch {
                        if (
                            alias.isNullOrBlank()
                            || alias == KeyChain.KEY_ALIAS_SELECTION_DENIED
                            || (requiredAlias != null && alias != requiredAlias)
                        ) {
                            systemSettings.removeMutualTlsAlias(siteKey)
                            request.ignore()
                        } else {
                            loadAlias(alias, false)
                        }
                    }
                },
                request.keyTypes,
                request.principals,
                request.host,
                request.port,
                preselectedAlias,
            )
        }.onFailure {
            request.ignore()
        }
    }

    val configuredAlias = rule.alias
    val rememberedAlias = systemSettings.getMutualTlsAlias(siteKey)
    when {
        configuredAlias != null -> loadAlias(configuredAlias, true)
        rememberedAlias != null -> loadAlias(rememberedAlias, true)
        else -> chooseAlias(null, null)
    }
}
