package app.logdate.wear.v2.surfaces

import android.content.Context
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.titleCard
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import app.logdate.wear.R
import app.logdate.wear.tile.latestEntryPreview
import app.logdate.wear.v2.memories.OnThisDay
import app.logdate.wear.v2.memories.OnThisDayFinder
import app.logdate.wear.v2.today.DayClock
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/** Clickable IDs on the On this day tile. */
object OnThisDayTileIds {
    const val MOMENT = "on_this_day_moment"
    const val OPEN = "open_home"
}

private const val EMPTY_MAX_LINES = 3
private const val PREVIEW_MAX_LINES = 2

/** The line On this day shows: the recalled day's summary, or its first moment when the summary is blank. */
internal fun onThisDayPreview(
    context: Context,
    day: OnThisDay,
): String = day.summary.ifBlank { latestEntryPreview(context, day.leadMoment) }

/** On this day for today's date, or null when there is none or it can't be read. */
internal suspend fun findOnThisDay(): OnThisDay? =
    try {
        val koin =
            org.koin.java.KoinJavaComponent
                .getKoin()
        koin.get<OnThisDayFinder>().find(koin.get<DayClock>().current().date)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Napier.e("Couldn't find On this day for a tile or complication", e)
        null
    }

/** The On this day tile: how long ago and the day's first moment, which it opens; or a line saying there is none. */
fun MaterialScope.onThisDayTileLayout(day: OnThisDay?): LayoutElementBuilders.LayoutElement {
    val label = context.getString(R.string.wear_memory_on_this_day)
    if (day == null) {
        return primaryLayout(
            titleSlot = { text(label.layoutString) },
            mainSlot = { text(context.getString(R.string.wear_surface_on_this_day_empty).layoutString, maxLines = EMPTY_MAX_LINES) },
            onClick = clickable(SurfaceLaunchActions.home(context), OnThisDayTileIds.OPEN),
        )
    }
    val yearsAgo = context.resources.getQuantityString(R.plurals.wear_memory_years_ago, day.yearsAgo, day.yearsAgo)
    return primaryLayout(
        titleSlot = { text(label.layoutString) },
        mainSlot = {
            titleCard(
                onClick = clickable(SurfaceLaunchActions.moment(context, day.leadMoment.uid), OnThisDayTileIds.MOMENT),
                title = { text(yearsAgo.layoutString) },
                content = { text(onThisDayPreview(context, day).layoutString, maxLines = PREVIEW_MAX_LINES) },
            )
        },
    )
}
