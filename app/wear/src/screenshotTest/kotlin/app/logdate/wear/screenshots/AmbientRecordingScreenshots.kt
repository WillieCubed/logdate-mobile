package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import app.logdate.wear.v2.ambient.AmbientRecordingFace
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The recording face the watch shows in ambient mode. */
class AmbientRecordingScreenshots {
    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun A01_OneMinute() {
        LogDateWearTheme(dynamicColor = false) { AmbientRecordingFace(elapsed = 1.minutes + 20.seconds, burnInProtection = false) }
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun A02_FortyTwoMinutes() {
        LogDateWearTheme(dynamicColor = false) { AmbientRecordingFace(elapsed = 42.minutes, burnInProtection = true) }
    }
}
