package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme
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
        MaterialTheme {
            SelectMoodContent(onMoodSelected = {})
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S02_MoodVoicePromptGreat() {
        MaterialTheme {
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
        MaterialTheme {
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
        MaterialTheme {
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
        MaterialTheme {
            MoodSavedContent()
        }
    }
}
