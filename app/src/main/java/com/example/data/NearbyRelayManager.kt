package com.example.data

import android.content.Context
import android.os.Build
import com.example.model.GpsTelemetryPacket
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

class NearbyRelayManager(context: Context) {

    interface Listener {
        fun onAdvertisingStarted()
        fun onDiscoveryStarted()
        fun onHandshaking(peerName: String)
        fun onConnected(endpointId: String, peerName: String)
        fun onPacketReceived(packet: GpsTelemetryPacket)
        fun onDisconnected(reason: String?)
        fun onError(message: String)
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private var connectedEndpointId: String? = null
    private var activePairingCode: String = ""
    private var activeListener: Listener? = null
    private val pendingPeerNames = mutableMapOf<String, String>()
    private var connectionRequested = false

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val packet = GpsTelemetryPacket.fromJsonBytes(bytes) ?: return
                activeListener?.onPacketReceived(packet)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val cleanPeerName = parseEndpointDeviceName(info.endpointName)
            pendingPeerNames[endpointId] = cleanPeerName
            activeListener?.onHandshaking(cleanPeerName)
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    activeListener?.onError("Connection accept failed: ${e.localizedMessage ?: "Unknown error"}")
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val peerName = pendingPeerNames.remove(endpointId) ?: "Peer Device"
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connectedEndpointId = endpointId
                    client.stopAdvertising()
                    client.stopDiscovery()
                    activeListener?.onConnected(endpointId, peerName)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    connectionRequested = false
                    activeListener?.onError("Pairing rejected by $peerName.")
                }
                else -> {
                    connectionRequested = false
                    activeListener?.onError("Connection failed (code ${result.status.statusCode}).")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (connectedEndpointId == endpointId) {
                connectedEndpointId = null
                connectionRequested = false
                activeListener?.onDisconnected("Peer disconnected.")
            }
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val parsedCode = parseEndpointCode(info.endpointName)
            if (parsedCode != null && parsedCode == activePairingCode && !connectionRequested) {
                connectionRequested = true
                val cleanPeerName = parseEndpointDeviceName(info.endpointName)
                activeListener?.onHandshaking(cleanPeerName)
                client.requestConnection(
                    localDeviceName(),
                    endpointId,
                    lifecycleCallback,
                ).addOnFailureListener { e ->
                    connectionRequested = false
                    activeListener?.onError("Pairing request failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            }
        }

        override fun onEndpointLost(endpointId: String) = Unit
    }

    fun startAdvertising(pairingCode: String, listener: Listener) {
        stopAll()
        activePairingCode = pairingCode
        activeListener = listener
        connectionRequested = false

        val endpointName = "$ENDPOINT_PREFIX$pairingCode:${localDeviceName()}"
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()

        client.startAdvertising(
            endpointName,
            SERVICE_ID,
            lifecycleCallback,
            options,
        ).addOnSuccessListener {
            listener.onAdvertisingStarted()
        }.addOnFailureListener { e ->
            listener.onError(formatNearbyError("Could not start Bluetooth/Wi-Fi advertising", e))
        }
    }

    fun startDiscovery(targetCode: String, listener: Listener) {
        stopAll()
        activePairingCode = targetCode
        activeListener = listener
        connectionRequested = false

        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()

        client.startDiscovery(
            SERVICE_ID,
            discoveryCallback,
            options,
        ).addOnSuccessListener {
            listener.onDiscoveryStarted()
        }.addOnFailureListener { e ->
            listener.onError(formatNearbyError("Could not scan nearby devices", e))
        }
    }

    fun sendTelemetry(packet: GpsTelemetryPacket): Boolean {
        val endpointId = connectedEndpointId ?: return false
        val payload = Payload.fromBytes(packet.toJsonBytes())
        client.sendPayload(endpointId, payload)
        return true
    }

    fun stopAll() {
        connectedEndpointId = null
        connectionRequested = false
        pendingPeerNames.clear()
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }
    }

    private fun localDeviceName(): String {
        val model = Build.MODEL?.trim().orEmpty()
        return if (model.isNotEmpty()) model else "Android Device"
    }

    private fun parseEndpointCode(endpointName: String): String? {
        if (!endpointName.startsWith(ENDPOINT_PREFIX)) return null
        val remainder = endpointName.removePrefix(ENDPOINT_PREFIX)
        return remainder.substringBefore(':').takeIf { it.length == 6 }
    }

    private fun parseEndpointDeviceName(endpointName: String): String {
        if (!endpointName.startsWith(ENDPOINT_PREFIX)) return endpointName
        val remainder = endpointName.removePrefix(ENDPOINT_PREFIX)
        return remainder.substringAfter(':', "Peer Device").ifBlank { "Peer Device" }
    }

    private fun formatNearbyError(prefix: String, error: Exception): String {
        val detail = error.localizedMessage ?: "Ensure Bluetooth and Location are enabled."
        return "$prefix: $detail"
    }

    companion object {
        private const val SERVICE_ID = "com.aistudio.linkgps.SERVICE"
        private const val ENDPOINT_PREFIX = "LINKGPS:"
    }
}
