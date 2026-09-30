package app.logdate.client.domain.location.history

import app.logdate.shared.model.location.HistoryCorrection
import app.logdate.shared.model.location.HistoryField
import app.logdate.shared.model.location.JourneyLeg
import app.logdate.shared.model.location.LocationDayItem
import app.logdate.shared.model.location.PlaceVisit
import app.logdate.shared.model.location.SemanticPlace
import app.logdate.shared.model.location.TravelMode
import kotlin.time.Instant

class ApplyHistoryEdits {
    operator fun invoke(
        items: List<LocationDayItem>,
        edits: List<HistoryCorrection>,
        places: List<SemanticPlace>,
    ): List<LocationDayItem> =
        items.mapNotNull { item ->
            val matching = edits.filter { it.targetEvidenceId in item.evidenceIds }
            if (matching.any { it.field == HistoryField.DELETE }) return@mapNotNull null
            val superseded = matching.flatMap { it.supersedes }.toSet()
            val fields = matching.filterNot { it.id in superseded }.groupBy { it.field }
            val unique = fields.mapValues { (_, values) -> values.distinctBy { it.value }.singleOrNull()?.value }
            val start = unique[HistoryField.START]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: item.start
            val end = unique[HistoryField.END]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: item.end
            val validStart = if (start <= end) start else item.start
            val validEnd = if (start <= end) end else item.end
            when (item) {
                is PlaceVisit ->
                    item.copy(
                        start = validStart,
                        end = validEnd,
                        place = places.firstOrNull { it.id == unique[HistoryField.PLACE] } ?: item.place,
                    )
                is JourneyLeg ->
                    item.copy(
                        start = validStart,
                        end = validEnd,
                        mode =
                            unique[HistoryField.ACTIVITY]?.let { name -> TravelMode.entries.firstOrNull { it.name == name } } ?: item.mode,
                        userConfirmed = unique[HistoryField.ACTIVITY] != null,
                    )
                else -> item
            }
        }
}
