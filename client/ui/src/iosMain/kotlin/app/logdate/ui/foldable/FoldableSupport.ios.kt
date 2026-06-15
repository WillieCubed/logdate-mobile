package app.logdate.ui.foldable

import androidx.compose.runtime.Composable

/**
 * iOS implementation of foldable layout info.
 *
 * iOS devices don't currently have foldable screens, so this always returns
 * a non-foldable state.
 */
@Composable
actual fun rememberFoldableLayoutInfo(): FoldableLayoutInfo = LocalFoldableLayoutInfoOverride.current ?: FoldableLayoutInfo()
