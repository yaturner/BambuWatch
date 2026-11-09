package com.das.bambuwatch.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PrinterConfig(
    val ip: String,
    val serial: String,
    val accessCode: String,
) {
    val isComplete: Boolean get() = ip.isNotBlank() && serial.isNotBlank() && accessCode.isNotBlank()
}

private val Context.dataStore by preferencesDataStore(name = "printer_config")

/** Printer connection settings. The access code is encrypted with [KeystoreCipher]. */
class ConfigStore(private val context: Context) {
    private val cipher = KeystoreCipher()

    val config: Flow<PrinterConfig?> = context.dataStore.data.map { prefs ->
        val ip = prefs[KEY_IP] ?: return@map null
        val serial = prefs[KEY_SERIAL] ?: return@map null
        val code = prefs[KEY_CODE]?.let { runCatching { cipher.decrypt(it) }.getOrNull() } ?: return@map null
        PrinterConfig(ip, serial, code)
    }

    suspend fun save(config: PrinterConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IP] = config.ip.trim()
            prefs[KEY_SERIAL] = config.serial.trim().uppercase()
            prefs[KEY_CODE] = cipher.encrypt(config.accessCode.trim())
        }
    }

    private companion object {
        val KEY_IP = stringPreferencesKey("ip")
        val KEY_SERIAL = stringPreferencesKey("serial")
        val KEY_CODE = stringPreferencesKey("access_code_enc")
    }
}
