package com.das.bambuwatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.das.bambuwatch.BuildConfig
import com.das.bambuwatch.data.ConfigFileStatus
import com.das.bambuwatch.shared.PrinterConfig

private val IPV4 = Regex("""^((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)$""")

@Composable
fun SetupScreen(
    initial: PrinterConfig?,
    fileStatus: ConfigFileStatus,
    onFileAccessResult: () -> Unit,
    onSave: (PrinterConfig) -> Unit,
    onCancel: (() -> Unit)?,
    /** Null hides the demo option (already in demo mode). */
    onTryDemo: (() -> Unit)?,
    discovery: DiscoveryUiState,
    onFindPrinters: () -> Unit,
    onStopFindingPrinters: () -> Unit,
) {
    var ip by rememberSaveable { mutableStateOf(initial?.ip.orEmpty()) }
    var serial by rememberSaveable { mutableStateOf(initial?.serial.orEmpty()) }
    var code by rememberSaveable { mutableStateOf(initial?.accessCode.orEmpty()) }
    var showCode by rememberSaveable { mutableStateOf(false) }

    // Listen for printer announcements while this screen is showing.
    DisposableEffect(Unit) {
        onFindPrinters()
        onDispose { onStopFindingPrinters() }
    }

    val ipError = ip.isNotEmpty() && !IPV4.matches(ip.trim())
    val canSave = IPV4.matches(ip.trim()) && serial.isNotBlank() && code.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Connect to your printer", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Pick your printer below, or type its details. The access code is on the printer under " +
                "Settings → Network (WLAN); the IP address is there too, and the serial number is under " +
                "Settings → Device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DiscoveryCard(
            state = discovery,
            selectedIp = ip,
            selectedSerial = serial,
            onSearch = onFindPrinters,
            onPick = { ip = it.ip; serial = it.serial },
        )
        OutlinedTextField(
            value = ip, onValueChange = { ip = it },
            label = { Text("IP address") }, placeholder = { Text("192.168.1.50") },
            isError = ipError,
            supportingText = if (ipError) {{ Text("Enter an IPv4 address") }} else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = serial, onValueChange = { serial = it },
            label = { Text("Serial number") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = code, onValueChange = { code = it },
            label = { Text("Access code") },
            singleLine = true,
            visualTransformation = if (showCode) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { showCode = !showCode }) { Text(if (showCode) "Hide" else "Show") } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onCancel != null) OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Button(enabled = canSave, onClick = { onSave(PrinterConfig(ip, serial, code)) }) {
                Text("Save & connect")
            }
        }
        if (BuildConfig.SETTINGS_FILE_ENABLED) ConfigFileCard(fileStatus, onFileAccessResult)
        if (onTryDemo != null) {
            HorizontalDivider()
            Text("No printer handy?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Demo mode shows a simulated print on your phone and watch, including alerts. " +
                    "It doesn't connect to anything.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onTryDemo) { Text("Try demo mode") }
        }
        PrivacyPolicyLink()
    }
}
