package com.das.bambuwatch.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.das.bambuwatch.shared.WatchState
import com.das.bambuwatch.shared.formatErrorCode
import com.das.bambuwatch.shared.prettyJobName
import com.das.bambuwatch.wear.tile.PrinterTileService
import com.das.bambuwatch.wear.tile.TileModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

private val WARN = Color(0xFFFFB300)
private val ERROR = Color(0xFFEF5350)

/** Debug-grade status screen for milestone 4; the tile (milestone 5) is the main surface. */
@Composable
fun WearStatusScreen(latest: StateFlow<Received?>) {
    val received by latest.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(10_000); now = System.currentTimeMillis() }
    }

    MaterialTheme {
        Scaffold(
            timeText = { TimeText() },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        ) {
            val r = received
            if (r == null) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Waiting for phone…\nOpen Bambu Watch on your phone.", textAlign = TextAlign.Center)
                }
                return@Scaffold
            }
            val s = r.state
            ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item { ProgressHeader(s, Color(PrinterTileService.toneColor(TileModel.from(r, now).tone))) }
                item {
                    Text(
                        prettyJobName(s.jobName) ?: "No job",
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (s.gcodeState in setOf("RUNNING", "PAUSE", "PREPARE")) {
                    item { Text(remainingText(s), color = MaterialTheme.colors.onSurfaceVariant) }
                    if (s.totalLayers > 0) item { Text("Layer ${s.layer} / ${s.totalLayers}", style = MaterialTheme.typography.caption1) }
                }
                item { Text("Nozzle ${s.nozzleTemp}° · Bed ${s.bedTemp}°", style = MaterialTheme.typography.caption1) }
                s.errorCode?.let { item { Text("Error ${formatErrorCode(it)}", color = ERROR) } }
                if (s.hmsCount > 0) item { Text("${s.hmsCount} HMS alert(s)", color = WARN) }
                item {
                    Text(
                        (if (s.connected) "" else "Phone not connected to printer · ") + "Updated ${ago(r.sentAt, now)}",
                        style = MaterialTheme.typography.caption3,
                        color = if (s.connected) MaterialTheme.colors.onSurfaceVariant else WARN,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(s: WatchState, color: Color) {
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = if (s.gcodeState in setOf("RUNNING", "PAUSE", "PREPARE", "FAILED")) s.percent / 100f else 1f,
            modifier = Modifier.fillMaxSize(),
            indicatorColor = color,
            strokeWidth = 6.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("${s.percent}%", style = MaterialTheme.typography.title2, fontWeight = FontWeight.Bold)
            Text(stateLabel(s.gcodeState), style = MaterialTheme.typography.caption3, color = color)
        }
    }
}

private fun stateLabel(raw: String) = when (raw) {
    "RUNNING" -> "Printing"
    "PREPARE" -> "Preparing"
    "PAUSE" -> "Paused"
    "FINISH" -> "Done"
    "FAILED" -> "Stopped"
    "IDLE" -> "Idle"
    else -> "—"
}

private fun remainingText(s: WatchState) = when {
    s.remainingMin >= 60 -> "${s.remainingMin / 60}h ${s.remainingMin % 60}m left"
    else -> "${s.remainingMin}m left"
}

private fun ago(sentAt: Long, now: Long): String {
    if (sentAt == 0L) return "—"
    val secs = ((now - sentAt) / 1000).coerceAtLeast(0)
    return when {
        secs < 60 -> "just now"
        secs < 3600 -> "${secs / 60} min ago"
        else -> "${secs / 3600} h ago"
    }
}
