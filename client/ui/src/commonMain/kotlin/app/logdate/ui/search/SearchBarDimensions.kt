package app.logdate.ui.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Maximum width of a docked search bar, following Material 3 expressive guidance.
 *
 * Material 3 clamps the docked search bar around 720.dp so the field stays comfortable to scan
 * instead of stretching edge-to-edge on tablets, desktop, and unfolded foldables. Sharing it here
 * keeps every search surface on the same ceiling.
 */
val SearchBarMaxWidth = 720.dp

/**
 * Fills the available width but never exceeds [SearchBarMaxWidth], matching Material 3 expressive
 * guidance for docked search bars. On compact windows the bar still spans the full width.
 *
 * The [widthIn] precedes [fillMaxWidth] intentionally: applying [fillMaxWidth] first would fix the
 * incoming constraints to the full available width and defeat the cap on wide windows.
 */
fun Modifier.searchBarMaxWidth(): Modifier = this.widthIn(max = SearchBarMaxWidth).fillMaxWidth()
