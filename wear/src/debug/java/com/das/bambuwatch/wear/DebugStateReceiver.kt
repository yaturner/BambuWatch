package com.das.bambuwatch.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.das.bambuwatch.shared.WatchState

/**
 * DEBUG BUILDS ONLY. Injects a fake printer state so the tile, complication and ongoing
 * activity can be checked without a real print. The phone's next update replaces it.
 *
 * adb shell am broadcast -n com.das.bambuwatch/com.das.bambuwatch.wear.DebugStateReceiver \
 *     --es state RUNNING --ei percent 42 --ei remaining 75 [--el error 5] [--ei hms 1] [--ez connected false]
 */
class DebugStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        val state = WatchState(
            connected = intent.getBooleanExtra("connected", true),
            gcodeState = intent.getStringExtra("state") ?: "RUNNING",
            percent = intent.getIntExtra("percent", 42),
            remainingMin = intent.getIntExtra("remaining", 75),
            layer = intent.getIntExtra("layer", 88),
            totalLayers = intent.getIntExtra("layers", 210),
            nozzleTemp = 220, nozzleTarget = 220, bedTemp = 60, bedTarget = 60,
            jobName = intent.getStringExtra("job") ?: "Debug_Test_Print",
            errorCode = if (intent.hasExtra("error")) intent.getLongExtra("error", 0) else null,
            hmsCount = intent.getIntExtra("hms", 0),
            updatedAt = now,
        )
        (context.applicationContext as WearApp).cache.update(state.toBytes(), now)
        // Receivers may not bind to services (tile/complication updaters do); use the app context.
        WearSurfaces.refresh(context.applicationContext)
    }
}
