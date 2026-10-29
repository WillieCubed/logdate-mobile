package app.logdate.feature.rewind.ui.detail

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

// Matches the current Apple RewindBlockLayout; these constrain memory content, never the scene.
internal val RewindReadingMaxWidth = 600.dp
internal val RewindMediaMaxWidth = 760.dp
internal val RewindMemoryContentPadding = 24.dp

internal val LocalRewindChromeHeight = staticCompositionLocalOf { 0.dp }

// Short mobile windows must fit the bitmap instead of collapsing the caption column.
internal val RewindMinimumReadableWidth = 240.dp
