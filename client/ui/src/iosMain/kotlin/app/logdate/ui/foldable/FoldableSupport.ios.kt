package app.logdate.ui.foldable

import androidx.compose.runtime.Composable

/**
 * iOS implementation of foldable state.
 *
 * iOS devices don't currently have foldable screens, so this always returns
 * a non-foldable state.
 */
@Composable
actual fun rememberFoldableState(): FoldableState {
    return FoldableState(isFoldable = false)
}
