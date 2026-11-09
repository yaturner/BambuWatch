package com.das.bambuwatch.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.das.bambuwatch.shared.InvalidConfigException
import com.das.bambuwatch.shared.PrinterConfig
import com.das.bambuwatch.shared.PrinterConfigCodec
import java.io.File
import java.io.IOException

/** What we know about Documents/BambuWatch/printer.json. */
sealed interface ConfigFileStatus {
    /** The app can't read or write shared storage (All files access not granted). */
    data object NoAccess : ConfigFileStatus
    data object Missing : ConfigFileStatus
    data class Invalid(val reason: String) : ConfigFileStatus
    data object InSync : ConfigFileStatus
}

sealed interface ConfigFileRead {
    data object NoAccess : ConfigFileRead
    data object Missing : ConfigFileRead
    data class Invalid(val reason: String) : ConfigFileRead
    data class Ok(val config: PrinterConfig) : ConfigFileRead
}

/**
 * Plain-text printer settings in the phone's shared Documents folder, so they
 * survive clearing app data and reinstalling, and can be edited or adb-pushed.
 *
 * Android 11+ only lets an app read a shared file it didn't create in its
 * current install (after a reinstall, or a pushed file) with All files access
 * (MANAGE_EXTERNAL_STORAGE). Android 8-10 use WRITE_EXTERNAL_STORAGE.
 */
class PrinterConfigFile(private val context: Context) {
    val file: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "$DIR_NAME/$FILE_NAME",
    )

    fun hasAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun read(): ConfigFileRead {
        if (!hasAccess()) return ConfigFileRead.NoAccess
        if (!file.isFile) return ConfigFileRead.Missing
        return try {
            ConfigFileRead.Ok(PrinterConfigCodec.decode(file.readText()))
        } catch (e: InvalidConfigException) {
            ConfigFileRead.Invalid(e.message ?: "invalid")
        } catch (e: IOException) {
            ConfigFileRead.Invalid("can't read: ${e.message}")
        } catch (e: SecurityException) {
            ConfigFileRead.NoAccess
        }
    }

    /** Writes atomically (temp file + rename). Returns false if not permitted or the write failed. */
    fun write(config: PrinterConfig): Boolean {
        if (!hasAccess()) return false
        return try {
            val dir = file.parentFile ?: return false
            if (!dir.isDirectory && !dir.mkdirs()) return false
            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(PrinterConfigCodec.encode(config))
            if (!tmp.renameTo(file)) {
                file.writeText(PrinterConfigCodec.encode(config))
                tmp.delete()
            }
            true
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        const val DIR_NAME = "BambuWatch"
        const val FILE_NAME = "printer.json"
        const val DISPLAY_PATH = "Documents/$DIR_NAME/$FILE_NAME"
    }
}
