package app.logdate.client.domain.location.history

import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.location.PlaceVisit
import app.logdate.shared.model.location.VisitMemoryLink

class AssociateVisitMemories {
    operator fun invoke(
        visits: List<PlaceVisit>,
        notes: List<JournalNote>,
        links: List<VisitMemoryLink>,
    ): List<PlaceVisit> {
        val assignments = mutableMapOf<String, MutableList<String>>()
        notes.forEach { note ->
            val explicit = links.filter { it.noteId == note.uid.toString() }
            val candidates =
                if (explicit.isNotEmpty()) {
                    visits.filter { visit -> explicit.any { it.targetEvidenceId in visit.evidenceIds } }
                } else {
                    val location = note.location
                    val lat = location?.effectiveLatitude
                    val lon = location?.effectiveLongitude
                    if (lat == null || lon == null) {
                        emptyList()
                    } else {
                        visits.filter {
                            note.creationTimestamp >= it.start &&
                                note.creationTimestamp <= it.end &&
                                geographicDistance(it.latitude, it.longitude, lat, lon) <= 75
                        }
                    }
                }
            candidates.singleOrNull()?.let { visit -> assignments.getOrPut(visit.id) { mutableListOf() }.add(note.uid.toString()) }
        }
        return visits.map { it.copy(memoryIds = assignments[it.id].orEmpty()) }
    }
}
