package app.logdate.client.feature.widgets

import app.logdate.client.domain.recommendation.MemoryRecallData

/**
 * Maps a recalled memory to widget state. [dateFormatted] is the memory's date as the widget shows
 * it, formatted by the platform in the device language.
 */
internal fun MemoryRecallData.toWidgetState(
    dateFormatted: String,
    fallbackSummary: String = "",
): OnThisDayWidgetState.HasMemory =
    OnThisDayWidgetState.HasMemory(
        dateIso = date.toString(),
        dateFormatted = dateFormatted,
        summary = summary.ifEmpty { fallbackSummary },
        thumbnailUri = mediaUris.firstOrNull(),
    )
