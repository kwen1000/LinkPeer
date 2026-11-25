package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.LocationSourceManager
import com.example.data.MockLocationInjector
import com.example.data.NearbyRelayManager
import com.example.model.ConnectionState
import com.example.model.GpsTelemetryPacket
import com.example.model.RelayRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Locale

class GpsRelayForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var nearbyManager: NearbyRelayManager
    private lateinit var locationSourceManager: LocationSourceManager
    private lateinit var mockLocationInjector: MockLocationInjector
    private lateinit var notificationManager: NotificationManager

    private var locationJob: Job? = null
    private var activeRole: RelayRole = RelayRole.TRANSMITTER
    private var isPeerConnected = false

    override fun onCreate() {
        super.onCreate()
        nearbyManager = NearbyRelayManager(applicationContext)
        locationSourceManager = LocationSourceManager(applicationContext)
        mockLocationInjector = MockLocationInjector(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRelaySession()
                return START_NOT_STICKY
            }
            ACTION_START_TRANSMITTER -> {
                val code = intent.getStringExtra(EXTRA_PAIRING_CODE).orEmpty()
                val intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, 1000L)
                if (code.length == 6) {
                    startTransmitterSession(code, intervalMs)
                } else {
                    stopRelaySession()
                }
            }
            ACTION_START_RECEIVER -> {
                val code = intent.getStringExtra(EXTRA_PAIRING_CODE).orEmpty()
                if (code.length == 6) {
                    startReceiverSession(code)
                } else {
                    stopRelaySession()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cleanupSession()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTransmitterSession(pairingCode: String, intervalMs: Long) {
        cleanupSession()
        activeRole = RelayRole.TRANSMITTER
        isPeerConnected = false

        startForegroundCompat(
            buildNotification(
                title = getString(R.string.notification_title_transmitter),
                text = "Waiting for receiver with code $pairingCode",
            )
        )

        RelaySessionCoordinator.update {
            RelayServiceSnapshot(
                activeRole = RelayRole.TRANSMITTER,
                connectionState = ConnectionState.ADVERTISING,
                statusMessage = "Advertising code $pairingCode over Bluetooth & Wi-Fi Direct…",
            )
        }

        nearbyManager.startAdvertising(
            pairingCode = pairingCode,
            listener = object : NearbyRelayManager.Listener {
                override fun onAdvertisingStarted() {
                    RelaySessionCoordinator.update {
                        it.copy(
                            connectionState = ConnectionState.ADVERTISING,
                            statusMessage = "Ready to pair with code $pairingCode",
                        )
                    }
                }

                override fun onDiscoveryStarted() = Unit

                override fun onHandshaking(peerName: String) {
                    RelaySessionCoordinator.update {
                        it.copy(
                            connectionState = ConnectionState.HANDSHAKING,
                            peerDeviceName = peerName,
                            statusMessage = "Pairing with $peerName…",
                        )
                    }
                }

                override fun onConnected(endpointId: String, peerName: String) {
                    isPeerConnected = true
                    RelaySessionCoordinator.update {
                        it.copy(
                            connectionState = ConnectionState.STREAMING,
                            peerDeviceName = peerName,
                            statusMessage = "Streaming live GPS to $peerName",
                            errorMessage = null,
                        )
                    }
                    updateNotification(
                        title = getString(R.string.notification_title_transmitter),
                        text = "Streaming live GPS to $peerName",
                    )
                }

                override fun onPacketReceived(packet: GpsTelemetryPacket) = Unit

                override fun onDisconnected(reason: String?) {
                    isPeerConnected = false
                    RelaySessionCoordinator.update {
                        it.copy(
                            connectionState = ConnectionState.ADVERTISING,
                            peerDeviceName = null,
                            statusMessage = "Receiver disconnected. Waiting for reconnection with $pairingCode…",
                        )
                    }
                    updateNotification(
                        title = getString(R.string.notification_title_transmitter),
                        text = "Waiting for receiver with code $pairingCode",
                    )
                }

                override fun onError(message: String) {
                    isPeerConnected = false
                    RelaySessionCoordinator.update {
                        it.copy(
                            connectionState = ConnectionState.ERROR,
                            errorMessage = message,
                            statusMessage = null,
                        )
                    }
                    stopSelf()
                }
            },
        )

        locationJob = serviceScope.launch {
            locationSourceManager.observeLocationUpdates(intervalMs)
                .catch { e ->
                    RelaySessionCoordinator.update {
                        it.copy(
                            connectionState = ConnectionState.ERROR,
                            errorMessage = "GPS read failed: ${e.localizedMessage ?: "Ensure Location is turned on."}",
                        )
                    }
                }
                .collect { packet ->
                    val sent = if (isPeerConnected) nearbyManager.sendTelemetry(packet) else false
                    RelaySessionCoordinator.update { current ->
                        current.copy(
                            latestPacket = packet,
                            packetsTransferred = if (sent) current.packetsTransferred + 1L else current.packetsTransferred,
                        )
                    }
                    if (isPeerConnected) {
                        val coords = String.format(
                            Locale.US,
                            "Sent %.5f, %.5f • %.1f km/h",
                            packet.latitude,
                            packet.longitude,
                            packet.speedMps * 3.6f,
                        )
                        updateNotification(
                            title = getString(R.string.notification_title_transmitter),
                            text = coords,
                        )
                    }
                }
        }
    }

    private fun startReceiverSession(targetCode: String) {
        cleanupSession()
        activeRole = RelayRole.RECEIVER
        isPeerConnected = false

        startForegroundCompat(
            buildNotification(
                title = getString(R.string.notification_title_receiver),
                text = "Searching for transmitter $targetCode…",
            )
        )

        val mockReady = mockLocationInjector.enableMockMode()
        if (mockReady.isFailure) {
            RelaySessionCoordinator.update {
                RelayServiceSnapshot(
                    activeRole = RelayRole.RECEIVER,
                    connectionState = ConnectionState.ERROR,
                    errorMessage = mockReady.exceptionOrNull()?.localizedMessage
                        ?: "Select LinkGPS as mock location app in Developer Options.",
                )
            }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        RelaySessionCoordinator.update {
            RelayServiceSnapshot(
                activeRole = RelayRole.RECEIVER,
                connectionState = ConnectionState.DISCOVERING,
                statusMessage = "Scanning for transmitter $targetCode…",
            )
        }

        nearbyManager.startDiscovery(
            targetCode = targetCode,
            listener = object : NearbyRelayManager.Listener {
                override fun onAdvertisingStarted() = Unit

                override fun onDiscoveryStarted() {
                    RelaySessionCoordinator.update {
                        it.copy(
                            connectionState = ConnectionState.DISCOVERING,
                            statusMessage = "Searching nearby for code $targetCode…",
                        )
                    }
                }

                override fun onHandshaking(peerName: String) {
                    RelaySessionCoordinator.update {
                        it.copy(
                            connectionState = ConnectionState.HANDSHAKING,
                            peerDeviceName = peerName,
                            statusMessage = "Connecting to $peerName…",
                        )
                    }
                }

                override fun onConnected(endpointId: String, peerName: String) {
                    isPeerConnected = true
                    RelaySessionCoordinator.update {
                        it.copy(
                            connectionState = ConnectionState.STREAMING,
                            peerDeviceName = peerName,
                            statusMessage = "Receiving mock GPS from $peerName",
                            errorMessage = null,
                        )
                    }
                    updateNotification(
                        title = getString(R.string.notification_title_receiver),
                        text = "Mock GPS active from $peerName",
                    )
                }

                override fun onPacketReceived(packet: GpsTelemetryPacket) {
                    val result = mockLocationInjector.injectLocation(packet)
                    val latency = (System.currentTimeMillis() - packet.timestampMillis).coerceAtLeast(0L)
                    if (result.isSuccess) {
                        RelaySessionCoordinator.update { current ->
                            current.copy(
                                latestPacket = packet,
                                packetsTransferred = current.packetsTransferred + 1L,
                                lastLatencyMs = latency,
                            )
                        }
                        val coords = String.format(
                            Locale.US,
                            "Mocking %.5f, %.5f • ±%.0fm",
                            packet.latitude,
                            packet.longitude,
                            packet.accuracyMeters,
                        )
                        updateNotification(
                            title = getString(R.string.notification_title_receiver),
                            text = coords,
                        )
                    } else {
                        RelaySessionCoordinator.update {
                            it.copy(
                                connectionState = ConnectionState.ERROR,
                                errorMessage = result.exceptionOrNull()?.localizedMessage
                                    ?: "Mock location injection lost authorization.",
                            )
                        }
                        stopRelaySession()
                    }
                }

                override fun onDisconnected(reason: String?) {
                    isPeerConnected = false
                    mockLocationInjector.disableMockMode()
                    RelaySessionCoordinator.update {
                        it.copy(
                            connectionState = ConnectionState.IDLE,
                            peerDeviceName = null,
                            statusMessage = reason ?: "Transmitter disconnected. Real GPS restored.",
                        )
                    }
                    ServiceCompat.stopForeground(this@GpsRelayForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }

                override fun onError(message: String) {
                    isPeerConnected = false
                    mockLocationInjector.disableMockMode()
                    RelaySessionCoordinator.update {
                        it.copy(
                            connectionState = ConnectionState.ERROR,
                            errorMessage = message,
                            statusMessage = null,
                        )
                    }
                    ServiceCompat.stopForeground(this@GpsRelayForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            },
        )
    }

    private fun stopRelaySession() {
        cleanupSession()
        RelaySessionCoordinator.reset()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupSession() {
        locationJob?.cancel()
        locationJob = null
        isPeerConnected = false
        if (::nearbyManager.isInitialized) {
            nearbyManager.stopAll()
        }
        if (::mockLocationInjector.isInitialized) {
            mockLocationInjector.disableMockMode()
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
    }

    private fun updateNotification(title: String, text: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, GpsRelayForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_stop),
                stopPendingIntent,
            )
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "linkgps_relay_channel"
        private const val NOTIFICATION_ID = 4201

        const val ACTION_START_TRANSMITTER = "com.example.action.START_TRANSMITTER"
        const val ACTION_START_RECEIVER = "com.example.action.START_RECEIVER"
        const val ACTION_STOP = "com.example.action.STOP"

        const val EXTRA_PAIRING_CODE = "extra_pairing_code"
        const val EXTRA_INTERVAL_MS = "extra_interval_ms"
    }
}
