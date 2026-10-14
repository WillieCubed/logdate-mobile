package app.logdate.wear.v2.surfaces

import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.modifiers.clickable
import app.logdate.ui.watch.logDateTodayTileLayout
import app.logdate.ui.watch.todayTilePresentation
import app.logdate.wear.tile.TodaySummary

object TodayTileIds {
    const val RECORD = "record"
    const val OPEN = "open_today"
}

/** The actual tile and the phone preview share the canonical native Material 3 layout. */
internal fun MaterialScope.todayTileLayout(summary: TodaySummary): LayoutElementBuilders.LayoutElement =
    logDateTodayTileLayout(
        presentation = todayTilePresentation(context, summary.entryCount, summary.latestEntry),
        onRecord = clickable(SurfaceLaunchActions.record(context), TodayTileIds.RECORD),
        onOpen = clickable(SurfaceLaunchActions.home(context), TodayTileIds.OPEN),
    )
