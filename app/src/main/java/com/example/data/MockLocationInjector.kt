package com.example.data

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.example.model.GpsTelemetryPacket
import com.google.android.gms.location.LocationServices

class MockLocationInjector(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    private var gpsProviderRegistered = false
    private var fusedMockActive = false

    @Suppress("DEPRECATION")
    fun isMockLocationAuthorized(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName,
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun enableMockMode(): Result<Unit> {
        if (!isMockLocationAuthorized()) {
            return Result.failure(
                SecurityException("LinkGPS is not selected as the mock location app in Developer Options.")
            )
        }

        return runCatching {
            ensureGpsTestProvider()
            fusedClient.setMockMode(true)
                .addOnSuccessListener { fusedMockActive = true }
                .addOnFailureListener { fusedMockActive = false }
        }
    }

    @SuppressLint("MissingPermission")
    fun injectLocation(packet: GpsTelemetryPacket): Result<Unit> {
        return runCatching {
            if (!gpsProviderRegistered) {
                ensureGpsTestProvider()
            }

            val location = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = packet.latitude
                longitude = packet.longitude
                accuracy = packet.accuracyMeters.coerceAtLeast(1f)
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

                if (packet.hasAltitude) {
                    altitude = packet.altitudeMeters
                }
                if (packet.hasSpeed) {
                    speed = packet.speedMps.coerceAtLeast(0f)
                }
                if (packet.hasBearing) {
                    bearing = ((packet.bearingDegrees % 360f) + 360f) % 360f
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    verticalAccuracyMeters = 3.0f
                    speedAccuracyMetersPerSecond = 0.5f
                    bearingAccuracyDegrees = 5.0f
                }
            }

            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
            if (fusedMockActive) {
                fusedClient.setMockLocation(location)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disableMockMode() {
        runCatching {
            if (fusedMockActive) {
                fusedClient.setMockMode(false)
                fusedMockActive = false
            }
        }
        runCatching {
            if (gpsProviderRegistered) {
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
                gpsProviderRegistered = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun ensureGpsTestProvider() {
        runCatching {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val properties = ProviderProperties.Builder()
                .setHasNetworkRequirement(false)
                .setHasSatelliteRequirement(false)
                .setHasCellRequirement(false)
                .setHasMonetaryCost(false)
                .setHasAltitudeSupport(true)
                .setHasSpeedSupport(true)
                .setHasBearingSupport(true)
                .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                .setAccuracy(ProviderProperties.ACCURACY_FINE)
                .build()

            locationManager.addTestProvider(LocationManager.GPS_PROVIDER, properties)
        } else {
            // Android 9 (API 28) through Android 11 (API 30) legacy overload
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE,
            )
        }

        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        gpsProviderRegistered = true
    }
}
