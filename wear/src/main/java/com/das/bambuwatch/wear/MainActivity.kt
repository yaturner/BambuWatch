package com.das.bambuwatch.wear

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.das.bambuwatch.shared.DataLayerPaths
import com.das.bambuwatch.wear.tile.PrinterTileService
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cache = (application as WearApp).cache
        setContent { WearStatusScreen(cache.latest) }
        lifecycleScope.launch { loadExisting(cache) }
    }

    /** Picks up the phone's current item in case it was written before this app was installed. */
    private suspend fun loadExisting(cache: WatchStateCache) {
        try {
            val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(DataLayerPaths.STATE).build()
            val items = Wearable.getDataClient(this).getDataItems(uri).await()
            try {
                items.forEach { item ->
                    val map = DataMapItem.fromDataItem(item).dataMap
                    map.getByteArray(DataLayerPaths.STATE_KEY)?.let { cache.update(it, map.getLong(DataLayerPaths.SENT_AT_KEY)) }
                }
            } finally {
                items.release()
            }
            PrinterTileService.requestUpdate(this)
        } catch (e: Exception) {
            Log.w("MainActivity", "Couldn't read existing data items", e)
        }
    }
}
