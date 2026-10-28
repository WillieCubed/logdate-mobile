@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.rewind_map_not_configured
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun RewindGeographicMap(
    geometry: RewindMapGeometry,
    modifier: Modifier,
) {
    Box(modifier) {
        RewindMapMessage(stringResource(Res.string.rewind_map_not_configured), Modifier.align(Alignment.Center))
    }
}
