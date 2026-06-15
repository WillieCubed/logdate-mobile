@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp

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
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                tonalElevation = 3.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                    if (showDragHandle) {
                        BottomSheetDefaults.DragHandle(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
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
        dragHandle = if (showDragHandle) ({ BottomSheetDefaults.DragHandle() }) else null,
        content = content,
    )
}
