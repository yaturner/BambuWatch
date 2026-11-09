package com.das.bambuwatch.wear.tile

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.ProgressIndicatorColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.EdgeContentLayout
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.das.bambuwatch.wear.MainActivity
import com.das.bambuwatch.wear.WearApp
import com.google.common.util.concurrent.ListenableFuture

/**
 * The print tile. Re-rendered when [DataLayerListenerService] calls [requestUpdate]
 * after a new state arrives, and every [FRESHNESS_MS] so "Phone not updating" can appear.
 */
class PrinterTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val received = (application as WearApp).cache.latest.value
        val model = TileModel.from(received, System.currentTimeMillis())
        val layout = render(this, model, requestParams.deviceConfiguration)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
        return immediate(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        immediate(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val FRESHNESS_MS = 5 * 60_000L

        fun requestUpdate(context: Context) {
            getUpdater(context).requestUpdate(PrinterTileService::class.java)
        }

        private fun <T> immediate(value: T): ListenableFuture<T> =
            CallbackToFutureAdapter.getFuture { completer -> completer.set(value); "immediate" }

        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val GREY = 0xFFBDBDBD.toInt()
        private const val TRACK = 0xFF2A2A2A.toInt()

        internal fun toneColor(tone: TileTone): Int = when (tone) {
            TileTone.IDLE -> 0xFF66BB6A.toInt() // green
            TileTone.PRINTING -> 0xFFFFD600.toInt() // yellow
            TileTone.ERROR -> 0xFFEF5350.toInt() // red
        }

        internal fun render(context: Context, m: TileModel, device: DeviceParameters): LayoutElementBuilders.LayoutElement {
            val accent = toneColor(m.tone)
            val label = text(context, m.label, Typography.TYPOGRAPHY_CAPTION1, accent)
            val center = LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(text(context, m.headline, Typography.TYPOGRAPHY_DISPLAY2, WHITE))
                .apply { m.detail?.let { addContent(text(context, it, Typography.TYPOGRAPHY_CAPTION2, GREY, maxLines = 2)) } }
                .build()
            val bottom = m.secondary?.let {
                text(context, it, Typography.TYPOGRAPHY_CAPTION2, if (m.tone == TileTone.ERROR) accent else GREY)
            }

            val layout = if (m.progress != null) {
                EdgeContentLayout.Builder(device)
                    .setResponsiveContentInsetEnabled(true)
                    .setEdgeContent(
                        CircularProgressIndicator.Builder()
                            .setProgress(m.progress)
                            .setCircularProgressIndicatorColors(ProgressIndicatorColors(accent, TRACK))
                            .build(),
                    )
                    .setPrimaryLabelTextContent(label)
                    .setContent(center)
                    .apply { bottom?.let { setSecondaryLabelTextContent(it) } }
                    .build()
            } else {
                PrimaryLayout.Builder(device)
                    .setResponsiveContentInsetEnabled(true)
                    .setPrimaryLabelTextContent(label)
                    .setContent(center)
                    .apply { bottom?.let { setSecondaryLabelTextContent(it) } }
                    .build()
            }

            // Tapping anywhere opens the watch app (pause/resume buttons come in milestone 7).
            return LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openApp(context)).build())
                .addContent(layout)
                .build()
        }

        private fun text(context: Context, value: String, typography: Int, color: Int, maxLines: Int = 1) =
            Text.Builder(context, value)
                .setTypography(typography)
                .setColor(argb(color))
                .setMaxLines(maxLines)
                .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                .build()

        private fun openApp(context: Context) = ModifiersBuilders.Clickable.Builder()
            .setId("open_app")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(context.packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()
    }
}
