package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.PermissionHelper
import com.example.data.QrCodeHelper
import com.example.model.RelayRole
import com.example.ui.components.MockReadinessBanner
import com.example.ui.components.PermissionReadinessBanner
import com.example.ui.components.ReceiverControlCard
import com.example.ui.components.RoleSegmentedBar
import com.example.ui.components.StatusHeroBanner
import com.example.ui.components.TelemetryCompassCard
import com.example.ui.components.TransmitterControlCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkGpsScreen(
    viewModel: LinkGpsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val qrBitmap by viewModel.qrBitmap.collectAsStateWithLifecycle()
    val pendingRoleConfirmation by viewModel.pendingRoleConfirmation.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshEnvironmentStatus()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshEnvironmentStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val error = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        viewModel.setTransientError(null)
    }

    if (pendingRoleConfirmation != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRoleSwitchDialog() },
            title = { Text("Stop Active GPS Session?") },
            text = {
                Text("Switching roles will stop your current connection and restore normal GPS state.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRoleSwitch() }) {
                    Text("Switch Role")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRoleSwitchDialog() }) {
                    Text("Keep Running")
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "Offline Bluetooth & Wi-Fi Direct GPS Relay",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val isExpandedLayout = maxWidth >= 680.dp
            val scrollState = rememberScrollState()

            val requestMissingPermissions = {
                val missing = PermissionHelper.requiredPermissionsForRole(uiState.selectedRole)
                permissionLauncher.launch(missing.toTypedArray())
            }

            val openDeveloperSettings: () -> Unit = {
                val devIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(devIntent) }
                    .onFailure { runCatching { context.startActivity(fallbackIntent) } }
            }

            val copyPairingCode: () -> Unit = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("LinkGPS Code", uiState.pairingCode))
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Copied pairing code ${uiState.pairingCode}")
                }
            }

            val launchQrScan = {
                QrCodeHelper.launchQrScanner(
                    context = context,
                    onResult = { raw -> viewModel.onQrScanned(raw) },
                    onError = { msg -> viewModel.setTransientError(msg) },
                )
            }

            if (isExpandedLayout) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RoleSegmentedBar(
                        selectedRole = uiState.selectedRole,
                        onSelectRole = { viewModel.selectRole(it) },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            PermissionReadinessBanner(
                                missingPermissions = uiState.missingPermissions,
                                onRequestPermissions = requestMissingPermissions,
                            )
                            AnimatedContent(
                                targetState = uiState.selectedRole,
                                transitionSpec = {
                                    fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                                        fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
                                },
                                label = "role_controls_expanded",
                            ) { role ->
                                when (role) {
                                    RelayRole.TRANSMITTER -> TransmitterControlCard(
                                        uiState = uiState,
                                        qrBitmap = qrBitmap,
                                        onSelectRate = { viewModel.selectUpdateRate(it) },
                                        onRegenerateCode = { viewModel.regeneratePairingCode() },
                                        onCopyCode = { copyPairingCode() },
                                        onStartBroadcast = { viewModel.startTransmitter() },
                                        onStopSession = { viewModel.stopSession() },
                                    )
                                    RelayRole.RECEIVER -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        MockReadinessBanner(
                                            isAuthorized = uiState.isMockLocationAuthorized,
                                            onOpenDeveloperOptions = openDeveloperSettings,
                                        )
                                        ReceiverControlCard(
                                            uiState = uiState,
                                            onCodeChanged = { viewModel.onInputPairingCodeChanged(it) },
                                            onScanQrClick = launchQrScan,
                                            onConnectClick = { viewModel.startReceiver() },
                                            onStopSession = { viewModel.stopSession() },
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            StatusHeroBanner(uiState = uiState)
                            TelemetryCompassCard(uiState = uiState)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    RoleSegmentedBar(
                        selectedRole = uiState.selectedRole,
                        onSelectRole = { viewModel.selectRole(it) },
                    )
                    StatusHeroBanner(uiState = uiState)
                    PermissionReadinessBanner(
                        missingPermissions = uiState.missingPermissions,
                        onRequestPermissions = requestMissingPermissions,
                    )
                    AnimatedContent(
                        targetState = uiState.selectedRole,
                        transitionSpec = {
                            fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                                fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
                        },
                        label = "role_controls_compact",
                    ) { role ->
                        when (role) {
                            RelayRole.TRANSMITTER -> TransmitterControlCard(
                                uiState = uiState,
                                qrBitmap = qrBitmap,
                                onSelectRate = { viewModel.selectUpdateRate(it) },
                                onRegenerateCode = { viewModel.regeneratePairingCode() },
                                onCopyCode = { copyPairingCode() },
                                onStartBroadcast = { viewModel.startTransmitter() },
                                onStopSession = { viewModel.stopSession() },
                            )
                            RelayRole.RECEIVER -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                MockReadinessBanner(
                                    isAuthorized = uiState.isMockLocationAuthorized,
                                    onOpenDeveloperOptions = openDeveloperSettings,
                                )
                                ReceiverControlCard(
                                    uiState = uiState,
                                    onCodeChanged = { viewModel.onInputPairingCodeChanged(it) },
                                    onScanQrClick = launchQrScan,
                                    onConnectClick = { viewModel.startReceiver() },
                                    onStopSession = { viewModel.stopSession() },
                                )
                            }
                        }
                    }
                    TelemetryCompassCard(uiState = uiState)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
