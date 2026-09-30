package app.logdate.feature.location.timeline.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.location.history.LocationHistoryService
import app.logdate.client.domain.location.history.LocationHistorySnapshot
import app.logdate.client.domain.location.history.SuggestNearbyHistoryPlacesUseCase
import app.logdate.client.domain.places.PlaceResolutionCache
import app.logdate.client.domain.places.PlaceResolutionResult
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import app.logdate.shared.model.location.HistoryField
import app.logdate.shared.model.location.LocationDayItem
import app.logdate.shared.model.location.PlaceVisit
import app.logdate.shared.model.location.SemanticPlace
import app.logdate.shared.model.location.VisitMemoryContext
import app.logdate.util.toReadableDateShort
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class HumanLocationHistoryViewModel(
    private val history: LocationHistoryService,
    private val settings: LocationTrackingSettingsRepository,
    private val placeResolution: PlaceResolutionCache,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val nearbyPlaces: SuggestNearbyHistoryPlacesUseCase? = null,
) : ViewModel() {
    private val zone = TimeZone.currentSystemDefault()
    var date: LocalDate =
        savedState.get<String>("historyDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: Clock.System
                .now()
                .toLocalDateTime(zone)
                .date
        private set
    private val mutableState =
        MutableStateFlow(
            HumanLocationHistoryState(
                "Today",
                recoveryMessage = "Loading your day…",
                selectedItemId = savedState["historySelection"],
                detailVisible = savedState["historyDetail"] ?: false,
                tab = HistoryTab.entries.firstOrNull { it.name == savedState.get<String>("historyTab") } ?: HistoryTab.Day,
            ),
        )
    val state = mutableState.asStateFlow()
    val snapshot = MutableStateFlow<LocationHistorySnapshot?>(null)
    val recordingEnabled = MutableStateFlow(false)
    val completeDayEnabled = MutableStateFlow(false)
    private var observeJob: Job? = null
    private var replayJob: Job? = null
    private var failedAction: (suspend () -> Unit)? = null
    private var rangeDays = 30
    private var selectedSource: String? = savedState["historySource"]
    private val resolving = mutableSetOf<String>()
    private val resolvedExternalPlaces = mutableMapOf<String, String>()

    init {
        observe()
    }

    fun select(
        id: String,
        detail: Boolean = false,
    ) {
        pauseReplay()
        mutableState.update { it.copy(selectedItemId = id, detailVisible = detail, selectionRevision = it.selectionRevision + 1) }
        savedState["historySelection"] = id
        savedState["historySelectionEvidence"] =
            snapshot.value
                ?.items
                ?.firstOrNull { it.id == id }
                ?.evidenceIds
                ?.firstOrNull()
        savedState["historyDetail"] = detail
    }

    fun dismiss() {
        mutableState.update { it.copy(detailVisible = false) }
        savedState["historyDetail"] = false
    }

    fun changeDay(offset: Int) = setDate(date.plus(DatePeriod(days = offset)))

    fun setDate(
        value: LocalDate,
        tab: HistoryTab = HistoryTab.Day,
    ) {
        pauseReplay()
        date = value
        savedState["historyDate"] = value.toString()
        savedState["historySelection"] = null
        savedState["historyDetail"] = false
        savedState["historyTab"] = tab.name
        mutableState.update { it.copy(selectedItemId = null, detailVisible = false, tab = tab) }
        observe()
    }

    fun setTab(tab: HistoryTab) {
        savedState["historyTab"] = tab.name
        savedState["historySelection"] = null
        savedState["historyDetail"] = false
        pauseReplay()
        mutableState.update { it.copy(tab = tab, detailVisible = false, selectedItemId = null) }
        observe()
    }

    fun setQuery(query: String) = mutableState.update { it.copy(placesQuery = query) }

    fun setMapVisible(visible: Boolean) = mutableState.update { it.copy(placesMapVisible = visible) }

    fun cycleRange() {
        rangeDays = if (rangeDays == 30) 90 else 30
        observe()
    }

    fun selectSource(id: String) {
        selectedSource = id
        savedState["historySource"] = id
        observe()
    }

    fun replay(play: Boolean) {
        pauseReplay()
        if (!play) return
        if (state.value.selectedItemId ==
            state.value.items
                .lastOrNull()
                ?.id
        ) {
            mutableState.update { it.copy(selectedItemId = null) }
        }
        mutableState.update { it.copy(replayPlaying = true) }
        replayJob =
            viewModelScope.launch {
                do {
                    val next = if (state.value.selectedItemId == null) state.value.items.firstOrNull() else state.value.adjacentItem(1)
                    if (next == null) break
                    mutableState.update {
                        it.copy(selectedItemId = next.id, detailVisible = false, selectionRevision = it.selectionRevision + 1)
                    }
                    delay(2500)
                } while (state.value.replayPlaying)
                mutableState.update { it.copy(replayPlaying = false) }
            }
    }

    fun pauseReplay() {
        replayJob?.cancel()
        mutableState.update { it.copy(replayPlaying = false) }
    }

    suspend fun nearbyPlacesFor(item: LocationDayItem?): List<SemanticPlace> {
        val visit = item as? PlaceVisit ?: return emptyList()
        return nearbyPlaces?.invoke(visit.latitude, visit.longitude, snapshot.value?.places.orEmpty()).orEmpty()
    }

    fun contextFor(id: String): VisitMemoryContext? =
        snapshot.value?.items?.filterIsInstance<PlaceVisit>()?.firstOrNull { it.id == id }?.let {
            VisitMemoryContext(it.evidenceIds.first(), it.place?.id, it.place?.name, it.latitude, it.longitude, it.start)
        }

    fun delete(id: String) =
        mutate {
            snapshot.value
                ?.items
                ?.firstOrNull { it.id == id }
                ?.let { history.deleteVisit(it) }
            dismiss()
        }

    fun linkMemory(
        visitId: String,
        noteId: String,
    ) = mutate {
        anchor(visitId)?.let { history.linkMemory(it, noteId) }
    }

    fun changeActivity(
        id: String,
        mode: String,
    ) = mutate { anchor(id)?.let { history.correct(it, HistoryField.ACTIVITY, mode) } }

    fun namePlace(
        id: String,
        name: String,
    ) = mutate {
        val visit =
            snapshot.value
                ?.items
                ?.filterIsInstance<PlaceVisit>()
                ?.firstOrNull { it.id == id } ?: return@mutate
        val place =
            SemanticPlace(visit.place?.id ?: Uuid.random().toString(), name.trim(), visit.latitude, visit.longitude, userConfirmed = true)
        history.savePlace(place)
        history.correct(visit.evidenceIds.first(), HistoryField.PLACE, place.id)
    }

    fun changeTime(
        id: String,
        start: Instant,
        end: Instant,
    ) = mutate {
        require(end >= start) { "The end must be after the start." }
        anchor(id)?.let {
            history.correct(it, HistoryField.START, start.toString())
            history.correct(it, HistoryField.END, end.toString())
        }
    }

    fun addVisit(
        start: Instant,
        end: Instant,
        place: SemanticPlace,
    ) = mutate { history.addVisit(start, end, place) }

    fun enableRecording() =
        mutate {
            val previous = settings.getSettings()
            settings.updateSettings(previous.copy(backgroundTrackingEnabled = true, captureMode = LocationCaptureMode.ACTIVE))
        }

    fun retry() {
        failedAction?.let {
            mutate(it)
            return
        }
        resolving.clear()
        observe()
    }

    fun choosePlace(
        id: String,
        place: SemanticPlace,
    ) = mutate {
        history.savePlace(place.copy(userConfirmed = true))
        anchor(id)?.let { history.correct(it, HistoryField.PLACE, place.id) }
    }

    fun mergePlace(
        from: String,
        into: SemanticPlace,
    ) = mutate { history.mergePlaces(from, into) }

    fun resolveConflict(
        evidenceId: String,
        field: HistoryField,
        value: String,
    ) = mutate {
        history.correct(evidenceId, field, value)
    }

    fun pauseRecording() = mutate { settings.setBackgroundTrackingEnabled(false) }

    private fun anchor(id: String) =
        snapshot.value
            ?.items
            ?.firstOrNull { it.id == id }
            ?.evidenceIds
            ?.firstOrNull()

    private fun mutate(action: suspend () -> Unit) {
        if (failedAction != null) mutableState.update { it.copy(recoveryMessage = null, recoveryActionLabel = null) }
        failedAction = null
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Napier.w("Location history action failed", error)
                failedAction = action
                mutableState.update {
                    it.copy(recoveryMessage = "That change could not be saved. Try again.", recoveryActionLabel = "Try again")
                }
            }
        }
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob =
            viewModelScope.launch {
                try {
                    val first = if (state.value.tab == HistoryTab.Places) date.minus(DatePeriod(days = rangeDays - 1)) else date
                    combine(history.observeRange(first, date, selectedSource, zone), settings.observeSettings()) { data, tracking ->
                        data to
                            tracking
                    }.collect { (data, tracking) ->
                        applySnapshot(data, tracking)
                        enrich(data)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Napier.w("Could not read location history", error)
                    mutableState.update {
                        it.copy(
                            recoveryMessage = "Your history couldn’t be loaded. Try again.",
                            recoveryActionLabel = "Try again",
                        )
                    }
                }
            }
    }

    private fun selectedDateLabel(): String {
        val label = date.atStartOfDayIn(zone).toReadableDateShort()
        return if (date.year ==
            Clock.System
                .now()
                .toLocalDateTime(zone)
                .year
        ) {
            label
        } else {
            "$label, ${date.year}"
        }
    }

    private fun applySnapshot(
        data: LocationHistorySnapshot,
        tracking: app.logdate.client.location.settings.LocationTrackingSettings,
    ) {
        val selection =
            remapHistorySelection(
                state.value.selectedItemId,
                snapshot.value?.items.orEmpty(),
                data.items,
                savedState["historySelectionEvidence"],
            )
        snapshot.value = data
        savedState["historySelection"] = selection
        recordingEnabled.value = tracking.backgroundTrackingEnabled
        completeDayEnabled.value = tracking.backgroundTrackingEnabled && tracking.captureMode == LocationCaptureMode.ACTIVE
        mutableState.update { current ->
            current.copy(
                selectedItemId = selection,
                detailVisible = current.detailVisible && selection != null,
                dateLabel = selectedDateLabel(),
                daySummary =
                    data.items.filterIsInstance<PlaceVisit>().size.let {
                        "$it ${if (it == 1) "visit" else "visits"} in your day"
                    },
                items = data.items.map { it.toHistoryUi(data.notes) },
                places = data.placeRows(),
                placesFilterLabel = "$rangeDays days",
                recoveryActionLabel =
                    when {
                        failedAction != null -> "Try again"
                        data.failedSections.isNotEmpty() -> "Try again"
                        data.conflicts.isNotEmpty() -> "Review changes"
                        else -> null
                    },
                recoveryMessage =
                    when {
                        failedAction != null -> "That change could not be saved. Try again."
                        data.failedSections.isNotEmpty() ->
                            "Some details couldn’t be loaded. Your available history is still here."
                        data.conflicts.isNotEmpty() ->
                            "Some changes differ across your devices. Tap Review changes to choose what to keep."
                        !tracking.backgroundTrackingEnabled && tracking.captureMode == LocationCaptureMode.PASSIVE ->
                            "Location history is off. You can still browse memories with a saved place."
                        !tracking.backgroundTrackingEnabled -> "Location history is paused. Your saved days are still here."
                        else -> null
                    },
            )
        }
    }

    private fun enrich(data: LocationHistorySnapshot) {
        data.places.forEach { place -> place.externalId?.let { resolvedExternalPlaces.getOrPut(it) { place.id } } }
        data.items.filterIsInstance<PlaceVisit>().filter { it.place == null }.take(20).forEach { visit ->
            if (!resolving.add(visit.id)) return@forEach
            viewModelScope.launch {
                try {
                    val result =
                        placeResolution.resolve(
                            Location(visit.latitude, visit.longitude, LocationAltitude(0.0, AltitudeUnit.METERS)),
                        )
                    if (result is PlaceResolutionResult.UserDefinedPlace && data.places.any { it.id == result.place.uid.toString() }) {
                        return@launch
                    }
                    val name =
                        when (result) {
                            is PlaceResolutionResult.UserDefinedPlace -> result.place.name
                            is PlaceResolutionResult.ExternalSuggestion -> "Near ${result.suggestion.name}"
                            is PlaceResolutionResult.CoarseLocation ->
                                result.address.thoroughfare?.let { "Near $it" }
                                    ?: result.address.locality?.let { "Somewhere in $it" }
                            else -> null
                        }
                    if (name != null) {
                        val place =
                            when (result) {
                                is PlaceResolutionResult.UserDefinedPlace ->
                                    SemanticPlace(
                                        result.place.uid.toString(),
                                        result.place.name,
                                        result.place.latitude,
                                        result.place.longitude,
                                        userConfirmed = true,
                                    )
                                is PlaceResolutionResult.ExternalSuggestion ->
                                    SemanticPlace(
                                        result.suggestion.externalId?.let {
                                            resolvedExternalPlaces.getOrPut(
                                                it,
                                            ) { Uuid.random().toString() }
                                        }
                                            ?: "evidence:${visit.evidenceIds.first()}",
                                        name,
                                        result.suggestion.latitude,
                                        result.suggestion.longitude,
                                        locality = result.suggestion.address,
                                        externalId = result.suggestion.externalId,
                                    )
                                else -> SemanticPlace("evidence:${visit.evidenceIds.first()}", name, visit.latitude, visit.longitude)
                            }
                        history.savePlace(place)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Napier.w("Place name unavailable; keeping the visit", error)
                }
            }
        }
    }
}
