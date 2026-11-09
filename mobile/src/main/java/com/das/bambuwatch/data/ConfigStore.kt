package com.das.bambuwatch.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.das.bambuwatch.shared.DemoPrinter
import com.das.bambuwatch.shared.PrinterConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore(name = "printer_config")

/**
 * Printer settings, kept in three places:
 *
 * 1. **Documents/BambuWatch/printer.json** ([PrinterConfigFile]): the main copy whenever the
 *    app has file access. Survives clearing data and reinstalling; hand edits are picked up by
 *    [restore] on the next launch.
 * 2. **DataStore** (access code encrypted with [KeystoreCipher]): the app's working copy, and
 *    the only copy when file access isn't granted.
 * 3. **[defaults]** from the gitignored printer.properties (debug builds only): used when
 *    neither of the above has settings.
 */
class ConfigStore(
    private val context: Context,
    private val defaults: PrinterConfig? = null,
    /** False in the Play build: no printer.json (it needs All files access, which Play restricts). */
    private val fileEnabled: Boolean = true,
) {
    private val cipher = KeystoreCipher()
    private val restoreLock = Mutex()
    val configFile = PrinterConfigFile(context)

    private val _fileStatus = MutableStateFlow<ConfigFileStatus>(ConfigFileStatus.NoAccess)
    val fileStatus: StateFlow<ConfigFileStatus> = _fileStatus.asStateFlow()

    /** The active config: [DemoPrinter.CONFIG] while demo mode is on, else the saved printer. */
    val config: Flow<PrinterConfig?> = context.dataStore.data.map { prefs ->
        if (prefs[KEY_DEMO] == true) DemoPrinter.CONFIG else decodeStored(prefs)
    }

    /** The saved real printer, ignoring demo mode (pre-fills the setup form). */
    val savedPrinter: Flow<PrinterConfig?> = context.dataStore.data.map(::decodeStored)

    val demoMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_DEMO] ?: false }

    /** Demo mode shows a simulated printer (for Play review and trying the app without one). */
    suspend fun setDemoMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DEMO] = enabled }
    }

    /** Whether the background monitor service should run. Defaults to on. */
    val monitoringEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_MONITORING] ?: true }

    /** Defaults for the setup form when nothing is saved (debug builds with printer.properties). */
    val setupDefaults: PrinterConfig? get() = defaults?.takeIf { it.ip.isNotBlank() || it.serial.isNotBlank() }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MONITORING] = enabled }
    }

    /** Saves to DataStore and, if possible, to printer.json. Ends demo mode. */
    suspend fun save(config: PrinterConfig) {
        val normalized = config.normalized()
        writeStored(normalized)
        setDemoMode(false)
        if (!fileEnabled) return
        val written = withContext(Dispatchers.IO) { configFile.write(normalized) }
        _fileStatus.value = when {
            written -> ConfigFileStatus.InSync
            !configFile.hasAccess() -> ConfigFileStatus.NoAccess
            else -> ConfigFileStatus.Invalid("couldn't write ${PrinterConfigFile.DISPLAY_PATH}")
        }
    }

    /**
     * Reconciles the three sources. Call on app start, service start, boot, and after file
     * access is granted. Safe to call repeatedly and concurrently.
     */
    suspend fun restore() = restoreLock.withLock {
        val stored = decodeStored(context.dataStore.data.first())
        val read = if (fileEnabled) withContext(Dispatchers.IO) { configFile.read() } else ConfigFileRead.NoAccess
        when (val fromFile = read) {
            is ConfigFileRead.Ok -> {
                if (fromFile.config != stored) {
                    Log.i(TAG, "Restoring printer settings from ${PrinterConfigFile.DISPLAY_PATH}")
                    writeStored(fromFile.config)
                }
                _fileStatus.value = ConfigFileStatus.InSync
            }
            ConfigFileRead.Missing -> {
                val source = stored ?: defaults?.takeIf { it.isComplete }?.normalized()
                if (source != null) {
                    if (stored == null) writeStored(source)
                    val written = withContext(Dispatchers.IO) { configFile.write(source) }
                    _fileStatus.value = if (written) ConfigFileStatus.InSync else ConfigFileStatus.Missing
                } else {
                    _fileStatus.value = ConfigFileStatus.Missing
                }
            }
            is ConfigFileRead.Invalid -> {
                // Leave a broken file alone so the user can fix it; don't overwrite their edits.
                Log.w(TAG, "Ignoring ${PrinterConfigFile.DISPLAY_PATH}: ${fromFile.reason}")
                if (stored == null) defaults?.takeIf { it.isComplete }?.let { writeStored(it.normalized()) }
                _fileStatus.value = ConfigFileStatus.Invalid(fromFile.reason)
            }
            ConfigFileRead.NoAccess -> {
                if (stored == null) defaults?.takeIf { it.isComplete }?.let { writeStored(it.normalized()) }
                _fileStatus.value = ConfigFileStatus.NoAccess
            }
        }
    }

    private suspend fun writeStored(config: PrinterConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IP] = config.ip
            prefs[KEY_SERIAL] = config.serial
            prefs[KEY_CODE] = cipher.encrypt(config.accessCode)
        }
    }

    private fun decodeStored(prefs: Preferences): PrinterConfig? {
        val ip = prefs[KEY_IP] ?: return null
        val serial = prefs[KEY_SERIAL] ?: return null
        val code = prefs[KEY_CODE]?.let { runCatching { cipher.decrypt(it) }.getOrNull() } ?: return null
        return PrinterConfig(ip, serial, code)
    }

    private companion object {
        const val TAG = "ConfigStore"
        val KEY_IP = stringPreferencesKey("ip")
        val KEY_SERIAL = stringPreferencesKey("serial")
        val KEY_CODE = stringPreferencesKey("access_code_enc")
        val KEY_MONITORING = booleanPreferencesKey("monitoring_enabled")
        val KEY_DEMO = booleanPreferencesKey("demo_mode")
    }
}
