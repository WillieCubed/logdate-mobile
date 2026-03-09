package app.logdate.screenshots.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.feature.editor.audio.AudioContext
import app.logdate.feature.editor.audio.model.AudioPalette
import app.logdate.feature.editor.audio.model.AudioSegment
import app.logdate.feature.editor.audio.model.DaylightPeriod
import app.logdate.feature.editor.audio.model.SegmentType
import app.logdate.feature.journals.ui.detail.AudioNoteViewerContent
import app.logdate.feature.journals.ui.detail.AudioNoteViewerUiState
import app.logdate.feature.journals.ui.detail.AudioPlaybackUiState
import app.logdate.feature.journals.ui.detail.NoteViewerErrorContent
import app.logdate.feature.journals.ui.detail.NoteViewerLoadingContent
import app.logdate.feature.journals.ui.detail.NoteViewerScaffoldContent
import app.logdate.feature.journals.ui.detail.NoteViewerShared
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.theme.Spacing
import com.android.tools.screenshot.PreviewTest
import kotlin.uuid.Uuid

private val sharedNote =
    NoteViewerShared(
        noteId = Uuid.parse("00000000-0000-0000-0000-000000000091"),
        createdAt = ScreenshotTestData.baseInstant,
        lastUpdated = ScreenshotTestData.baseInstant,
        location =
            NoteLocation(
                coordinates = NoteCoordinates(latitude = 37.7749, longitude = -122.4194),
                place =
                    NotePlace(
                        id = Uuid.parse("00000000-0000-0000-0000-000000000092"),
                        name = "Mission District",
                        latitude = 37.7749,
                        longitude = -122.4194,
                    ),
            ),
    )

private val audioContext =
    AudioContext(
        amplitudes = ScreenshotTestData.mockAmplitudes,
        segments =
            listOf(
                AudioSegment(timestampMs = 8_000L, type = SegmentType.SPEECH_ONSET),
                AudioSegment(timestampMs = 34_000L, type = SegmentType.VOLUME_PEAK),
            ),
        daylightPeriod = DaylightPeriod.GOLDEN_HOUR,
        palette =
            AudioPalette(
                waveformGradientStart = 0xFFE8A044,
                waveformGradientEnd = 0xFFD4603A,
                playedFillColor = 0xFFE8A044,
                accentColor = 0xFFE8A044,
                immersiveBackground = 0xFF1A0F05,
            ),
    )

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun NoteViewerRoute_Loading() {
    ScreenshotTheme {
        NoteViewerLoadingContent()
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun NoteViewerRoute_Error() {
    ScreenshotTheme {
        NoteViewerErrorContent(message = "Note not found")
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun NoteViewerRoute_Text() {
    ScreenshotTheme {
        NoteViewerScaffoldContent(
            shared = sharedNote,
            onGoBack = {},
        ) {
            Text(
                text = "Captured the train ride home before the details blurred. The city felt quieter than usual.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun NoteViewerRoute_Image() {
    ScreenshotTheme {
        NoteViewerScaffoldContent(
            shared = sharedNote,
            onGoBack = {},
        ) {
            MediaPlaceholder(
                label = "Image",
                description = "Golden-hour desk setup",
            )
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun NoteViewerRoute_Video() {
    ScreenshotTheme {
        NoteViewerScaffoldContent(
            shared = sharedNote,
            onGoBack = {},
        ) {
            MediaPlaceholder(
                label = "Video",
                description = "Evening commute clip",
            )
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun NoteViewerRoute_Audio() {
    ScreenshotTheme {
        AudioNoteViewerContent(
            uiState =
                AudioNoteViewerUiState.Ready(
                    mediaRef = "preview://note-viewer/audio",
                    durationMs = 182_000L,
                    createdAt = ScreenshotTestData.baseInstant,
                    context = audioContext,
                    playbackState = AudioPlaybackUiState(progress = 0.38f, isPlaying = true),
                ),
            onGoBack = {},
        )
    }
}

@Composable
private fun MediaPlaceholder(
    label: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.xxl * 4)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
