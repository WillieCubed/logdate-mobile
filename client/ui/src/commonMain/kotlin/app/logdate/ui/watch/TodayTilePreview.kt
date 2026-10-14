@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class TodayTilePresentation(
    val title: String,
    val count: String,
    val latest: String?,
    val record: String,
)

/** Read-only rendering of the actual watch tile; no phone recording actions are attached. */
@Composable
expect fun NativeTodayTilePreview(
    presentation: TodayTilePresentation?,
    modifier: Modifier = Modifier,
)

data class OnThisDayTilePresentation(
    val title: String,
    val yearsAgo: String?,
    val summary: String,
)

@Composable
expect fun NativeOnThisDayTilePreview(
    presentation: OnThisDayTilePresentation,
    modifier: Modifier = Modifier,
)
