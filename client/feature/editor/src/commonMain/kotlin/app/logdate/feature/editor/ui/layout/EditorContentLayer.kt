@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.layout

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.LocalSharedTransitionScope

internal val LocalEntryChromeHeight = compositionLocalOf { 64.dp }

/** Safe layout belongs to the inner document/controls, never the full-window painted surface. */
internal val LocalEntryContentInsets = compositionLocalOf { WindowInsets(0, 0, 0, 0) }

/** Apply after painting the owning surface; shared by writing and recording's inner layout. */
@Composable
internal fun Modifier.entryContentPadding(
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
): Modifier = windowInsetsPadding(LocalEntryContentInsets.current.union(WindowInsets.ime)).padding(top = top, bottom = bottom)

/** Shared block overlays stay inside the content layer, below the frame's interactive chrome. */
@Composable
internal fun EditorContentLayer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) { content() }
    }
}
