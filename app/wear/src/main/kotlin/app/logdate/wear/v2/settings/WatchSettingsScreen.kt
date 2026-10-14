package app.logdate.wear.v2.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.lazy.rememberTransformationSpec

object WatchSettingsScreenTags {
    const val LIST = "watch_settings_list"
}

/** Watch preferences and connection repair, reached directly from the recent-moments screen. */
@Composable
fun WatchSettingsScreen(
    state: WatchSettingsUiState,
    actions: WatchSettingsActions,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.testTag(WatchSettingsScreenTags.LIST),
        ) {
            watchSettingsItems(state, actions, spec)
        }
    }
}
