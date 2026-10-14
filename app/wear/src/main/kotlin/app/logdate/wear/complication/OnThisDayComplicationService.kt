package app.logdate.wear.complication

import android.app.PendingIntent
import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.logdate.wear.R
import app.logdate.wear.launch.WearLaunchIntents
import app.logdate.wear.launch.isWearStartupReady
import app.logdate.wear.v2.memories.OnThisDay
import app.logdate.wear.v2.surfaces.findOnThisDay
import app.logdate.wear.v2.surfaces.onThisDayPreview

/**
 * On this day on a watch face: how long ago, and the day's summary or first moment, which a tap
 * opens. Its manifest entry is disabled; `SurfaceComponents` offers it while the new watch app is on.
 */
class OnThisDayComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.LONG_TEXT) return null
        val label = PlainComplicationText.Builder(getString(R.string.wear_memory_on_this_day)).build()
        return LongTextComplicationData
            .Builder(text = label, contentDescription = label)
            .setTitle(PlainComplicationText.Builder(resources.getQuantityString(R.plurals.wear_memory_years_ago, 1, 1)).build())
            .build()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        if (!isWearStartupReady()) return NoDataComplicationData()
        val day = findOnThisDay() ?: return NoDataComplicationData()
        return onThisDayComplicationData(this, day, WearLaunchIntents.moment(this, day.leadMoment.uid))
    }
}

/** On this day as long text: how long ago as the title, the day's summary or first moment as the text. */
internal fun onThisDayComplicationData(
    context: Context,
    day: OnThisDay,
    tapAction: PendingIntent,
): ComplicationData {
    val yearsAgo = context.resources.getQuantityString(R.plurals.wear_memory_years_ago, day.yearsAgo, day.yearsAgo)
    val preview = onThisDayPreview(context, day)
    val description = context.getString(R.string.wear_surface_on_this_day_description, yearsAgo, preview)
    return LongTextComplicationData
        .Builder(
            text = PlainComplicationText.Builder(preview).build(),
            contentDescription = PlainComplicationText.Builder(description).build(),
        ).setTitle(PlainComplicationText.Builder(yearsAgo).build())
        .setTapAction(tapAction)
        .build()
}
