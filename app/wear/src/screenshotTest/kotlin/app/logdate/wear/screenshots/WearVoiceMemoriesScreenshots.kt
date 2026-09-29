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
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Fixed moments and a fixed zone, so the previews render the same on every machine and every day.
 * Dates well in the past also keep the day label off "Today" and "Yesterday".
 */
private val PREVIEW_NOW = Instant.parse("2026-01-15T15:30:00Z")
private val PREVIEW_ZONE = TimeZone.UTC

class WearVoiceMemoriesScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S01_MemoriesEmpty() {
        OnWatch { WearVoiceMemoriesContent(VoiceMemoriesUiState(isLoaded = true), timeZone = PREVIEW_ZONE) }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S02_MemoriesList() {
        OnWatch {
            WearVoiceMemoriesContent(
                VoiceMemoriesUiState(memories = sampleMemories(), isLoaded = true, hasMore = true),
                timeZone = PREVIEW_ZONE,
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S03_PlayerPlaying() {
        OnWatch {
            WearMemoryPlayerContent(
                playerState(WearPlaybackUiState.Active(memory.noteId, progress = 0.35f, durationMs = 84_000)),
                timeZone = PREVIEW_ZONE,
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S04_PlayerPaused() {
        OnWatch {
            WearMemoryPlayerContent(
                playerState(WearPlaybackUiState.Active(memory.noteId, progress = 0.35f, durationMs = 84_000, isPaused = true)),
                timeZone = PREVIEW_ZONE,
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S05_PlayerPreparing() {
        OnWatch { WearMemoryPlayerContent(playerState(WearPlaybackUiState.Preparing(memory.noteId)), timeZone = PREVIEW_ZONE) }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S06_PlayerLoadFailed() {
        OnWatch { WearMemoryPlayerContent(playerState(WearPlaybackUiState.Error(memory.noteId)), timeZone = PREVIEW_ZONE) }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S07_PlayerNoSpeaker() {
        OnWatch {
            WearMemoryPlayerContent(
                playerState(WearPlaybackUiState.BlockedOutput(memory.noteId), output = AudioOutputState.Unavailable),
                timeZone = PREVIEW_ZONE,
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
        createdAt = PREVIEW_NOW,
        durationMs = 84_000,
    )

private fun playerState(
    playback: WearPlaybackUiState,
    output: AudioOutputState = AudioOutputState.SpeakerOnly,
) = MemoryPlayerUiState(isLoaded = true, memory = memory, playback = playback, output = output)

private fun sampleMemories(): List<VoiceMemoryItem> =
    listOf(
        VoiceMemoryItem(Uuid.parse("00000000-0000-0000-0000-000000000001"), PREVIEW_NOW, 84_000),
        VoiceMemoryItem(Uuid.parse("00000000-0000-0000-0000-000000000002"), PREVIEW_NOW - 1.days - 2.hours, 12_000),
        VoiceMemoryItem(Uuid.parse("00000000-0000-0000-0000-000000000003"), PREVIEW_NOW - 5.days, 605_000),
    )

@Composable
private fun OnWatch(content: @Composable () -> Unit) {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) { content() }
    }
}
