package app.logdate.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Adds an iOS-style leading-edge swipe-back gesture: a horizontal drag that begins within the
 * first 20pt of the surface's left edge and exceeds the activation threshold fires [onBack].
 *
 * No-op on Android and desktop. The Android back gesture is already handled by the OS, and
 * desktop surfaces use keyboard chords.
 */
@Composable
expect fun Modifier.iosEdgeSwipeBack(
    enabled: Boolean,
    onBack: () -> Unit,
): Modifier
