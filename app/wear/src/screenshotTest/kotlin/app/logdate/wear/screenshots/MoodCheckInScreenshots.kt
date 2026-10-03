package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import app.logdate.wear.presentation.mood.MoodOption
import app.logdate.wear.presentation.mood.MoodSavedContent
import app.logdate.wear.presentation.mood.SelectMoodContent
import app.logdate.wear.presentation.mood.VoicePromptContent
import com.android.tools.screenshot.PreviewTest

class MoodCheckInScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S01_MoodSelectMood() {
        OnWatchSurface {
            SelectMoodContent(onMoodSelected = {})
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S02_MoodVoicePromptGreat() {
        OnWatchSurface {
            VoicePromptContent(
                selectedMood = MoodOption.GREAT,
                onAttachVoice = {},
                onSkip = {},
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S03_MoodVoicePromptSad() {
        OnWatchSurface {
            VoicePromptContent(
                selectedMood = MoodOption.SAD,
                onAttachVoice = {},
                onSkip = {},
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S04_MoodVoicePromptNull() {
        OnWatchSurface {
            VoicePromptContent(
                selectedMood = null,
                onAttachVoice = {},
                onSkip = {},
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S05_MoodSaved() {
        OnWatchSurface {
            MoodSavedContent()
        }
    }
}
