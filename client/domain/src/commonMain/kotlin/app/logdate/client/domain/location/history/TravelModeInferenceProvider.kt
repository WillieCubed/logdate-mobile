package app.logdate.client.domain.location.history

import app.logdate.shared.model.location.LocationObservation
import app.logdate.shared.model.location.TravelMode

data class TravelModeCandidate(
    val mode: TravelMode,
    val confidence: Float,
    val provenance: String,
)

fun interface TravelModeInferenceProvider {
    fun infer(evidence: List<LocationObservation>): List<TravelModeCandidate>
}

class RecordedTravelModeInferenceProvider : TravelModeInferenceProvider {
    override fun infer(evidence: List<LocationObservation>): List<TravelModeCandidate> {
        val modes = evidence.map { it.activity }.filterNot { it == TravelMode.STILL || it == TravelMode.UNKNOWN }
        val mode =
            modes
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: return emptyList()
        return listOf(TravelModeCandidate(mode, modes.count { it == mode }.toFloat() / evidence.size, "recorded_activity"))
    }
}
