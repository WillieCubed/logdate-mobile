package app.logdate.feature.editor.ui.camera

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.feature.editor.ui.layout.LocalEntryCreationReserve
import app.logdate.feature.editor.ui.layout.LocalEntryHeaderReserve

/** Keep capture and review controls outside the editor's separate creation surface. */
@Composable
internal fun Modifier.cameraCreationClearance(): Modifier = padding(bottom = LocalEntryCreationReserve.current)

/** Keep camera route controls below the editor's persistent header. */
@Composable
internal fun Modifier.cameraHeaderClearance(): Modifier = padding(top = LocalEntryHeaderReserve.current)
