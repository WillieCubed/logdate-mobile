package app.logdate.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * Cross-platform icon set. Each accessor returns a [Painter] so the iOS actual can hand back an
 * SF Symbol rasterized from `UIImage.systemImageNamed`, while Android and desktop continue to
 * render the Material vector counterpart.
 *
 * Callers prefer this object over `Icons.Default.*` for any icon that appears on iOS — it keeps
 * the visual language consistent with the host platform without forcing every site to branch
 * on [currentPlatform].
 */
expect object PlatformIcons {
    @Composable fun back(): Painter

    @Composable fun close(): Painter

    @Composable fun edit(): Painter

    @Composable fun search(): Painter

    @Composable fun settings(): Painter

    @Composable fun add(): Painter

    @Composable fun more(): Painter

    @Composable fun history(): Painter

    @Composable fun brush(): Painter

    @Composable fun delete(): Painter

    @Composable fun share(): Painter

    @Composable fun check(): Painter

    @Composable fun info(): Painter

    @Composable fun play(): Painter

    @Composable fun pause(): Painter

    @Composable fun newEntry(): Painter

    @Composable fun rewind(): Painter

    @Composable fun journal(): Painter

    @Composable fun library(): Painter

    @Composable fun timeline(): Painter

    @Composable fun location(): Painter
}
