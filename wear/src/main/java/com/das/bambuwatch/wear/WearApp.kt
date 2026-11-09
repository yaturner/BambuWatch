package com.das.bambuwatch.wear

import android.app.Application

class WearApp : Application() {
    val cache by lazy { WatchStateCache(this) }
}
