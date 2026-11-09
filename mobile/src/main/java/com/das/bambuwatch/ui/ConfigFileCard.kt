package com.das.bambuwatch.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.das.bambuwatch.data.ConfigFileStatus
import com.das.bambuwatch.data.PrinterConfigFile

/** Shows where printer settings are backed up and offers to grant file access. */
@Composable
fun ConfigFileCard(status: ConfigFileStatus, onAccessResult: () -> Unit) {
    val context = LocalContext.current
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onAccessResult()
    }
    val path = PrinterConfigFile.DISPLAY_PATH

    val (title, body) = when (status) {
        ConfigFileStatus.InSync -> "Settings file" to "Saved to $path. Edit it to change settings; it's loaded on next launch."
        ConfigFileStatus.Missing -> "Settings file" to "No $path yet. It's created when you save settings."
        is ConfigFileStatus.Invalid -> "Settings file has a problem" to "$path was ignored: ${status.reason}"
        ConfigFileStatus.NoAccess ->
            "Back up settings to a file" to
                "Allow file access to save settings to $path so they survive reinstalling and can be edited. " +
                "If the file already exists, it's restored."
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (status is ConfigFileStatus.Invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (status == ConfigFileStatus.NoAccess) {
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val perApp = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                        try {
                            context.startActivity(perApp)
                        } catch (e: ActivityNotFoundException) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                        // Access is re-checked when the app resumes.
                    } else {
                        legacyLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }) { Text("Allow file access") }
            }
        }
    }
}
