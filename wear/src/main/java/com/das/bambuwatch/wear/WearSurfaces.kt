package com.das.bambuwatch.wear

import android.content.Context
import com.das.bambuwatch.wear.complication.PrinterComplicationService
import com.das.bambuwatch.wear.ongoing.PrintOngoingActivity
import com.das.bambuwatch.wear.tile.PrinterTileService

/** Refreshes everything that shows printer state outside the app: tile, complication, ongoing activity. */
object WearSurfaces {
    fun refresh(context: Context) {
        val received = (context.applicationContext as WearApp).cache.latest.value
        PrinterTileService.requestUpdate(context)
        PrinterComplicationService.requestUpdate(context)
        PrintOngoingActivity.update(context, received)
    }
}
