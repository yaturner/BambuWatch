package com.das.bambuwatch.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.das.bambuwatch.data.ConfigFileStatus
import com.das.bambuwatch.shared.AmsTray
import com.das.bambuwatch.shared.ConnectionState
import com.das.bambuwatch.shared.GcodeState
import com.das.bambuwatch.shared.PrinterState
import com.das.bambuwatch.shared.prettyJobName
import com.das.bambuwatch.watch.WatchLinkStatus

@Composable
fun StatusScreen(
    state: PrinterState,
    printerIp: String,
    monitoringEnabled: Boolean,
    fileStatus: ConfigFileStatus,
    onFileAccessResult: () -> Unit,
    watchLink: WatchLinkStatus,
    onSetMonitoring: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onReconnect: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConnectionBanner(state, printerIp)
        MonitoringCard(monitoringEnabled, onSetMonitoring)
        WatchCard(watchLink, monitoringEnabled)
        if (monitoringEnabled) BatteryOptimizationCard()

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stateLabel(state.gcodeState), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text(prettyJobName(state.jobName) ?: "No job", style = MaterialTheme.typography.titleLarge)
                if (state.isPrinting || state.gcodeState == GcodeState.FINISH) {
                    val pct = state.percent ?: 0
                    LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$pct%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatRemaining(state.remainingMinutes))
                            Text("Layer ${state.layer ?: "–"} / ${state.totalLayers ?: "–"}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TempCell("Nozzle", state.nozzleTemp, state.nozzleTarget)
                TempCell("Bed", state.bedTemp, state.bedTarget)
                TempCell("Chamber", state.chamberTemp, null)
            }
        }

        if (state.amsTrays.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text("AMS", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        state.amsHumidityPercent?.let {
                            Text("$it% RH", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.amsTrays.forEach { TrayChip(it, active = it.slot == state.activeTray) }
                    }
                    if (state.activeTray == PrinterState.EXTERNAL_SPOOL) Text("Using external spool")
                }
            }
        }

        if (state.hasError) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Attention needed", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium)
                    state.printError?.takeIf { it != 0L }?.let {
                        Text("Print error 0x${it.toString(16).uppercase().padStart(8, '0')}")
                    }
                    if (state.hmsCount > 0) Text("${state.hmsCount} HMS alert(s)")
                }
            }
        }

        ConfigFileCard(fileStatus, onFileAccessResult)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onEdit) { Text("Printer settings") }
            OutlinedButton(onClick = onReconnect) { Text("Reconnect") }
        }
    }
}

@Composable
private fun ConnectionBanner(state: PrinterState, ip: String) {
    val (label, color) = when (state.connection) {
        ConnectionState.CONNECTED -> "Connected to $ip" to Color(0xFF2E7D32)
        ConnectionState.CONNECTING -> "Connecting to $ip…" to MaterialTheme.colorScheme.tertiary
        ConnectionState.RECONNECTING -> "Reconnecting to $ip…" to MaterialTheme.colorScheme.tertiary
        ConnectionState.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.error
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.size(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.connection != ConnectionState.CONNECTED) {
            state.lastError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WatchCard(link: WatchLinkStatus, monitoring: Boolean) {
    val watch = link.connectedWatches.firstOrNull()
    val (title, body) = when {
        !link.apiAvailable -> "Watch" to "Wear OS isn't available on this phone."
        watch == null -> "No watch connected" to "Pair your watch in Galaxy Wearable to see the print on your wrist."
        link.appInstalledOn.isEmpty() -> "Watch: $watch" to
            "Install Bambu Watch on the watch. Notifications still reach the watch without it."
        !monitoring -> "Watch: $watch" to "Turn on background monitoring to send updates to the watch."
        link.lastSentAt == 0L -> "Watch: $watch" to "Waiting for the first printer update to send."
        else -> "Watch: $watch" to "Watch app installed. Last update sent ${formatAgo(link.lastSentAt)}."
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatAgo(epochMillis: Long): String {
    val secs = ((System.currentTimeMillis() - epochMillis) / 1000).coerceAtLeast(0)
    return when {
        secs < 60 -> "${secs}s ago"
        secs < 3600 -> "${secs / 60} min ago"
        else -> "${secs / 3600} h ago"
    }
}

@Composable
private fun MonitoringCard(enabled: Boolean, onSet: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Background monitoring", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (enabled) "Notifies you (and your watch) when a print finishes or needs attention."
                    else "Off. You won't get print notifications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onSet)
        }
    }
}

/** Battery optimization can delay or drop the background connection; offer to exempt the app. */
@SuppressLint("BatteryLife")
@Composable
private fun BatteryOptimizationCard() {
    val context = LocalContext.current
    val power = remember { context.getSystemService(PowerManager::class.java) }
    var optimized by remember { mutableStateOf(!power.isIgnoringBatteryOptimizations(context.packageName)) }
    LifecycleResumeEffect(Unit) {
        optimized = !power.isIgnoringBatteryOptimizations(context.packageName)
        onPauseOrDispose { }
    }
    if (!optimized) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Battery optimization is on", style = MaterialTheme.typography.titleMedium)
            Text(
                "Android may pause the printer connection while the screen is off, which delays alerts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                )
            }) { Text("Allow unrestricted") }
        }
    }
}

@Composable
private fun TempCell(label: String, actual: Double?, target: Double?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(actual?.let { "${it.toInt()}°" } ?: "–", style = MaterialTheme.typography.titleLarge)
        if (target != null && target > 0) {
            Text("→ ${target.toInt()}°", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrayChip(tray: AmsTray, active: Boolean) {
    val swatch = tray.colorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(32.dp)
                .background(swatch ?: MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .border(
                    width = if (active) 3.dp else 1.dp,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
        )
        Text(if (tray.isEmpty) "Empty" else tray.filamentType ?: "?", style = MaterialTheme.typography.labelSmall)
        tray.remainPercent?.let { Text("$it%", style = MaterialTheme.typography.labelSmall) }
    }
}

private fun stateLabel(s: GcodeState) = when (s) {
    GcodeState.IDLE -> "IDLE"
    GcodeState.PREPARE -> "PREPARING"
    GcodeState.RUNNING -> "PRINTING"
    GcodeState.PAUSE -> "PAUSED"
    GcodeState.FINISH -> "FINISHED"
    GcodeState.FAILED -> "FAILED"
    GcodeState.UNKNOWN -> "WAITING FOR DATA"
}

private fun formatRemaining(min: Int?): String = when {
    min == null -> "–"
    min >= 60 -> "${min / 60}h ${min % 60}m left"
    else -> "${min}m left"
}
