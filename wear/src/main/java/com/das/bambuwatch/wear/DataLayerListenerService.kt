package com.das.bambuwatch.wear

import com.das.bambuwatch.shared.DataLayerPaths
import com.das.bambuwatch.wear.tile.PrinterTileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Woken by Play services when the phone writes a new item under /printer. */
class DataLayerListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val cache = (application as WearApp).cache
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == DataLayerPaths.STATE) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val bytes = map.getByteArray(DataLayerPaths.STATE_KEY) ?: return@forEach
                cache.update(bytes, map.getLong(DataLayerPaths.SENT_AT_KEY))
            }
        }
        PrinterTileService.requestUpdate(this)
    }
}
