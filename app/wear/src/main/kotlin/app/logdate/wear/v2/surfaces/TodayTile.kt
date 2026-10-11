package app.logdate.wear.v2.surfaces

import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import app.logdate.wear.R
import app.logdate.wear.tile.TodaySummary
import app.logdate.wear.tile.latestEntryPreview

/** Clickable IDs on the Today tile. */
object TodayTileIds {
    const val RECORD = "record"
    const val OPEN = "open_today"
}

private const val COUNT_MAX_LINES = 2
private const val LATEST_MAX_LINES = 2

/** The new watch app's Today tile: how many moments today has, the newest one, and Record along the bottom edge. */
internal fun MaterialScope.todayTileLayout(summary: TodaySummary): LayoutElementBuilders.LayoutElement =
    primaryLayout(
        titleSlot = { text(context.getString(R.string.wear_surface_today_title).layoutString) },
        mainSlot = { todayContent(summary) },
        bottomSlot = {
            textEdgeButton(
                onClick = clickable(SurfaceLaunchActions.record(context), TodayTileIds.RECORD),
                labelContent = { text(context.getString(R.string.wear_surface_record).layoutString) },
            )
        },
        onClick = clickable(SurfaceLaunchActions.home(context), TodayTileIds.OPEN),
    )

private fun MaterialScope.todayContent(summary: TodaySummary): LayoutElementBuilders.LayoutElement {
    val count =
        if (summary.entryCount == 0) {
            context.getString(R.string.wear_surface_today_empty)
        } else {
            context.resources.getQuantityString(R.plurals.wear_surface_today_moments, summary.entryCount, summary.entryCount)
        }
    val latest =
        summary.latestEntry
            ?: return text(count.layoutString, typography = Typography.TITLE_MEDIUM, maxLines = COUNT_MAX_LINES)
    return LayoutElementBuilders.Column
        .Builder()
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .addContent(text(count.layoutString, typography = Typography.TITLE_MEDIUM))
        .addContent(text(latestEntryPreview(context, latest).layoutString, typography = Typography.BODY_SMALL, maxLines = LATEST_MAX_LINES))
        .build()
}
