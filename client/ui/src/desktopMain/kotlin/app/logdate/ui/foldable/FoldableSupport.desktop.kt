package app.logdate.ui.foldable

import androidx.compose.runtime.Composable

/**
 * Desktop implementation of foldable layout info.
 *
 * Desktop devices don't have foldable screens, so this always returns
 * a non-foldable state.
 */
@Composable
actual fun rememberFoldableLayoutInfo(): FoldableLayoutInfo = LocalFoldableLayoutInfoOverride.current ?: FoldableLayoutInfo()
