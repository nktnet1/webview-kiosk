package uk.nktnet.webviewkiosk.managers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Collections

object NetworkManager {
    fun isOnline(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.applicationContext.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val activeNetworks = Collections.synchronizedSet(mutableSetOf<Network>())

        val checkOnline = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val active = connectivityManager.activeNetwork
                active != null && connectivityManager.getNetworkCapabilities(active)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } else {
                @Suppress("DEPRECATION")
                connectivityManager.activeNetworkInfo?.isConnected == true
            }
        }

        trySend(checkOnline())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activeNetworks.add(network)
                if (activeNetworks.size == 1) {
                    trySend(true)
                }
            }

            override fun onLost(network: Network) {
                activeNetworks.remove(network)
                if (activeNetworks.isEmpty()) {
                    trySend(false)
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(
            request,
            callback
        )

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
