package uk.nktnet.webviewkiosk.utils.webview

import android.content.Context
import android.net.http.SslCertificate
import android.net.http.SslError
import android.net.http.X509TrustManagerExtensions
import android.security.KeyChain
import android.util.Base64
import android.webkit.ClientCertRequest
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.SslErrorModeOption
import java.io.IOException
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager

/** TLS decisions belong to one PDF viewer, and never change process-wide TLS defaults. */
internal class PdfTlsState(previous: PdfTlsState? = null) {
    private data class SslApproval(val site: String, val certificates: String, val error: Int)

    private val approvals: ConcurrentHashMap<SslApproval, Boolean> = ConcurrentHashMap<SslApproval, Boolean>().apply {
        previous?.approvals?.let(::putAll)
    }
    private val sslRequests = ConcurrentHashMap<SslApproval, SslErrorRequest>()
    private val clientCertRequests = ConcurrentHashMap<String, ClientCertRequest>()
    @Volatile private var closed = false

    val hasPendingRequests: Boolean
        get() = sslRequests.isNotEmpty() || clientCertRequests.isNotEmpty()

    fun cancelPendingRequests() {
        closed = true
        sslRequests.values.toList().forEach { it.cancel() }
        clientCertRequests.values.toList().forEach { it.ignore() }
    }

    fun configureConnection(
        connection: HttpsURLConnection,
        context: Context,
        userSettings: UserSettings,
        isActive: () -> Boolean,
        onClientCertificateRequired: (ClientCertRequest) -> Unit,
        onSslError: (SslErrorRequest) -> Unit,
        onApproved: () -> Unit,
    ) {
        val url = connection.url
        val port = url.port.takeIf { it >= 0 } ?: 443
        val site = mutualTlsSiteKey(url.host, port)
        val active = { !closed && isActive() }
        if (!active()) throw IOException("PDF viewer is no longer active")

        val platformTrust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(null as KeyStore?)
        }.trustManagers.filterIsInstance<X509TrustManager>().first()
        val extendedTrust = X509TrustManagerExtensions(platformTrust)

        fun allowCertificate(chain: Array<X509Certificate>, error: Int): Boolean {
            if (!active() || chain.isEmpty()) return false
            val digest = MessageDigest.getInstance("SHA-256")
            chain.forEach { digest.update(it.encoded) }
            val approval = SslApproval(site, Base64.encodeToString(digest.digest(), Base64.NO_WRAP), error)
            return when (userSettings.sslErrorMode) {
                SslErrorModeOption.BLOCK -> false
                SslErrorModeOption.PROCEED -> true
                SslErrorModeOption.PROMPT -> {
                    if (approvals.containsKey(approval)) {
                        true
                    } else {
                        lateinit var request: SslErrorRequest
                        request = SslErrorRequest(
                            SslError(error, SslCertificate(chain.first()), url.toString()),
                            onProceed = {
                                sslRequests.remove(approval, request)
                                if (active()) {
                                    approvals[approval] = true
                                    onApproved()
                                }
                            },
                            onCancel = { sslRequests.remove(approval, request) },
                        )
                        if (sslRequests.putIfAbsent(approval, request) == null) {
                            if (active()) onSslError(request) else request.cancel()
                        }
                        false
                    }
                }
            }
        }

        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = platformTrust.acceptedIssuers
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                platformTrust.checkClientTrusted(chain, authType)

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (!active()) throw CertificateException("PDF viewer is no longer active")
                try {
                    // Keep Android's host-specific trust configuration and certificate pinning.
                    extendedTrust.checkServerTrusted(chain, authType, url.host)
                } catch (e: CertificateException) {
                    val error = when {
                        generateSequence<Throwable>(e) { it.cause }.any { it is CertificateExpiredException } ->
                            SslError.SSL_EXPIRED
                        generateSequence<Throwable>(e) { it.cause }.any { it is CertificateNotYetValidException } ->
                            SslError.SSL_NOTYETVALID
                        else -> SslError.SSL_UNTRUSTED
                    }
                    if (!allowCertificate(chain, error)) throw e
                }
            }
        }

        val rule = parseMutualTlsRules(userSettings.mutualTls)?.firstOrNull { it.siteKey == site }
        var privateKey: PrivateKey? = null
        var certificateChain: Array<X509Certificate>? = null
        val keys = object : X509KeyManager {
            override fun chooseClientAlias(
                keyTypes: Array<String>?, issuers: Array<Principal>?, socket: Socket?,
            ): String? {
                if (!active() || rule == null) return null
                // Re-read KeyChain access and the current rule for every connection, including redirects.
                if (parseMutualTlsRules(userSettings.mutualTls)?.none { it == rule } != false) return null
                val alias = rule.alias ?: SystemSettings(context).getMutualTlsAlias(site)
                val credentials = alias?.let {
                    runCatching {
                        val key = KeyChain.getPrivateKey(context, it)
                        val chain = KeyChain.getCertificateChain(context, it)
                        if (key != null && !chain.isNullOrEmpty()) key to chain else null
                    }.getOrNull()
                }
                if (credentials != null) {
                    privateKey = credentials.first
                    certificateChain = credentials.second
                    return "pdf-client"
                }

                val completed = AtomicBoolean(false)
                val request = object : ClientCertRequest() {
                    override fun getHost() = url.host
                    override fun getPort() = port
                    override fun getKeyTypes() = keyTypes?.clone()
                    override fun getPrincipals() = issuers?.clone()
                    override fun proceed(key: PrivateKey?, chain: Array<X509Certificate>?) {
                        if (completed.compareAndSet(false, true)) {
                            clientCertRequests.remove(site, this)
                            if (active() && key != null && !chain.isNullOrEmpty()) onApproved()
                        }
                    }
                    override fun ignore() {
                        if (completed.compareAndSet(false, true)) clientCertRequests.remove(site, this)
                    }
                    override fun cancel() = ignore()
                }
                if (clientCertRequests.putIfAbsent(site, request) == null) {
                    if (active()) onClientCertificateRequired(request) else request.ignore()
                }
                // Retry the PDF after the existing KeyChain picker has saved the chosen alias.
                return null
            }

            override fun getPrivateKey(alias: String?) = privateKey.takeIf { alias == "pdf-client" }
            override fun getCertificateChain(alias: String?) = certificateChain.takeIf { alias == "pdf-client" }
            override fun getClientAliases(keyType: String?, issuers: Array<Principal>?) = null
            override fun getServerAliases(keyType: String?, issuers: Array<Principal>?) = null
            override fun chooseServerAlias(keyType: String?, issuers: Array<Principal>?, socket: Socket?) = null
        }

        connection.sslSocketFactory = SSLContext.getInstance("TLS").apply {
            init(arrayOf(keys), arrayOf(trust), null)
        }.socketFactory
        val platformHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { host, session ->
            active() && (
                platformHostnameVerifier.verify(host, session)
                || allowCertificate(
                    session.peerCertificates.filterIsInstance<X509Certificate>().toTypedArray(),
                    SslError.SSL_IDMISMATCH,
                )
            )
        }
    }
}
