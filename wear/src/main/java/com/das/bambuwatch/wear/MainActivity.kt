package com.das.bambuwatch.wear

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.das.bambuwatch.shared.DataLayerPaths
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
        requestNotificationPermission()
        lifecycleScope.launch { loadExisting(cache) }
    }

    /** The ongoing activity on the watch face needs notification permission (Wear OS 4+). */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { WearSurfaces.refresh(this) }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
            WearSurfaces.refresh(this)
        } catch (e: Exception) {
            Log.w("MainActivity", "Couldn't read existing data items", e)
        }
    }
}
