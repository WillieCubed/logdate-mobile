package app.logdate.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.awareness.daylight.DaylightPeriod
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.systemControlledSelection
import app.logdate.feature.editor.ui.audio.expansion.ImmersiveAudioScreen
import app.logdate.ui.audio.color.PaletteGenerator
import app.logdate.ui.media.MediaDeviceSelectorTags
import app.logdate.ui.theme.LogDateTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class ImmersiveAudioOutputContrastTest {
    @Test
    fun outputLabelUsesTheImmersivePaletteEvenWhenTheAppIsLight() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            val palette = PaletteGenerator().generate(DaylightPeriod.NIGHT)
            setContent {
                LogDateTheme(darkTheme = false, dynamicColor = false) {
                    ImmersiveAudioScreen(
                        amplitudes = List(80) { .2f + (it % 11) / 14f },
                        progress = .4f,
                        isPlaying = false,
                        palette = palette,
                        daylightPeriod = DaylightPeriod.NIGHT,
                        durationMs = 73_000,
                        createdAt = Instant.parse("2026-10-13T05:29:00Z"),
                        outputSelection = systemControlledSelection(MediaDeviceKind.AUDIO_OUTPUT),
                    )
                }
            }
            val image = onNodeWithTag(MediaDeviceSelectorTags.chip("Audio output")).captureToImage()
            val pixels = image.toPixelMap()
            val expected = Color(palette.contentColor)
            var legibleForegroundPixels = 0
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val pixel = pixels[x, y]
                    if (abs(pixel.red - expected.red) < .02f &&
                        abs(pixel.green - expected.green) < .02f &&
                        abs(pixel.blue - expected.blue) < .02f
                    ) {
                        legibleForegroundPixels++
                    }
                }
            }
            val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until image.height) for (x in 0 until image.width) output.setRGB(x, y, pixels[x, y].toArgb())
            ImageIO.write(output, "png", File("/tmp/logdate-immersive-output-contrast.png"))
            val player = onRoot().captureToImage()
            val playerPixels = player.toPixelMap()
            val playerOutput = BufferedImage(player.width, player.height, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until player.height) for (x in 0 until player.width) playerOutput.setRGB(x, y, playerPixels[x, y].toArgb())
            ImageIO.write(playerOutput, "png", File("/tmp/logdate-immersive-player-contrast.png"))
            assertTrue(legibleForegroundPixels > 20, "Output text and icon must inherit the playback palette's legible foreground")
        }
}
