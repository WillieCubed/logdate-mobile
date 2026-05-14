package app.logdate.feature.editor.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.AspectRatios
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.add_a_video_to_your_entry
import logdate.client.feature.editor.generated.resources.choose_from_gallery
import logdate.client.feature.editor.generated.resources.pause_video
import logdate.client.feature.editor.generated.resources.play_video
import org.jetbrains.compose.resources.stringResource
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import java.nio.file.Paths
import javax.swing.SwingUtilities
import kotlin.io.path.absolutePathString

private const val CONTROLS_AUTO_HIDE_MS = 3000L
private const val POSITION_POLL_MS = 250L

@Suppress("ktlint:standard:function-naming")
@Composable
actual fun VideoPlayerContent(
    uri: String,
    modifier: Modifier,
) {
    val vlcAvailable = remember { VlcMediaPlayerFactory.isAvailable() }
    if (!vlcAvailable) {
        ExternalPlayerFallback(uri = uri, modifier = modifier)
        return
    }

    val component = remember(uri) { CallbackMediaPlayerComponent() }
    val mediaPlayer = remember(uri) { component.mediaPlayer() }

    var isPlaying by remember(uri) { mutableStateOf(false) }
    var isMuted by remember(uri) { mutableStateOf(false) }
    var positionMs by remember(uri) { mutableLongStateOf(0L) }
    var durationMs by remember(uri) { mutableLongStateOf(0L) }
    var controlsVisible by remember(uri) { mutableStateOf(true) }
    var lastInteractionTick by remember(uri) { mutableLongStateOf(0L) }

    // Load + parse the media so duration is available before play.
    LaunchedEffect(uri) {
        val resolvedPath = uriToPlayableMrl(uri)
        if (resolvedPath != null) {
            mediaPlayer.media().prepare(resolvedPath)
        } else {
            Napier.w("Desktop video URI couldn't be resolved to a libVLC MRL: $uri")
        }
    }

    // Poll libVLC for the playhead — VLCJ's listener API works too but a 250ms
    // pulse keeps the slider smooth without burning a thread on every frame.
    LaunchedEffect(uri, isPlaying) {
        while (isPlaying) {
            positionMs = mediaPlayer.status().time().coerceAtLeast(0L)
            val total = mediaPlayer.status().length()
            if (total > 0) durationMs = total
            delay(POSITION_POLL_MS)
        }
    }

    LaunchedEffect(lastInteractionTick) {
        if (controlsVisible) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    DisposableEffect(uri) {
        onDispose {
            runCatching {
                mediaPlayer.controls().stop()
                component.release()
            }.onFailure { error ->
                Napier.w("Failed to release Desktop video player", error)
            }
        }
    }

    val playPauseLabel =
        stringResource(if (isPlaying) Res.string.pause_video else Res.string.play_video)

    Box(
        modifier =
            modifier
                .aspectRatio(AspectRatios.WIDESCREEN)
                .background(Color.Black)
                .clickable {
                    controlsVisible = !controlsVisible
                    if (controlsVisible) lastInteractionTick = lastInteractionTick + 1
                },
    ) {
        SwingPanel(
            factory = { component },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(modifier = Modifier.height(1.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    val sliderMax = (durationMs.coerceAtLeast(1L)).toFloat()
                    Slider(
                        value = positionMs.coerceIn(0L, durationMs).toFloat(),
                        onValueChange = { newMs ->
                            positionMs = newMs.toLong()
                            lastInteractionTick = lastInteractionTick + 1
                        },
                        onValueChangeFinished = {
                            mediaPlayer.controls().setTime(positionMs)
                        },
                        valueRange = 0f..sliderMax,
                        colors =
                            SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    mediaPlayer.controls().pause()
                                    isPlaying = false
                                } else {
                                    mediaPlayer.controls().play()
                                    isPlaying = true
                                }
                                lastInteractionTick = lastInteractionTick + 1
                            },
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = playPauseLabel,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        Text(
                            text = formatTimestamp(positionMs) + " / " + formatTimestamp(durationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )

                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                mediaPlayer.audio().setMute(isMuted)
                                lastInteractionTick = lastInteractionTick + 1
                            },
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fallback used when libVLC isn't bundled on this host (e.g. CI). Mirrors the
 * pre-VLCJ behavior: tap to hand off to the user's system player.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun ExternalPlayerFallback(
    uri: String,
    modifier: Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .aspectRatio(AspectRatios.WIDESCREEN)
                .clickable { openVideo(uri) },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.62f),
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(Res.string.play_video),
                modifier = Modifier.padding(18.dp).size(42.dp),
                tint = Color.White,
            )
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return minutes.toString() + ":" + seconds.toString().padStart(2, '0')
}

/**
 * Convert a Kotlin URI string into a path libVLC will accept. libVLC handles
 * file:// URIs natively, but the picker may hand us a raw absolute path.
 */
private fun uriToPlayableMrl(uri: String): String? {
    if (uri.startsWith("file:")) return uri
    return runCatching {
        val file = File(uri)
        if (file.exists()) file.absolutePath else uri
    }.getOrNull()
}

@Suppress("ktlint:standard:function-naming")
@Composable
actual fun VideoPickerContent(
    onVideoSelected: (uri: String, durationMs: Long) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(AspectRatios.WIDESCREEN),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.add_a_video_to_your_entry),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            openVideoDialog { file ->
                                file?.let { selected ->
                                    onVideoSelected(
                                        selected.toURI().toString(),
                                        probeDurationMs(selected),
                                    )
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(Res.string.choose_from_gallery))
                }
            }
        }
    }
}

/**
 * Read a clip's duration via VLCJ without rendering. Returns 0 on hosts where
 * libVLC isn't bundled — the picker doesn't depend on duration to proceed.
 */
private fun probeDurationMs(file: File): Long {
    val player = VlcMediaPlayerFactory.newEmbeddedPlayer() ?: return 0L
    return try {
        if (!player.media().start(file.absolutePath)) {
            return 0L
        }
        // start() blocks until libVLC has parsed metadata.
        val length = player.status().length().coerceAtLeast(0L)
        player.controls().stop()
        length
    } catch (error: Exception) {
        Napier.w("Failed to probe Desktop video duration", error)
        0L
    } finally {
        runCatching { player.release() }
    }
}

private fun openVideo(uri: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(uri))
        }
    }.onFailure { error ->
        Napier.e("Failed to open desktop video: $uri", error)
    }
}

private fun openVideoDialog(callback: (File?) -> Unit) {
    SwingUtilities.invokeLater {
        val fileDialog =
            FileDialog(Frame()).apply {
                title = "Select a Video"
                mode = FileDialog.LOAD
                isMultipleMode = false
                setFilenameFilter { _, name ->
                    val normalized = name.lowercase()
                    normalized.endsWith(".mp4") ||
                        normalized.endsWith(".mov") ||
                        normalized.endsWith(".m4v") ||
                        normalized.endsWith(".webm")
                }
            }

        fileDialog.isVisible = true

        val selectedFile =
            if (fileDialog.file != null) {
                File(Paths.get(fileDialog.directory, fileDialog.file).absolutePathString())
            } else {
                null
            }

        callback(selectedFile)
    }
}
