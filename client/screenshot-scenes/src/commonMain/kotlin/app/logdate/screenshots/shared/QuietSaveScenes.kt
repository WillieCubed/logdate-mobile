package app.logdate.screenshots.shared

import app.logdate.client.media.audio.download.ModelDownloadStatus
import app.logdate.feature.core.settings.ui.VoiceNotesSettingsContent
import app.logdate.feature.core.settings.ui.VoiceNotesSettingsViewModel

internal val quietSaveScenes =
    listOf(
        sharedScene(SharedScreenshotSceneId.QuietSaveSettings, ScreenshotSceneGroup.SETTINGS, osIntegrationVariants) {
            VoiceNotesSettingsContent(
                state =
                    VoiceNotesSettingsViewModel.UiState(
                        quietSaveAvailable = true,
                        transcription = VoiceNotesSettingsViewModel.ModelRowState(ModelDownloadStatus.Completed),
                        tagging = VoiceNotesSettingsViewModel.ModelRowState(ModelDownloadStatus.Idle),
                    ),
                onDownloadTranscription = {},
                onDownloadTagging = {},
                onQuietSaveChanged = {},
                onBack = {},
            )
        },
    )
