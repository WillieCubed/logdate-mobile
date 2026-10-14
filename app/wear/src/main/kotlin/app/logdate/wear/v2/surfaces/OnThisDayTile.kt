package app.logdate.wear.v2.surfaces

import android.content.Context
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.modifiers.clickable
import app.logdate.ui.watch.logDateOnThisDayTileLayout
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
    val presentation =
        app.logdate.ui.watch.OnThisDayTilePresentation(
            title = context.getString(R.string.wear_memory_on_this_day),
            yearsAgo = day?.let { context.resources.getQuantityString(R.plurals.wear_memory_years_ago, it.yearsAgo, it.yearsAgo) },
            summary = day?.let { onThisDayPreview(context, it) } ?: context.getString(R.string.wear_surface_on_this_day_empty),
        )
    return logDateOnThisDayTileLayout(
        presentation,
        day?.let { clickable(SurfaceLaunchActions.moment(context, it.leadMoment.uid), OnThisDayTileIds.MOMENT) }
            ?: clickable(SurfaceLaunchActions.home(context), OnThisDayTileIds.OPEN),
    )
}
