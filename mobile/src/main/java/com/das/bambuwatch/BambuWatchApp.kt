package com.das.bambuwatch

import android.app.Application
import com.das.bambuwatch.data.ConfigStore
import com.das.bambuwatch.mqtt.PrinterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Holds the app-wide singletons. In milestone 2 the MQTT connection lives in
 * [appScope] (survives rotation, dies with the process); milestone 3 moves
 * its lifetime under a foreground service.
 */
class BambuWatchApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val configStore by lazy { ConfigStore(this) }
    val printerRepository by lazy { PrinterRepository(appScope) }
}
