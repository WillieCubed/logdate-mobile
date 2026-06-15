@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp

/**
 * iOS-flavored sheet: 14pt continuous-style rounded top corners, a 36×5pt iOS-style drag
 * handle, and a 35%-black scrim that matches `UISheetPresentationController`'s default dim.
 *
 * M1 replaces this body with a real `UISheetPresentationController` bridge for proper
 * detents and rubber-band swipe-dismiss. For now we ride on `ModalBottomSheet` with iOS
 * visual flavor on top.
 */
private val IosScrim = Color(0x59000000)
private val IosHandleColor = Color(0x4D3C3C43)

@Composable
actual fun PlatformSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    sheetState: SheetState,
    showDragHandle: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalInspectionMode.current) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                tonalElevation = 3.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                    if (showDragHandle) {
                        IosSheetDragHandle()
                    }
                    content()
                }
            }
        }
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        scrimColor = IosScrim,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        dragHandle =
            if (showDragHandle) {
                { IosSheetDragHandle() }
            } else {
                null
            },
        content = content,
    )
}

@Composable
private fun IosSheetDragHandle() {
    Box(
        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 36.dp, height = 5.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(IosHandleColor),
        )
    }
}
