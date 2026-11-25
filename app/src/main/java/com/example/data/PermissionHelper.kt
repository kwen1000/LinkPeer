package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.RelayRole

object PermissionHelper {

    fun requiredPermissionsForRole(role: RelayRole, sdkInt: Int = Build.VERSION.SDK_INT): List<String> {
        val permissions = mutableListOf<String>()

        // Transmitter always needs precise GPS; Android 9-12 (API <= 32) also needs fine location for Nearby BT/Wi-Fi discovery
        if (role == RelayRole.TRANSMITTER || sdkInt <= Build.VERSION_CODES.S_V2) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Android 12+ (API 31+) Bluetooth runtime permissions
        if (sdkInt >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        // Android 13+ (API 33+) Nearby Wi-Fi Devices & Notifications
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions.distinct()
    }

    fun getMissingPermissions(context: Context, role: RelayRole): List<String> {
        return requiredPermissionsForRole(role).filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun formatPermissionLabel(permission: String): String {
        return when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION -> "Precise Location"
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN -> "Nearby Bluetooth"
            Manifest.permission.NEARBY_WIFI_DEVICES -> "Nearby Wi-Fi"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            else -> permission.substringAfterLast('.')
        }
    }
}
