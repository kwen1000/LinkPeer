package com.example.model

import org.json.JSONObject
import java.security.SecureRandom

enum class RelayRole {
    TRANSMITTER,
    RECEIVER,
}

enum class ConnectionState {
    IDLE,
    ADVERTISING,
    DISCOVERING,
    HANDSHAKING,
    STREAMING,
    ERROR,
}

enum class UpdateRateOption(
    val label: String,
    val intervalMs: Long,
) {
    RATE_1_HZ("1 Hz", 1000L),
    RATE_2_HZ("2 Hz", 500L),
    RATE_5_HZ("5 Hz", 200L);

    companion object {
        fun fromInterval(intervalMs: Long): UpdateRateOption =
            entries.firstOrNull { it.intervalMs == intervalMs } ?: RATE_1_HZ
    }
}

data class GpsTelemetryPacket(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val hasAltitude: Boolean,
    val speedMps: Float,
    val hasSpeed: Boolean,
    val bearingDegrees: Float,
    val hasBearing: Boolean,
    val accuracyMeters: Float,
    val timestampMillis: Long,
    val sequenceNumber: Long,
) {
    fun toJsonBytes(): ByteArray {
        val json = JSONObject().apply {
            put("lat", latitude)
            put("lng", longitude)
            put("alt", altitudeMeters)
            put("hasAlt", hasAltitude)
            put("spd", speedMps.toDouble())
            put("hasSpd", hasSpeed)
            put("brg", bearingDegrees.toDouble())
            put("hasBrg", hasBearing)
            put("acc", accuracyMeters.toDouble())
            put("ts", timestampMillis)
            put("seq", sequenceNumber)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromJsonBytes(bytes: ByteArray): GpsTelemetryPacket? {
            return runCatching {
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                GpsTelemetryPacket(
                    latitude = json.getDouble("lat"),
                    longitude = json.getDouble("lng"),
                    altitudeMeters = json.optDouble("alt", 0.0),
                    hasAltitude = json.optBoolean("hasAlt", false),
                    speedMps = json.optDouble("spd", 0.0).toFloat(),
                    hasSpeed = json.optBoolean("hasSpd", false),
                    bearingDegrees = json.optDouble("brg", 0.0).toFloat(),
                    hasBearing = json.optBoolean("hasBrg", false),
                    accuracyMeters = json.optDouble("acc", 5.0).toFloat(),
                    timestampMillis = json.optLong("ts", System.currentTimeMillis()),
                    sequenceNumber = json.optLong("seq", 0L),
                )
            }.getOrNull()
        }
    }
}

data class PairingPayload(
    val code: String,
    val deviceName: String,
    val protocolVersion: Int = PROTOCOL_VERSION,
) {
    fun toQrString(): String {
        return JSONObject().apply {
            put("app", "linkgps")
            put("v", protocolVersion)
            put("code", code)
            put("name", deviceName)
        }.toString()
    }

    companion object {
        const val PROTOCOL_VERSION = 1
        private const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        private const val CODE_LENGTH = 6
        private val random = SecureRandom()

        fun generateCode(): String {
            return buildString(CODE_LENGTH) {
                repeat(CODE_LENGTH) {
                    append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)])
                }
            }
        }

        fun normalizeCode(raw: String): String {
            return raw.uppercase()
                .filter { it.isLetterOrDigit() }
                .take(CODE_LENGTH)
        }

        fun parseQrOrCode(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.startsWith("{")) {
                val parsed = runCatching {
                    val json = JSONObject(trimmed)
                    if (json.optString("app") == "linkgps") {
                        normalizeCode(json.optString("code"))
                    } else {
                        null
                    }
                }.getOrNull()
                if (!parsed.isNullOrEmpty() && parsed.length == CODE_LENGTH) {
                    return parsed
                }
            }
            val normalized = normalizeCode(trimmed)
            return normalized.takeIf { it.length == CODE_LENGTH }
        }
    }
}

data class RelayUiState(
    val selectedRole: RelayRole = RelayRole.TRANSMITTER,
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val pairingCode: String = PairingPayload.generateCode(),
    val inputPairingCode: String = "",
    val peerDeviceName: String? = null,
    val latestPacket: GpsTelemetryPacket? = null,
    val packetsTransferred: Long = 0L,
    val lastLatencyMs: Long? = null,
    val updateRate: UpdateRateOption = UpdateRateOption.RATE_1_HZ,
    val isMockLocationAuthorized: Boolean = false,
    val missingPermissions: List<String> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
) {
    val isSessionActive: Boolean
        get() = connectionState == ConnectionState.ADVERTISING ||
            connectionState == ConnectionState.DISCOVERING ||
            connectionState == ConnectionState.HANDSHAKING ||
            connectionState == ConnectionState.STREAMING
}
