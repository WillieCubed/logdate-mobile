@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Native tile rendering is provided by Android's Wear TileRenderer.
@Composable
actual fun NativeTodayTilePreview(
    presentation: TodayTilePresentation?,
    modifier: Modifier,
) = Unit

@Composable
actual fun NativeOnThisDayTilePreview(
    presentation: OnThisDayTilePresentation,
    modifier: Modifier,
) = Unit
