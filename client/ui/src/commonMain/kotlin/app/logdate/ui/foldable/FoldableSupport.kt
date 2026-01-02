package app.logdate.ui.foldable

import androidx.compose.runtime.Composable

/**
 * Basic foldable device information available across all platforms.
 *
 * On platforms without foldable support (iOS, Desktop), this will always
 * return non-foldable state.
 */
data class FoldableState(
    val isFoldable: Boolean = false,
    val isHalfOpened: Boolean = false,
    val hasVerticalHinge: Boolean = false,
    val hasHorizontalHinge: Boolean = false,
)

/**
 * Remembers the current foldable state of the device.
 *
 * This is a cross-platform composable that provides foldable device
 * information. On Android, it uses androidx.window to detect foldables.
 * On other platforms, it returns a non-foldable state.
 *
 * @return Current foldable state
 */
@Composable
expect fun rememberFoldableState(): FoldableState
