package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.ClientCertRequest
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.PrivateKey
import java.security.cert.X509Certificate
import android.security.KeyChain

/**
 * Manages client certificate selection and persistence for WebView mTLS.
 *
 * Usage:
 *   val clientCertManager = ClientCertManager(context, activity)
 *   // inside WebViewClient.onReceivedClientCertRequest:
 *   clientCertManager.handleClientCertRequest(view, request)
 *
 * Notes:
 * - onReceivedClientCertRequest is available API 21+ (Lollipop). Guard calls on lower API levels.
 * - By default this will try stored alias first; if it fails, it shows the system chooser.
 */
class ClientCertManager(
    private val context: Context,
    private val activity: Activity,
    private val prefsName: String = "client_cert_prefs",
    private val aliasKey: String = "client_cert_alias",
    private val useEncryptedPrefs: Boolean = true // set false if androidx.security not available
) {
    private val TAG = "ClientCertManager"

    private val prefs by lazy {
        if (useEncryptedPrefs && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    prefsName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create EncryptedSharedPreferences, falling back to regular prefs", e)
                context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            }
        } else {
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        }
    }

    private fun getStoredAlias(): String? = prefs.getString(aliasKey, null)

    private fun storeAlias(alias: String?) {
        prefs.edit().putString(aliasKey, alias).apply()
    }

    fun clearStoredAlias() {
        prefs.edit().remove(aliasKey).apply()
    }

    /**
     * Entry point: attempt to satisfy the ClientCertRequest.
     * If there's a stored alias, try to use it; otherwise show chooser.
     */
    fun handleClientCertRequest(view: WebView?, request: ClientCertRequest?) {
        if (request == null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // Callback not available pre-Lollipop; cancel to be explicit
            request.cancel()
            return
        }

        val storedAlias = getStoredAlias()
        if (!storedAlias.isNullOrBlank()) {
            tryUseAlias(storedAlias, request) { success ->
                if (!success) {
                    // alias failed -> fall back to chooser
                    showChooser(request)
                }
            }
        } else {
            showChooser(request)
        }
    }

    /**
     * Try to get private key and chain for alias and call proceed/cancel accordingly.
     * Callback invoked with success boolean.
     */
    private fun tryUseAlias(alias: String, request: ClientCertRequest, callback: (Boolean) -> Unit) {
        try {
            val privateKey: PrivateKey? = KeyChain.getPrivateKey(context, alias)
            val certChain: Array<X509Certificate>? = KeyChain.getCertificateChain(context, alias)
            if (privateKey != null && certChain != null && certChain.isNotEmpty()) {
                request.proceed(privateKey, certChain)
                callback(true)
                return
            } else {
                Log.w(TAG, "Got null key/chain for alias=$alias")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error while getting key/chain for alias=$alias", e)
        }
        callback(false)
    }

    /**
     * Show system certificate chooser. Stores the chosen alias for future attempts.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun showChooser(request: ClientCertRequest) {
        // KeyChain.choosePrivateKeyAlias signature:
        // choosePrivateKeyAlias(Activity activity, KeyChainAliasCallback callback,
        //                      String[] keyTypes, Principal[] issuers, String host, int port, String alias)
        try {
            KeyChain.choosePrivateKeyAlias(
                activity,
                { chosenAlias ->
                    if (chosenAlias == null) {
                        // user cancelled or no alias available
                        try {
                            request.cancel()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed cancelling ClientCertRequest after chooser cancel", e)
                        }
                        return@choosePrivateKeyAlias
                    }
                    // Try to use chosen alias
                    try {
                        val privateKey = KeyChain.getPrivateKey(context, chosenAlias)
                        val certChain = KeyChain.getCertificateChain(context, chosenAlias)
                        if (privateKey != null && certChain != null && certChain.isNotEmpty()) {
                            // Persist alias for next time (note: access can be revoked by user)
                            storeAlias(chosenAlias)
                            request.proceed(privateKey, certChain)
                        } else {
                            request.cancel()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error retrieving selected cert for alias=$chosenAlias", e)
                        try {
                            request.cancel()
                        } catch (ex: Exception) {
                            Log.w(TAG, "Failed to cancel ClientCertRequest after error", ex)
                        }
                    }
                },
                null, // key types (null = any)
                null, // issuers (null = any)
                request.host,
                request.port,
                null // alias hint
            )
        } catch (e: Exception) {
            Log.w(TAG, "choosePrivateKeyAlias failed", e)
            try {
                request.cancel()
            } catch (ex: Exception) {
                Log.w(TAG, "Failed to cancel ClientCertRequest on chooser failure", ex)
            }
        }
    }
}
