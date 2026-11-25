package com.example

import android.Manifest
import android.os.Build
import com.example.data.PermissionHelper
import com.example.model.GpsTelemetryPacket
import com.example.model.PairingPayload
import com.example.model.RelayRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 36])
class LinkGpsLogicTest {

    @Test
    fun `telemetry packet round-trips all fields via JSON`() {
        val original = GpsTelemetryPacket(
            latitude = 37.4219983,
            longitude = -122.084,
            altitudeMeters = 42.5,
            hasAltitude = true,
            speedMps = 13.8f,
            hasSpeed = true,
            bearingDegrees = 215.4f,
            hasBearing = true,
            accuracyMeters = 3.2f,
            timestampMillis = 1795586840000L,
            sequenceNumber = 19L,
        )

        val decoded = GpsTelemetryPacket.fromJsonBytes(original.toJsonBytes())
        assertNotNull(decoded)
        assertEquals(original.latitude, decoded!!.latitude, 1e-7)
        assertEquals(original.longitude, decoded.longitude, 1e-7)
        assertEquals(original.altitudeMeters, decoded.altitudeMeters, 1e-4)
        assertTrue(decoded.hasAltitude)
        assertEquals(original.speedMps, decoded.speedMps, 1e-3f)
        assertTrue(decoded.hasSpeed)
        assertEquals(original.bearingDegrees, decoded.bearingDegrees, 1e-3f)
        assertTrue(decoded.hasBearing)
        assertEquals(original.accuracyMeters, decoded.accuracyMeters, 1e-3f)
        assertEquals(original.timestampMillis, decoded.timestampMillis)
        assertEquals(original.sequenceNumber, decoded.sequenceNumber)
    }

    @Test
    fun `pairing payload parses QR JSON and manual 6-char codes`() {
        val code = "K7M9P2"
        val qr = PairingPayload(code = code, deviceName = "Pixel").toQrString()
        assertEquals(code, PairingPayload.parseQrOrCode(qr))
        assertEquals(code, PairingPayload.parseQrOrCode("k7m-9p2"))
        assertNull(PairingPayload.parseQrOrCode("ABC"))
    }

    @Test
    fun `permission matrix supports Android 9 and Android 13 plus`() {
        val android9ReceiverPerms = PermissionHelper.requiredPermissionsForRole(
            role = RelayRole.RECEIVER,
            sdkInt = Build.VERSION_CODES.P,
        )
        assertTrue(android9ReceiverPerms.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertFalse(android9ReceiverPerms.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertFalse(android9ReceiverPerms.contains(Manifest.permission.POST_NOTIFICATIONS))

        val android13ReceiverPerms = PermissionHelper.requiredPermissionsForRole(
            role = RelayRole.RECEIVER,
            sdkInt = Build.VERSION_CODES.TIRAMISU,
        )
        assertFalse(android13ReceiverPerms.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(android13ReceiverPerms.contains(Manifest.permission.NEARBY_WIFI_DEVICES))
        assertTrue(android13ReceiverPerms.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(android13ReceiverPerms.contains(Manifest.permission.POST_NOTIFICATIONS))
    }
}
