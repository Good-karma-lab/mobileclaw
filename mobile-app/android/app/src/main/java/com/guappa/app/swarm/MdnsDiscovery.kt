package com.guappa.app.swarm

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * MdnsDiscovery — discovers swarm connectors on the local network via mDNS/DNS-SD.
 *
 * Looks for services advertised as `_guappa-swarm._tcp` and reports their
 * host:port so the SwarmManager can auto-connect without manual URL entry.
 */
class MdnsDiscovery(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryActive = false

    data class DiscoveredConnector(
        val name: String,
        val host: String,
        val port: Int
    ) {
        val url: String get() = "http://$host:$port"
    }

    private val discovered = mutableMapOf<String, DiscoveredConnector>()

    var onConnectorFound: ((DiscoveredConnector) -> Unit)? = null
    var onConnectorLost: ((String) -> Unit)? = null

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.d(TAG, "mDNS discovery started for $serviceType")
            discoveryActive = true
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d(TAG, "mDNS discovery stopped")
            discoveryActive = false
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
            // Resolve to get host:port
            nsdManager.resolveService(serviceInfo, resolveListener)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            val name = serviceInfo.serviceName
            Log.d(TAG, "Service lost: $name")
            discovered.remove(name)
            onConnectorLost?.invoke(name)
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery start failed: error=$errorCode")
            discoveryActive = false
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery stop failed: error=$errorCode")
        }
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: error=$errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val host = serviceInfo.host?.hostAddress ?: return
            val port = serviceInfo.port
            val name = serviceInfo.serviceName
            Log.d(TAG, "Resolved: $name → $host:$port")

            val connector = DiscoveredConnector(name, host, port)
            discovered[name] = connector
            onConnectorFound?.invoke(connector)
        }
    }

    /**
     * Start discovering swarm connectors on the local network.
     */
    fun startDiscovery() {
        if (discoveryActive) return
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
        }
    }

    /**
     * Stop mDNS discovery.
     */
    fun stopDiscovery() {
        if (!discoveryActive) return
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop discovery", e)
        }
    }

    /**
     * Get all currently discovered connectors.
     */
    fun getDiscoveredConnectors(): List<DiscoveredConnector> = discovered.values.toList()

    fun isDiscovering(): Boolean = discoveryActive

    companion object {
        private const val TAG = "MdnsDiscovery"
        const val SERVICE_TYPE = "_guappa-swarm._tcp"
    }
}
