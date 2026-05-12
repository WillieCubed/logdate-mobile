@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.platform

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Modal sheet that adapts to the host platform.
 *
 * - Android / desktop: a `ModalBottomSheet` with the project's default styling.
 * - iOS (N1): the same `ModalBottomSheet`, with N3.3 picking up iOS-flavored corners,
 *   handle, and dim. M1 replaces the iOS actual with a `UISheetPresentationController`
 *   bridge so callsites pick up real iOS detents without further changes.
 *
 * Signature mirrors Material 3's `ModalBottomSheet` so existing callsites move over one
 * import at a time.
 */
@Composable
expect fun PlatformSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    showDragHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
)
