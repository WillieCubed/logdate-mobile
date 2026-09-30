package app.logdate.feature.location.timeline.ui.history

import app.logdate.shared.model.location.LocationDayItem

internal fun remapHistorySelection(
    selectedId: String?,
    previous: List<LocationDayItem>,
    updated: List<LocationDayItem>,
    savedEvidenceId: String? = null,
): String? {
    if (selectedId == null) return null
    if (updated.any { it.id == selectedId }) return selectedId
    val evidence = previous.firstOrNull { it.id == selectedId }?.evidenceIds?.firstOrNull() ?: savedEvidenceId ?: return null
    return updated.singleOrNull { evidence in it.evidenceIds }?.id
}
