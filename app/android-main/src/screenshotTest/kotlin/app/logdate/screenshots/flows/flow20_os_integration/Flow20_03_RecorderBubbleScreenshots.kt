package app.logdate.screenshots.flows.flow20_os_integration

import androidx.compose.runtime.Composable
import app.logdate.feature.editor.ui.audio.RecorderBubbleContent
import app.logdate.feature.editor.ui.audio.RecorderBubbleState
import app.logdate.screenshots.common.BubbleWindowPreview
import app.logdate.screenshots.common.OsIntegrationPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

private val speechLevels =
    List(64) { index -> (0.35f + 0.3f * sin(index * PI / 5).toFloat() + 0.2f * sin(index * PI / 13).toFloat()).coerceIn(0.05f, 1f) }

private val recording =
    RecorderBubbleState(
        elapsed = 83.seconds,
        levels = speechLevels,
        paused = false,
        transcript =
            "Walked the long way home along the river. The light on the water made me want to " +
                "remember exactly how quiet it was.",
    )

/** The floating recorder while recording, with the live transcript filling the free space. */
@PreviewTest
@OsIntegrationPreviewMatrix
@BubbleWindowPreview
@Composable
fun S01_RecorderBubbleRecording() {
    ScreenshotTheme {
        RecorderBubbleContent(state = recording, onPauseToggle = {}, onFinish = {}, onOpenApp = {})
    }
}

/** The floating recorder paused, before anything was transcribed. */
@PreviewTest
@OsIntegrationPreviewMatrix
@BubbleWindowPreview
@Composable
fun S02_RecorderBubblePaused() {
    ScreenshotTheme {
        RecorderBubbleContent(
            state = recording.copy(elapsed = 12.seconds, paused = true, transcript = null),
            onPauseToggle = {},
            onFinish = {},
            onOpenApp = {},
        )
    }
}
