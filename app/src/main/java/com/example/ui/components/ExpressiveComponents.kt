package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.BroadcastOnPersonal
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.PermissionHelper
import com.example.model.ConnectionState
import com.example.model.GpsTelemetryPacket
import com.example.model.RelayRole
import com.example.model.RelayUiState
import com.example.model.UpdateRateOption
import com.example.ui.theme.TelemetryMonoStyle
import java.util.Locale

@Composable
fun RoleSegmentedBar(
    selectedRole: RelayRole,
    onSelectRole: (RelayRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RolePillItem(
                selected = selectedRole == RelayRole.TRANSMITTER,
                label = stringResource(R.string.role_transmitter),
                icon = Icons.Rounded.BroadcastOnPersonal,
                activeContainer = MaterialTheme.colorScheme.primaryContainer,
                activeContent = MaterialTheme.colorScheme.onPrimaryContainer,
                testTag = "role_transmitter_button",
                onClick = { onSelectRole(RelayRole.TRANSMITTER) },
                modifier = Modifier.weight(1f),
            )
            RolePillItem(
                selected = selectedRole == RelayRole.RECEIVER,
                label = stringResource(R.string.role_receiver),
                icon = Icons.Rounded.MyLocation,
                activeContainer = MaterialTheme.colorScheme.tertiaryContainer,
                activeContent = MaterialTheme.colorScheme.onTertiaryContainer,
                testTag = "role_receiver_button",
                onClick = { onSelectRole(RelayRole.RECEIVER) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RolePillItem(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeContainer: Color,
    activeContent: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) activeContainer else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "role_bg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) activeContent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "role_fg",
    )

    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(26.dp),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StatusHeroBanner(
    uiState: RelayUiState,
    modifier: Modifier = Modifier,
) {
    val isTransmitter = uiState.selectedRole == RelayRole.TRANSMITTER
    val containerColor = when (uiState.connectionState) {
        ConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
        ConnectionState.STREAMING -> if (isTransmitter) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
        ConnectionState.ADVERTISING,
        ConnectionState.DISCOVERING,
        ConnectionState.HANDSHAKING -> MaterialTheme.colorScheme.secondaryContainer
        ConnectionState.IDLE -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when (uiState.connectionState) {
        ConnectionState.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        ConnectionState.STREAMING -> if (isTransmitter) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        }
        ConnectionState.ADVERTISING,
        ConnectionState.DISCOVERING,
        ConnectionState.HANDSHAKING -> MaterialTheme.colorScheme.onSecondaryContainer
        ConnectionState.IDLE -> MaterialTheme.colorScheme.onSurface
    }

    val badgeText = when (uiState.connectionState) {
        ConnectionState.IDLE -> "STANDBY"
        ConnectionState.ADVERTISING -> "ADVERTISING"
        ConnectionState.DISCOVERING -> "SCANNING"
        ConnectionState.HANDSHAKING -> "PAIRING"
        ConnectionState.STREAMING -> if (isTransmitter) "LIVE BROADCAST" else "MOCK GPS ACTIVE"
        ConnectionState.ERROR -> "ATTENTION"
    }

    val subtitle = uiState.statusMessage ?: when (uiState.selectedRole) {
        RelayRole.TRANSMITTER -> stringResource(R.string.telemetry_idle_transmitter)
        RelayRole.RECEIVER -> stringResource(R.string.telemetry_idle_receiver)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulseSignalIndicator(
                isActive = uiState.isSessionActive,
                isStreaming = uiState.connectionState == ConnectionState.STREAMING,
                accentColor = contentColor,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = contentColor.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = contentColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    uiState.peerDeviceName?.let { peer ->
                        Text(
                            text = peer,
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.92f),
                )
            }
        }
    }
}

@Composable
private fun PulseSignalIndicator(
    isActive: Boolean,
    isStreaming: Boolean,
    accentColor: Color,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "signal_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isStreaming) 1200 else 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_radius",
    )

    Box(
        modifier = Modifier.size(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isActive) {
                drawCircle(
                    color = accentColor.copy(alpha = (1f - pulse) * 0.35f),
                    radius = (size.minDimension / 2f) * (0.45f + pulse * 0.55f),
                )
            }
            drawCircle(
                color = accentColor.copy(alpha = 0.14f),
                radius = size.minDimension * 0.38f,
            )
        }
        Icon(
            imageVector = if (isStreaming) Icons.Rounded.GpsFixed else Icons.Rounded.SatelliteAlt,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(24.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionReadinessBanner(
    missingPermissions: List<String>,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = missingPermissions.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Permissions Needed",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    missingPermissions.distinctBy { PermissionHelper.formatPermissionLabel(it) }.forEach { perm ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = PermissionHelper.formatPermissionLabel(perm),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestPermissions,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("grant_permissions_button"),
                ) {
                    Text(stringResource(R.string.action_grant_permissions))
                }
            }
        }
    }
}

@Composable
fun MockReadinessBanner(
    isAuthorized: Boolean,
    onOpenDeveloperOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isAuthorized) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isAuthorized) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mock_setup_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isAuthorized) Icons.Rounded.CheckCircle else Icons.Rounded.DeveloperMode,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isAuthorized) {
                        stringResource(R.string.mock_auth_ready)
                    } else {
                        stringResource(R.string.mock_auth_required)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!isAuthorized) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.mock_auth_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenDeveloperOptions,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_dev_settings_button"),
                ) {
                    Text(stringResource(R.string.action_open_dev_settings))
                }
            }
        }
    }
}

@Composable
fun TransmitterControlCard(
    uiState: RelayUiState,
    qrBitmap: Bitmap?,
    onSelectRate: (UpdateRateOption) -> Unit,
    onRegenerateCode: () -> Unit,
    onCopyCode: () -> Unit,
    onStartBroadcast: () -> Unit,
    onStopSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Transmitter Pairing",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.role_transmitter_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // QR + 6-character Pairing Code Showcase
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(116.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.pairing_qr_desc),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.pairing_code_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val formattedCode = uiState.pairingCode.chunked(3).joinToString(" · ")
                        Text(
                            text = formattedCode,
                            style = TelemetryMonoStyle.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("transmitter_pairing_code"),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onCopyCode,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("copy_code_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy pairing code",
                                )
                            }
                            if (!uiState.isSessionActive) {
                                IconButton(
                                    onClick = onRegenerateCode,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("regenerate_code_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Generate new pairing code",
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Update frequency chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "GPS Telemetry Cadence",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UpdateRateOption.entries.forEach { option ->
                        val selected = uiState.updateRate == option
                        FilterChip(
                            selected = selected,
                            enabled = !uiState.isSessionActive,
                            onClick = { onSelectRate(option) },
                            label = { Text(option.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rate_chip_${option.intervalMs}"),
                        )
                    }
                }
            }

            // Primary Action Pill Button
            if (uiState.isSessionActive) {
                Button(
                    onClick = onStopSession,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("stop_session_button"),
                ) {
                    Icon(Icons.Rounded.StopCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_stop_session),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                Button(
                    onClick = onStartBroadcast,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_broadcast_button"),
                ) {
                    Icon(Icons.Rounded.BroadcastOnPersonal, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_start_broadcast),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
fun ReceiverControlCard(
    uiState: RelayUiState,
    onCodeChanged: (String) -> Unit,
    onScanQrClick: () -> Unit,
    onConnectClick: () -> Unit,
    onStopSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(
                    text = "Pair with Transmitter",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.role_receiver_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!uiState.isSessionActive) {
                FilledTonalButton(
                    onClick = onScanQrClick,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("scan_qr_button"),
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.action_scan_qr),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                OutlinedTextField(
                    value = uiState.inputPairingCode,
                    onValueChange = onCodeChanged,
                    label = { Text(stringResource(R.string.pairing_code_label)) },
                    placeholder = { Text("e.g. K7M9P2") },
                    singleLine = true,
                    textStyle = TelemetryMonoStyle.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    ),
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onConnectClick() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing_code_input"),
                )

                Button(
                    onClick = onConnectClick,
                    enabled = uiState.inputPairingCode.length == 6,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("connect_code_button"),
                ) {
                    Icon(Icons.Rounded.BluetoothSearching, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_connect_code),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onStopSession,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("stop_session_button"),
                ) {
                    Icon(Icons.Rounded.StopCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_stop_session),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
fun TelemetryCompassCard(
    uiState: RelayUiState,
    modifier: Modifier = Modifier,
) {
    val packet: GpsTelemetryPacket? = uiState.latestPacket
    val isTransmitter = uiState.selectedRole == RelayRole.TRANSMITTER
    val accentColor = if (isTransmitter) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("telemetry_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isTransmitter) "Live Sensor Telemetry" else "Injected Mock Telemetry",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    val packetInfo = buildString {
                        append("Packets: ${uiState.packetsTransferred}")
                        uiState.lastLatencyMs?.let { append(" • ${it}ms lag") }
                    }
                    Text(
                        text = packetInfo,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                CompassDial(
                    bearingDegrees = packet?.bearingDegrees ?: 0f,
                    hasBearing = packet?.hasBearing == true,
                    accentColor = accentColor,
                )
            }

            if (packet == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.telemetry_waiting),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                // Latitude & Longitude Hero Strip
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        CoordinateColumn(
                            label = "LATITUDE",
                            value = String.format(Locale.US, "%.6f°", packet.latitude),
                            accent = accentColor,
                            modifier = Modifier.weight(1f),
                        )
                        CoordinateColumn(
                            label = "LONGITUDE",
                            value = String.format(Locale.US, "%.6f°", packet.longitude),
                            accent = accentColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // 2x2 Telemetry Grid: Speed, Altitude, Bearing, Accuracy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TelemetryStatTile(
                        label = "SPEED",
                        value = if (packet.hasSpeed) {
                            String.format(Locale.US, "%.1f km/h", packet.speedMps * 3.6f)
                        } else {
                            "0.0 km/h"
                        },
                        subValue = String.format(Locale.US, "%.1f m/s", packet.speedMps),
                        modifier = Modifier.weight(1f),
                    )
                    TelemetryStatTile(
                        label = "ALTITUDE",
                        value = if (packet.hasAltitude) {
                            String.format(Locale.US, "%.1f m", packet.altitudeMeters)
                        } else {
                            "—"
                        },
                        subValue = "WGS84 MSL",
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TelemetryStatTile(
                        label = "BEARING",
                        value = if (packet.hasBearing) {
                            String.format(Locale.US, "%.0f°", packet.bearingDegrees)
                        } else {
                            "—"
                        },
                        subValue = bearingCardinal(packet.bearingDegrees, packet.hasBearing),
                        modifier = Modifier.weight(1f),
                    )
                    TelemetryStatTile(
                        label = "ACCURACY",
                        value = String.format(Locale.US, "±%.1f m", packet.accuracyMeters),
                        subValue = "Fix #${packet.sequenceNumber}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CoordinateColumn(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = TelemetryMonoStyle.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun TelemetryStatTile(
    label: String,
    value: String,
    subValue: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = TelemetryMonoStyle.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CompassDial(
    bearingDegrees: Float,
    hasBearing: Boolean,
    accentColor: Color,
) {
    val animatedBearing by animateFloatAsState(
        targetValue = if (hasBearing) bearingDegrees else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bearing_angle",
    )
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - 6.dp.toPx()
            drawCircle(
                color = trackColor,
                radius = radius,
                style = Stroke(width = 2.dp.toPx()),
            )
            // North tick
            drawLine(
                color = accentColor.copy(alpha = 0.6f),
                start = Offset(center.x, center.y - radius),
                end = Offset(center.x, center.y - radius + 5.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            rotate(degrees = animatedBearing, pivot = center) {
                val needlePath = Path().apply {
                    moveTo(center.x, center.y - radius + 8.dp.toPx())
                    lineTo(center.x + 6.dp.toPx(), center.y + 8.dp.toPx())
                    lineTo(center.x, center.y + 3.dp.toPx())
                    lineTo(center.x - 6.dp.toPx(), center.y + 8.dp.toPx())
                    close()
                }
                drawPath(
                    path = needlePath,
                    color = if (hasBearing) accentColor else trackColor,
                )
            }
        }
    }
}

private fun bearingCardinal(bearing: Float, hasBearing: Boolean): String {
    if (!hasBearing) return "No heading"
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val normalized = ((bearing % 360f) + 360f) % 360f
    val index = (((normalized + 22.5f) / 45f).toInt()) % 8
    return "${dirs[index]} heading"
}
