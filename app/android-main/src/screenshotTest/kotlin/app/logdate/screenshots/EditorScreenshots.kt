package app.logdate.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.editor.audio.model.AudioPalette
import app.logdate.feature.editor.ui.audio.expansion.SpatialExpandedAudioBlock
import app.logdate.feature.editor.ui.layout.ImmersiveEditorLayout
import app.logdate.ui.theme.LogDateTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Instant

private const val PHONE = "spec:width=411dp,height=891dp"

// Warm afternoon palette (golden hour colors)
private val mockPalette = AudioPalette(
    waveformGradientStart = 0xFFE8A044,
    waveformGradientEnd = 0xFFD4603A,
    playedFillColor = 0xFFE8A044,
    accentColor = 0xFFE8A044,
    immersiveBackground = 0xFF1A0F05,
)

// ~40 bars of fake waveform data
private val mockAmplitudes = listOf(
    0.3f, 0.5f, 0.4f, 0.7f, 0.6f, 0.8f, 0.9f, 0.7f, 0.5f, 0.6f,
    0.4f, 0.3f, 0.5f, 0.8f, 0.7f, 0.6f, 0.9f, 0.8f, 0.7f, 0.5f,
    0.4f, 0.6f, 0.7f, 0.5f, 0.4f, 0.6f, 0.8f, 0.9f, 0.7f, 0.6f,
    0.5f, 0.4f, 0.3f, 0.5f, 0.6f, 0.7f, 0.8f, 0.6f, 0.4f, 0.3f,
)

private val mockCreatedAt = Instant.fromEpochMilliseconds(1_740_000_000_000L) // ~Feb 2025 afternoon

// ─── ImmersiveEditorLayout ────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun ImmersiveEditorLayout_Empty() {
    LogDateTheme {
        ImmersiveEditorLayout(
            isEditorFocused = false,
            topBarContent = { Text("← Back") },
            editorContent = {
                Box(modifier = Modifier.fillMaxSize())
            },
            bottomContent = { Text("Toolbar placeholder") },
        )
    }
}

// ─── SpatialExpandedAudioBlock ────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun SpatialExpandedAudioBlock_Paused() {
    LogDateTheme {
        SpatialExpandedAudioBlock(
            amplitudes = mockAmplitudes,
            progress = 0.35f,
            isPlaying = false,
            palette = mockPalette,
            durationMs = 127_000L,
            createdAt = mockCreatedAt,
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun SpatialExpandedAudioBlock_Playing() {
    LogDateTheme {
        SpatialExpandedAudioBlock(
            amplitudes = mockAmplitudes,
            progress = 0.6f,
            isPlaying = true,
            palette = mockPalette,
            durationMs = 127_000L,
            createdAt = mockCreatedAt,
        )
    }
}

