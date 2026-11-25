package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MockLocationInjector
import com.example.data.PermissionHelper
import com.example.data.QrCodeHelper
import com.example.data.UserPreferencesRepository
import com.example.model.ConnectionState
import com.example.model.PairingPayload
import com.example.model.RelayRole
import com.example.model.RelayUiState
import com.example.model.UpdateRateOption
import com.example.service.GpsRelayForegroundService
import com.example.service.RelaySessionCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LinkGpsViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferencesRepository = UserPreferencesRepository(appContext)
    private val mockLocationInjector = MockLocationInjector(appContext)

    private data class LocalFormState(
        val pairingCode: String = PairingPayload.generateCode(),
        val inputPairingCode: String = "",
        val isMockAuthorized: Boolean = false,
        val missingPermissions: List<String> = emptyList(),
        val pendingRoleSwitch: RelayRole? = null,
        val transientError: String? = null,
    )

    private val localState = MutableStateFlow(LocalFormState())

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap

    val uiState: StateFlow<RelayUiState> = combine(
        preferencesRepository.preferencesFlow,
        RelaySessionCoordinator.snapshot,
        localState,
    ) { prefs, serviceSnapshot, local ->
        val effectiveRole = if (serviceSnapshot.activeRole != null &&
            serviceSnapshot.connectionState != ConnectionState.IDLE &&
            serviceSnapshot.connectionState != ConnectionState.ERROR
        ) {
            serviceSnapshot.activeRole
        } else {
            prefs.selectedRole
        }

        RelayUiState(
            selectedRole = effectiveRole,
            connectionState = serviceSnapshot.connectionState,
            pairingCode = local.pairingCode,
            inputPairingCode = local.inputPairingCode,
            peerDeviceName = serviceSnapshot.peerDeviceName,
            latestPacket = serviceSnapshot.latestPacket,
            packetsTransferred = serviceSnapshot.packetsTransferred,
            lastLatencyMs = serviceSnapshot.lastLatencyMs,
            updateRate = prefs.updateRate,
            isMockLocationAuthorized = local.isMockAuthorized,
            missingPermissions = local.missingPermissions,
            statusMessage = serviceSnapshot.statusMessage,
            errorMessage = local.transientError ?: serviceSnapshot.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = RelayUiState(),
    )

    private val _pendingRoleConfirmation = MutableStateFlow<RelayRole?>(null)
    val pendingRoleConfirmation: StateFlow<RelayRole?> = _pendingRoleConfirmation

    init {
        refreshEnvironmentStatus()
        regenerateQrBitmap(localState.value.pairingCode)
    }

    fun refreshEnvironmentStatus() {
        val currentRole = uiState.value.selectedRole
        val missing = PermissionHelper.getMissingPermissions(appContext, currentRole)
        val mockOk = mockLocationInjector.isMockLocationAuthorized()
        localState.update {
            it.copy(
                missingPermissions = missing,
                isMockAuthorized = mockOk,
            )
        }
    }

    fun selectRole(role: RelayRole) {
        if (role == uiState.value.selectedRole) return
        if (uiState.value.isSessionActive) {
            _pendingRoleConfirmation.value = role
            return
        }
        applyRoleChange(role)
    }

    fun confirmRoleSwitch() {
        val target = _pendingRoleConfirmation.value ?: return
        _pendingRoleConfirmation.value = null
        stopSession()
        applyRoleChange(target)
    }

    fun dismissRoleSwitchDialog() {
        _pendingRoleConfirmation.value = null
    }

    private fun applyRoleChange(role: RelayRole) {
        viewModelScope.launch {
            preferencesRepository.setSelectedRole(role)
            val missing = PermissionHelper.getMissingPermissions(appContext, role)
            localState.update {
                it.copy(
                    missingPermissions = missing,
                    transientError = null,
                )
            }
        }
    }

    fun selectUpdateRate(rate: UpdateRateOption) {
        viewModelScope.launch {
            preferencesRepository.setUpdateRate(rate)
        }
    }

    fun regeneratePairingCode() {
        if (uiState.value.isSessionActive) return
        val newCode = PairingPayload.generateCode()
        localState.update { it.copy(pairingCode = newCode) }
        regenerateQrBitmap(newCode)
    }

    fun onInputPairingCodeChanged(raw: String) {
        val normalized = PairingPayload.normalizeCode(raw)
        localState.update { it.copy(inputPairingCode = normalized, transientError = null) }
    }

    fun onQrScanned(rawContent: String) {
        val code = PairingPayload.parseQrOrCode(rawContent)
        if (code == null) {
            setTransientError("Unrecognized QR code. Scan a LinkGPS transmitter QR or type the 6-character code.")
            return
        }
        localState.update { it.copy(inputPairingCode = code, transientError = null) }
        startReceiver(code)
    }

    fun setTransientError(message: String?) {
        localState.update { it.copy(transientError = message) }
    }

    fun startTransmitter() {
        refreshEnvironmentStatus()
        val state = uiState.value
        if (state.missingPermissions.isNotEmpty()) {
            setTransientError("Please grant required permissions before broadcasting GPS.")
            return
        }

        val intent = Intent(appContext, GpsRelayForegroundService::class.java).apply {
            action = GpsRelayForegroundService.ACTION_START_TRANSMITTER
            putExtra(GpsRelayForegroundService.EXTRA_PAIRING_CODE, state.pairingCode)
            putExtra(GpsRelayForegroundService.EXTRA_INTERVAL_MS, state.updateRate.intervalMs)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun startReceiver(overrideCode: String? = null) {
        refreshEnvironmentStatus()
        val state = uiState.value
        val code = PairingPayload.normalizeCode(overrideCode ?: state.inputPairingCode)
        if (code.length != 6) {
            setTransientError("Enter the 6-character pairing code shown on the Transmitter.")
            return
        }
        if (!state.isMockLocationAuthorized) {
            setTransientError("Enable LinkGPS in Developer Options → Select mock location app first.")
            return
        }
        if (state.missingPermissions.isNotEmpty()) {
            setTransientError("Please grant nearby connection permissions to discover the transmitter.")
            return
        }

        val intent = Intent(appContext, GpsRelayForegroundService::class.java).apply {
            action = GpsRelayForegroundService.ACTION_START_RECEIVER
            putExtra(GpsRelayForegroundService.EXTRA_PAIRING_CODE, code)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun stopSession() {
        val intent = Intent(appContext, GpsRelayForegroundService::class.java)
        appContext.stopService(intent)
        RelaySessionCoordinator.reset()
        localState.update { it.copy(transientError = null) }
    }

    private fun regenerateQrBitmap(code: String) {
        val deviceName = Build.MODEL?.trim().orEmpty().ifEmpty { "Android Device" }
        val payload = PairingPayload(code = code, deviceName = deviceName).toQrString()
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                QrCodeHelper.generateQrBitmap(payload, sizePx = 480)
            }
            _qrBitmap.value = bitmap
        }
    }
}
