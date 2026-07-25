@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.logdate.ui.audio.AudioWaveformComponent
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.timeline.newstuff.TimelineTranscriptPreview
import kotlin.uuid.Uuid

private val AUDIO_PANEL_BACKGROUND = Color(0xFF1A1A1A)

/**
 * A panel displaying an audio journal entry in story format.
 *
 * Mirrors [TextNotePanel]'s visual language (dark gradient, centered translucent card)
 * but swaps the quote body for a waveform, a play/pause control, and — when available —
 * the transcript. [transcriptionText] is frequently null: transcription is eventual, not
 * guaranteed by the time a rewind is generated, so the panel must read fine either way.
 *
 * @param uri Playable URI of the recording
 * @param durationMs Recording length in milliseconds
 * @param transcriptionText The transcript, or null if not yet available
 * @param dateFormatted Formatted date string showing when the recording was made
 * @param cachedAmplitudes Cached waveform amplitudes, or null when uncached — the
 *   waveform draws a flat placeholder line in that case rather than blocking the panel
 * @param isPlaying Whether this panel's recording is the one currently playing
 * @param playbackProgress Playback progress (0.0–1.0) while [isPlaying] is true
 * @param onTogglePlayback Invoked when the user taps the play/pause control
 * @param modifier Modifier for customizing the panel container
 */
@Composable
fun AudioNotePanel(
    sourceId: Uuid,
    uri: String,
    durationMs: Long,
    transcriptionText: String?,
    dateFormatted: String,
    cachedAmplitudes: List<Float>?,
    isPlaying: Boolean,
    playbackProgress: Float,
    onTogglePlayback: (Uuid, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AUDIO_PANEL_BACKGROUND, Color.Black),
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f),
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .pointerInput(sourceId, uri) {
                            detectTapGestures { onTogglePlayback(sourceId, uri) }
                        },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = if (isPlaying) PlatformIcons.pause() else PlatformIcons.play(),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AudioWaveformComponent(
                audioLevels = cachedAmplitudes.orEmpty(),
                waveformColor = if (isPlaying) Color.White else Color.White.copy(alpha = 0.6f),
                minHeight = 56.dp,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isPlaying) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { playbackProgress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = formatAudioNoteDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )

            if (transcriptionText != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TimelineTranscriptPreview(
                    noteId = sourceId,
                    fallbackTranscript = transcriptionText,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = dateFormatted,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

private fun formatAudioNoteDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
