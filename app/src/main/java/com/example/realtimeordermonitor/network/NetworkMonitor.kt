// NetworkMonitor.kt
package com.example.realtimeordermonitor.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _networkType = MutableStateFlow<NetworkType>(NetworkType.NONE)
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()

    private val _connectionQuality = MutableStateFlow<ConnectionQuality>(ConnectionQuality.UNKNOWN)
    val connectionQuality: StateFlow<ConnectionQuality> = _connectionQuality.asStateFlow()

    companion object {
        private const val TAG = "NetworkMonitor"
    }

    sealed class NetworkType {
        object WIFI : NetworkType()
        object CELLULAR : NetworkType()
        object ETHERNET : NetworkType()
        object NONE : NetworkType()
    }

    enum class ConnectionQuality {
        EXCELLENT, GOOD, POOR, UNKNOWN
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available: $network")
            _isConnected.value = true
            updateNetworkInfo(network)
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost: $network")
            _isConnected.value = false
            _networkType.value = NetworkType.NONE
            _connectionQuality.value = ConnectionQuality.UNKNOWN
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            Log.d(TAG, "Network capabilities changed: $network")
            updateNetworkInfo(network, networkCapabilities)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) {
            Log.d(TAG, "Link properties changed: $network")
            updateNetworkInfo(network)
        }
    }

    init {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            Log.d(TAG, "Network callback registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }

        // Set initial state
        updateInitialNetworkState()
    }

    private fun updateInitialNetworkState() {
        val isConnected = isNetworkAvailable()
        _isConnected.value = isConnected

        if (isConnected) {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                updateNetworkInfo(activeNetwork)
            }
        } else {
            _networkType.value = NetworkType.NONE
            _connectionQuality.value = ConnectionQuality.UNKNOWN
        }

        Log.d(TAG, "Initial network state - Connected: $isConnected, Type: ${_networkType.value}")
    }

    private fun updateNetworkInfo(network: Network, capabilities: NetworkCapabilities? = null) {
        val networkCapabilities = capabilities ?: connectivityManager.getNetworkCapabilities(network)

        if (networkCapabilities != null) {
            // Update network type
            _networkType.value = when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                else -> NetworkType.NONE
            }

            // Update connection quality based on bandwidth
            _connectionQuality.value = determineConnectionQuality(networkCapabilities)

            Log.d(TAG, "Network info updated - Type: ${_networkType.value}, Quality: ${_connectionQuality.value}")
        }
    }

    private fun determineConnectionQuality(capabilities: NetworkCapabilities): ConnectionQuality {
        return try {
            val downstreamBandwidth = capabilities.linkDownstreamBandwidthKbps
            val upstreamBandwidth = capabilities.linkUpstreamBandwidthKbps

            when {
                downstreamBandwidth > 10000 && upstreamBandwidth > 5000 -> ConnectionQuality.EXCELLENT
                downstreamBandwidth > 5000 && upstreamBandwidth > 1000 -> ConnectionQuality.GOOD
                downstreamBandwidth > 1000 -> ConnectionQuality.POOR
                else -> ConnectionQuality.UNKNOWN
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine connection quality", e)
            ConnectionQuality.UNKNOWN
        }
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network availability", e)
            false
        }
    }

    fun isWifiConnected(): Boolean {
        return _networkType.value == NetworkType.WIFI && _isConnected.value
    }

    fun isCellularConnected(): Boolean {
        return _networkType.value == NetworkType.CELLULAR && _isConnected.value
    }

    fun getNetworkInfo(): NetworkInfo {
        return NetworkInfo(
            isConnected = _isConnected.value,
            networkType = _networkType.value,
            connectionQuality = _connectionQuality.value
        )
    }

    fun cleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.d(TAG, "Network callback unregistered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
    }

    data class NetworkInfo(
        val isConnected: Boolean,
        val networkType: NetworkType,
        val connectionQuality: ConnectionQuality
    )
}