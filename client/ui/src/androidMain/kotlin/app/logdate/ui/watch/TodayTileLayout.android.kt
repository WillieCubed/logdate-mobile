package app.logdate.ui.watch

import android.content.Context
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.material3.titleCard
import androidx.wear.protolayout.types.layoutString
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.ui.R

fun todayTilePresentation(
    context: Context,
    momentCount: Int,
    latest: JournalNote?,
): TodayTilePresentation =
    TodayTilePresentation(
        title = context.getString(R.string.watch_tile_today),
        count =
            if (momentCount == 0) {
                context.getString(R.string.watch_tile_empty)
            } else {
                context.resources.getQuantityString(R.plurals.watch_tile_moments, momentCount, momentCount)
            },
        latest =
            when (latest) {
                is JournalNote.Text -> latest.content.take(30)
                is JournalNote.Audio -> context.getString(R.string.watch_tile_voice)
                is JournalNote.Image -> context.getString(R.string.watch_tile_photo)
                is JournalNote.Video -> context.getString(R.string.watch_tile_video)
                null -> null
            },
        record = context.getString(R.string.watch_tile_record),
    )

fun MaterialScope.logDateTodayTileLayout(
    presentation: TodayTilePresentation,
    onRecord: Clickable,
    onOpen: Clickable,
): LayoutElementBuilders.LayoutElement =
    primaryLayout(
        titleSlot = { text(presentation.title.layoutString) },
        mainSlot = {
            presentation.latest?.let { latest ->
                LayoutElementBuilders.Column
                    .Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(text(presentation.count.layoutString, typography = Typography.TITLE_MEDIUM))
                    .addContent(text(latest.layoutString, typography = Typography.BODY_SMALL, maxLines = 2))
                    .build()
            } ?: text(presentation.count.layoutString, typography = Typography.TITLE_MEDIUM, maxLines = 2)
        },
        bottomSlot = { textEdgeButton(onClick = onRecord, labelContent = { text(presentation.record.layoutString) }) },
        onClick = onOpen,
    )

fun MaterialScope.logDateOnThisDayTileLayout(
    presentation: OnThisDayTilePresentation,
    onOpen: Clickable,
): LayoutElementBuilders.LayoutElement {
    val yearsAgo = presentation.yearsAgo
    if (yearsAgo == null) {
        return primaryLayout(
            titleSlot = { text(presentation.title.layoutString) },
            mainSlot = { text(presentation.summary.layoutString, maxLines = 3) },
            onClick = onOpen,
        )
    }
    return primaryLayout(
        titleSlot = { text(presentation.title.layoutString) },
        mainSlot = {
            titleCard(
                onClick = onOpen,
                title = { text(yearsAgo.layoutString) },
                content = { text(presentation.summary.layoutString, maxLines = 2) },
            )
        },
    )
}
