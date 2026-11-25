package com.example.service

import com.example.model.ConnectionState
import com.example.model.GpsTelemetryPacket
import com.example.model.RelayRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RelayServiceSnapshot(
    val activeRole: RelayRole? = null,
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val peerDeviceName: String? = null,
    val latestPacket: GpsTelemetryPacket? = null,
    val packetsTransferred: Long = 0L,
    val lastLatencyMs: Long? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

object RelaySessionCoordinator {

    private val _snapshot = MutableStateFlow(RelayServiceSnapshot())
    val snapshot: StateFlow<RelayServiceSnapshot> = _snapshot.asStateFlow()

    fun update(transform: (RelayServiceSnapshot) -> RelayServiceSnapshot) {
        _snapshot.update(transform)
    }

    fun reset() {
        _snapshot.value = RelayServiceSnapshot()
    }
}
