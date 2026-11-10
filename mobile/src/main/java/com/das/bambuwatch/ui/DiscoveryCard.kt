package com.das.bambuwatch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.das.bambuwatch.shared.DiscoveredPrinter

/** Printers heard on the network; tapping one fills in its IP address and serial number. */
@Composable
fun DiscoveryCard(
    state: DiscoveryUiState,
    selectedIp: String,
    selectedSerial: String,
    onSearch: () -> Unit,
    onPick: (DiscoveredPrinter) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Printers found", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.searching) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onSearch) { Text("Search again") }
                }
            }
            state.found.forEach { p ->
                val selected = p.ip == selectedIp.trim() && p.serial.equals(selectedSerial.trim(), ignoreCase = true)
                Card(
                    Modifier.fillMaxWidth().clickable { onPick(p) },
                    colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                listOfNotNull(p.modelName?.let { "Bambu Lab $it" }, p.name?.let { "\"$it\"" })
                                    .joinToString(" ").ifBlank { "Bambu Lab printer" },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text("${p.ip} · ${p.serial}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(if (selected) "Selected" else "Use", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            val hint = when {
                state.error != null -> state.error
                state.found.isEmpty() && state.searching -> "Listening for printers on this Wi-Fi network…"
                state.found.isEmpty() ->
                    "No printer heard. Check the printer is on and on the same Wi-Fi network, or enter its details below."
                else -> "Tap your printer, then enter its access code."
            }
            Text(hint, style = MaterialTheme.typography.bodySmall,
                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
