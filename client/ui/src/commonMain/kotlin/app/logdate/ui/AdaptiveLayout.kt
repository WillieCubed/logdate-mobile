package app.logdate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.conditional

@Composable
fun AdaptiveLayout(
    useCompactLayout: Boolean,
    supplementalContent: @Composable () -> Unit,
    mainContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        if (!useCompactLayout) {
            Box(
                modifier = Modifier
                    .widthIn(max = 372.dp)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                supplementalContent()
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .conditional(!useCompactLayout) {
                    clip(MaterialTheme.shapes.large)
                }
                .background(MaterialTheme.colorScheme.surface),
        ) {
            mainContent()
        }
    }
}