package app.logdate.wear.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme
import app.logdate.wear.playback.AudioOutputState
import app.logdate.wear.presentation.memories.MemoryPlayerUiState
import app.logdate.wear.presentation.memories.VoiceMemoriesUiState
import app.logdate.wear.presentation.memories.VoiceMemoryItem
import app.logdate.wear.presentation.memories.WearMemoryPlayerContent
import app.logdate.wear.presentation.memories.WearVoiceMemoriesContent
import app.logdate.wear.presentation.timeline.WearPlaybackUiState
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class WearVoiceMemoriesScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S01_MemoriesEmpty() {
        OnWatch { WearVoiceMemoriesContent(VoiceMemoriesUiState(isLoaded = true)) }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S02_MemoriesList() {
        OnWatch { WearVoiceMemoriesContent(VoiceMemoriesUiState(memories = sampleMemories(), isLoaded = true, hasMore = true)) }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S03_PlayerPlaying() {
        OnWatch { WearMemoryPlayerContent(playerState(WearPlaybackUiState.Active(memory.noteId, progress = 0.35f, durationMs = 84_000))) }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S04_PlayerPaused() {
        OnWatch {
            WearMemoryPlayerContent(
                playerState(WearPlaybackUiState.Active(memory.noteId, progress = 0.35f, durationMs = 84_000, isPaused = true)),
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S05_PlayerPreparing() {
        OnWatch { WearMemoryPlayerContent(playerState(WearPlaybackUiState.Preparing(memory.noteId))) }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S06_PlayerLoadFailed() {
        OnWatch { WearMemoryPlayerContent(playerState(WearPlaybackUiState.Error(memory.noteId))) }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S07_PlayerNoSpeaker() {
        OnWatch {
            WearMemoryPlayerContent(
                playerState(WearPlaybackUiState.BlockedOutput(memory.noteId), output = AudioOutputState.Unavailable),
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S08_PlayerNotFound() {
        OnWatch { WearMemoryPlayerContent(MemoryPlayerUiState(isLoaded = true, memory = null)) }
    }
}

private val memory =
    VoiceMemoryItem(
        noteId = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
        createdAt = Clock.System.now() - 3.hours,
        durationMs = 84_000,
    )

private fun playerState(
    playback: WearPlaybackUiState,
    output: AudioOutputState = AudioOutputState.SpeakerOnly,
) = MemoryPlayerUiState(isLoaded = true, memory = memory, playback = playback, output = output)

private fun sampleMemories(): List<VoiceMemoryItem> {
    val now = Clock.System.now()
    return listOf(
        VoiceMemoryItem(Uuid.random(), now - 3.hours, 84_000),
        VoiceMemoryItem(Uuid.random(), now - 1.days - 2.hours, 12_000),
        VoiceMemoryItem(Uuid.random(), now - 3.days, 605_000),
    )
}

@Composable
private fun OnWatch(content: @Composable () -> Unit) {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) { content() }
    }
}
