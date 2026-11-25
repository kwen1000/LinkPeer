package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.example.model.GpsTelemetryPacket
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicLong

class LocationSourceManager(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sequenceCounter = AtomicLong(0L)

    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(intervalMs: Long): Flow<GpsTelemetryPacket> = callbackFlow {
        sequenceCounter.set(0L)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs / 2L).coerceAtLeast(150L))
            .setWaitForAccurateLocation(false)
            .build()

        val fusedCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                trySend(location.toPacket(sequenceCounter.incrementAndGet()))
            }
        }

        var fallbackListener: LocationListener? = null

        fun startFrameworkFallback() {
            if (fallbackListener != null) return
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    trySend(location.toPacket(sequenceCounter.incrementAndGet()))
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            fallbackListener = listener
            runCatching {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        intervalMs,
                        0f,
                        listener,
                        Looper.getMainLooper(),
                    )
                }
            }
        }

        fusedClient.lastLocation.addOnSuccessListener { last ->
            if (last != null) {
                trySend(last.toPacket(sequenceCounter.incrementAndGet()))
            }
        }

        fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
            .addOnFailureListener {
                startFrameworkFallback()
            }

        awaitClose {
            fusedClient.removeLocationUpdates(fusedCallback)
            fallbackListener?.let { listener ->
                runCatching { locationManager.removeUpdates(listener) }
            }
        }
    }

    private fun Location.toPacket(seq: Long): GpsTelemetryPacket {
        return GpsTelemetryPacket(
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitude,
            hasAltitude = hasAltitude(),
            speedMps = speed,
            hasSpeed = hasSpeed(),
            bearingDegrees = bearing,
            hasBearing = hasBearing(),
            accuracyMeters = if (hasAccuracy()) accuracy else 5f,
            timestampMillis = System.currentTimeMillis(),
            sequenceNumber = seq,
        )
    }
}
