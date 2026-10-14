package app.logdate.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.logdate.client.awareness.daylight.DaylightPeriod
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.systemControlledSelection
import app.logdate.client.media.storage.MediaCollection
import app.logdate.client.media.storage.MediaDirectories
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.ui.audio.AudioContextProcessor
import app.logdate.ui.audio.AudioPlaybackDisplayInfo
import app.logdate.ui.audio.AudioPlaybackState
import app.logdate.ui.audio.DesktopWaveformStorage
import app.logdate.ui.audio.LocalAudioContextProcessor
import app.logdate.ui.audio.LocalAudioPlaybackState
import app.logdate.ui.audio.LocalPlaybackHandoff
import app.logdate.ui.audio.MomentAudioCard
import app.logdate.ui.audio.PlaybackHandoffState
import app.logdate.ui.audio.extraction.DesktopAmplitudeExtractor
import app.logdate.ui.media.MediaDeviceSelectorTags
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.timeline.MomentAudioUiState
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Exercises a recording's direct watch handoff alongside its separate output selector. */
@OptIn(ExperimentalTestApi::class)
class PlaybackHandoffPresentationTest {
    @Test
    fun currentAudioOwnsTransferAndRecovery() {
        val audioFile =
            generateSequence(File(".").absoluteFile) { it.parentFile }
                .map { File(it, "app/android-main/src/androidTest/assets/parity/completed-recording.wav") }
                .first { it.isFile }
        val uri = audioFile.absolutePath
        val durationMs =
            AudioSystem.getAudioInputStream(audioFile).use {
                (it.frameLength * 1_000 / it.format.frameRate).toLong()
            }
        val extractor =
            DesktopAmplitudeExtractor(
                MediaFileResolver(
                    object : MediaDirectories {
                        override fun directory(collection: MediaCollection): String = audioFile.parent
                    },
                ),
            )
        val storage = DesktopWaveformStorage(File("/tmp/logdate-firstclass-continuity-waveforms"))
        val amplitudes = runBlocking { extractor.extractAmplitudes(uri) }
        assertTrue(amplitudes.isNotEmpty() && amplitudes.any { it > 0f }, "Actual audio must produce a populated waveform")
        runBlocking { storage.save(uri, amplitudes) }
        val context = AudioContextProcessor(extractor, storage)
        listOf(
            Triple("compact", 320, 568),
            Triple("phone", 411, 891),
            Triple("tablet", 840, 900),
            Triple("phone-large-text", 411, 891),
        ).forEach { (name, width, height) ->
            runDesktopComposeUiTest(width = width, height = height) {
                val noteId = Uuid.parse("edc2d969-99a9-4653-9520-c94c1b2c8e12")
                val handoff = mutableStateOf(PlaybackHandoffState(available = true))
                var transfers = 0
                var routes = 0
                val transfer = {
                    transfers++
                    handoff.value =
                        if (transfers == 1) {
                            PlaybackHandoffState(available = true, failed = true)
                        } else {
                            PlaybackHandoffState(completed = true)
                        }
                }
                setContent {
                    LogDateTheme(dynamicColor = false) {
                        CompositionLocalProvider(
                            LocalDensity provides Density(1f, if (name == "phone-large-text") 2f else 1f),
                            LocalAudioContextProcessor provides context,
                            LocalAudioPlaybackState provides
                                AudioPlaybackState(
                                    currentlyPlayingId = noteId,
                                    currentUri = uri,
                                    isPlaying = true,
                                    progress = .4f,
                                    duration = durationMs.milliseconds,
                                    displayInfo = AudioPlaybackDisplayInfo(title = "A quiet moment by the lake"),
                                    outputSelection = systemControlledSelection(MediaDeviceKind.AUDIO_OUTPUT),
                                    selectOutputDevice = { routes++ },
                                ),
                            LocalPlaybackHandoff provides handoff.value.copy(onContinue = transfer),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                MomentAudioCard(
                                    audio =
                                        MomentAudioUiState(
                                            uri = uri,
                                            durationMs = durationMs,
                                            transcript = "The water is still today. A quiet moment by the lake before the walk home.",
                                            noteId = noteId,
                                            recordedAt = Instant.parse("2026-05-04T17:35:00Z"),
                                        ),
                                    timeOfDay = DaylightPeriod.MIDDAY,
                                )
                            }
                        }
                    }
                }
                onNodeWithTag(MediaDeviceSelectorTags.chip("Audio output")).assertIsDisplayed()
                save("$name-current", onRoot().captureToImage())
                onNodeWithText("Continue on watch").performScrollTo().assertIsDisplayed().performClick()
                onNodeWithText(
                    "Playback is still on your phone. Check your watch connection and try again.",
                ).performScrollTo().assertIsDisplayed()
                save("$name-retry", onRoot().captureToImage())
                onNodeWithText("Try again").performScrollTo().assertIsDisplayed().performClick()
                onNodeWithText("Playing on your watch.").performScrollTo().assertIsDisplayed()
                save("$name-accepted", onRoot().captureToImage())
                assertEquals(2, transfers)
                assertEquals(0, routes, "App handoff must never claim to select a system audio route")
            }
        }
    }

    private fun save(
        name: String,
        image: ImageBitmap,
    ) {
        val directory = File("/tmp/logdate-firstclass-continuity-renders")
        directory.mkdirs()
        val pixels = image.toPixelMap()
        val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) for (x in 0 until image.width) output.setRGB(x, y, pixels[x, y].toArgb())
        ImageIO.write(output, "png", File(directory, "$name.png"))
    }
}
