package app.logdate.wear.presentation.more

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import app.logdate.wear.R

/** The captures and settings that don't earn a place beside the record button. */
@Composable
fun WearMoreScreen(
    onNavigateToQuickText: () -> Unit,
    onNavigateToTimeline: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(timeText = { TimeText() }, scrollState = listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.wear_home_more),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
            }
            item(key = "quick_text") {
                MoreButton(Icons.Default.TextFields, R.string.wear_home_quick_text, onNavigateToQuickText)
            }
            item(key = "timeline") {
                MoreButton(Icons.Default.ViewTimeline, R.string.wear_home_timeline, onNavigateToTimeline)
            }
            item(key = "settings") {
                MoreButton(Icons.Default.Settings, R.string.wear_home_settings, onNavigateToSettings)
            }
        }
    }
}

@Composable
private fun MoreButton(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        icon = { Icon(imageVector = icon, contentDescription = null) },
        label = { Text(stringResource(label)) },
    )
}
