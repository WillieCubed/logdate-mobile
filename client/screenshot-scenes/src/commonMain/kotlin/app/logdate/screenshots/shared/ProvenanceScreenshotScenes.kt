@file:Suppress("ktlint:standard:function-naming")

package app.logdate.screenshots.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.provenance.CaptureTimeLabel
import app.logdate.ui.provenance.CapturedOnWatchBanner
import app.logdate.ui.provenance.CapturedOnWatchGlyph
import app.logdate.ui.provenance.LocalWatchProvenanceVisible

/** How a moment captured on the watch is marked: the editor banner, a note's time, and a moment's label. */
internal val provenanceScenes: List<SharedScreenshotSceneSpec> =
    listOf(
        sharedScene(SharedScreenshotSceneId.CapturedOnWatch, ScreenshotSceneGroup.TIMELINE, standardMatrixVariants) {
            CapturedOnWatchScene()
        },
    )

@Composable
private fun CapturedOnWatchScene() {
    CompositionLocalProvider(LocalWatchProvenanceVisible provides true) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CapturedOnWatchBanner()
            CaptureTimeLabel(text = "Today, 3:42 PM", capturedOnWatch = true)
            CaptureTimeLabel(text = "Today, 9:15 AM", capturedOnWatch = false)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "Morning run", style = MaterialTheme.typography.titleSmall)
                CapturedOnWatchGlyph()
            }
        }
    }
}
