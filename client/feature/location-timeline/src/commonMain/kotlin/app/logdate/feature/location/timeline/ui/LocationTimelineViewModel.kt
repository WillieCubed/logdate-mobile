package app.logdate.feature.location.timeline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.location.CaptureLocationForTimelineReviewUseCase
import app.logdate.client.domain.location.DeleteLocationRangeUseCase
import app.logdate.client.domain.location.LocationMemoryPlace
import app.logdate.client.domain.location.LocationMemoryTimeFilter
import app.logdate.client.domain.location.LocationStop
import app.logdate.client.domain.location.ObserveLocationMemoryPlacesUseCase
import app.logdate.client.domain.location.ObserveLocationStopsUseCase
import app.logdate.client.domain.places.PlaceResolutionResult
import app.logdate.client.domain.places.ResolveLocationToPlaceUseCase
import app.logdate.client.domain.world.ObserveLocationUseCase
import app.logdate.client.repository.journals.NoteType
import app.logdate.feature.location.timeline.ui.model.CurrentLocationUiModel
import app.logdate.feature.location.timeline.ui.model.LocationLabelSource
import app.logdate.feature.location.timeline.ui.model.LocationMemoryKind
import app.logdate.feature.location.timeline.ui.model.LocationMemoryPreviewUiModel
import app.logdate.feature.location.timeline.ui.model.LocationPlaceUiModel
import app.logdate.feature.location.timeline.ui.model.LocationStopUiModel
import app.logdate.feature.location.timeline.ui.model.LocationTimelineUiState
import app.logdate.feature.location.timeline.ui.model.toLocationTimelineErrorUiState
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import app.logdate.util.localTime
import app.logdate.util.toReadableDateShort
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LocationTimelineViewModel(
    private val observeLocationUseCase: ObserveLocationUseCase,
    private val observeLocationStopsUseCase: ObserveLocationStopsUseCase,
    private val observeLocationMemoryPlacesUseCase: ObserveLocationMemoryPlacesUseCase,
    private val resolveLocationToPlaceUseCase: ResolveLocationToPlaceUseCase,
    private val deleteLocationRangeUseCase: DeleteLocationRangeUseCase,
    private val captureLocationForTimelineReviewUseCase: CaptureLocationForTimelineReviewUseCase,
) : ViewModel() {
    private sealed interface ObservationState<out T> {
        data class Value<T>(
            val value: T,
        ) : ObservationState<T>

        data class Failure(
            val throwable: Throwable,
        ) : ObservationState<Nothing>
    }

    private val _uiState = MutableStateFlow<LocationTimelineUiState>(LocationTimelineUiState.Loading)
    val uiState: StateFlow<LocationTimelineUiState> = _uiState.asStateFlow()
    private val selectedFilter = MutableStateFlow(LocationMemoryTimeFilter.Last30Days)
    private val visiblePlaceCount = MutableStateFlow(DEFAULT_PLACE_PAGE_SIZE)
    private var observeTimelineJob: Job? = null

    init {
        captureLocationForTimelineReview()
        observeTimeline()
    }

    fun selectPlace(placeId: String) {
        val state = _uiState.value as? LocationTimelineUiState.Success ?: return
        _uiState.value = state.copy(selectedPlaceId = placeId)
    }

    fun dismissPlaceDetail() {
        val state = _uiState.value as? LocationTimelineUiState.Success ?: return
        _uiState.value = state.copy(selectedPlaceId = null)
    }

    fun selectFilter(filter: LocationMemoryTimeFilter) {
        selectedFilter.value = filter
        visiblePlaceCount.value = DEFAULT_PLACE_PAGE_SIZE
    }

    fun loadMorePlaces() {
        val state = _uiState.value as? LocationTimelineUiState.Success ?: return
        if (!state.canLoadMorePlaces) {
            return
        }

        visiblePlaceCount.value += DEFAULT_PLACE_PAGE_SIZE
    }

    fun deleteStop(stopId: String) {
        val state = _uiState.value as? LocationTimelineUiState.Success ?: return
        val stop = state.recentStops.firstOrNull { it.id == stopId } ?: return

        viewModelScope.launch {
            deleteLocationRangeUseCase(
                startTime = stop.startTime,
                endTime = stop.endTime + 1.milliseconds,
            ).onFailure { error ->
                Napier.e("Failed to delete location stop ${stop.id}", error)
            }
        }
    }

    fun retry() {
        _uiState.value = LocationTimelineUiState.Loading
        observeTimeline()
    }

    private fun captureLocationForTimelineReview() {
        viewModelScope.launch {
            runCatching {
                captureLocationForTimelineReviewUseCase()
            }.onFailure { error ->
                Napier.w("Failed to capture location for timeline review", error)
            }
        }
    }

    private fun observeTimeline() {
        observeTimelineJob?.cancel()
        observeTimelineJob =
            viewModelScope.launch {
                combine(
                    observeCurrentLocationState(),
                    observeLocationStopsState(),
                    selectedFilter.flatMapLatest { filter ->
                        observeLocationMemoryPlacesUseCase(filter)
                            .catch { error ->
                                Napier.w("Failed to observe location memory places", error)
                                emit(emptyList())
                            }.mapLatest { places -> filter to places }
                    },
                    visiblePlaceCount,
                ) { currentLocation, stops, filterAndPlaces, visibleCount ->
                    buildUiState(
                        currentLocationState = currentLocation,
                        stopsState = stops,
                        selectedFilter = filterAndPlaces.first,
                        memoryPlaces = filterAndPlaces.second,
                        visibleCount = visibleCount,
                    )
                }.catch { error ->
                    Napier.e("Failed to build location timeline", error)
                    emit(LocationTimelineUiState.Error(error.toLocationTimelineErrorUiState()))
                }.collect { state ->
                    _uiState.value = state
                }
            }
    }

    private fun observeCurrentLocationState(): Flow<ObservationState<Location?>> =
        observeLocationUseCase()
            .mapLatest<_, ObservationState<Location?>> { ObservationState.Value(it as Location?) }
            .catch { error ->
                Napier.w("Failed to observe current location", error)
                emit(ObservationState.Failure(error))
            }.onStart { emit(ObservationState.Value(null)) }

    private fun observeLocationStopsState(): Flow<ObservationState<List<LocationStop>>> =
        observeLocationStopsUseCase()
            .mapLatest<_, ObservationState<List<LocationStop>>> { ObservationState.Value(it) }
            .catch { error ->
                Napier.w("Failed to observe location stops", error)
                emit(ObservationState.Failure(error))
            }.onStart { emit(ObservationState.Value(emptyList())) }

    private suspend fun buildUiState(
        currentLocationState: ObservationState<Location?>,
        stopsState: ObservationState<List<LocationStop>>,
        selectedFilter: LocationMemoryTimeFilter,
        memoryPlaces: List<LocationMemoryPlace>,
        visibleCount: Int,
    ): LocationTimelineUiState {
        val currentLocation = (currentLocationState as? ObservationState.Value)?.value
        val stops = (stopsState as? ObservationState.Value)?.value.orEmpty()

        if (currentLocation == null && stops.isEmpty() && memoryPlaces.isEmpty()) {
            val fatalError =
                when {
                    stopsState is ObservationState.Failure -> stopsState.throwable
                    currentLocationState is ObservationState.Failure -> currentLocationState.throwable
                    else -> null
                }

            if (fatalError != null) {
                return LocationTimelineUiState.Error(fatalError.toLocationTimelineErrorUiState())
            }
        }

        return buildSuccessState(
            currentLocation = currentLocation,
            stops = stops,
            memoryPlaces = memoryPlaces,
            selectedFilter = selectedFilter,
            visibleCount = visibleCount,
        )
    }

    private suspend fun buildSuccessState(
        currentLocation: Location?,
        stops: List<LocationStop>,
        memoryPlaces: List<LocationMemoryPlace>,
        selectedFilter: LocationMemoryTimeFilter,
        visibleCount: Int,
    ): LocationTimelineUiState {
        val mappedStops = stops.map { stop -> stop.toUiModel() }
        val mappedPlaces = memoryPlaces.map { place -> place.toUiModel(mappedStops) }
        val mappedCurrentLocation = currentLocation?.toCurrentLocationUiModel()
        val visiblePlaces = mappedPlaces.take(visibleCount.coerceAtLeast(DEFAULT_PLACE_PAGE_SIZE))
        val existingSelection = (_uiState.value as? LocationTimelineUiState.Success)?.selectedPlaceId
        val selectedPlaceId =
            visiblePlaces.firstOrNull { it.id == existingSelection }?.id ?: visiblePlaces.firstOrNull()?.id

        return LocationTimelineUiState.Success(
            currentLocation = mappedCurrentLocation,
            selectedFilter = selectedFilter,
            places = mappedPlaces,
            visiblePlaces = visiblePlaces,
            recentStops = mappedStops,
            selectedPlaceId = selectedPlaceId,
            canLoadMorePlaces = mappedPlaces.size > visiblePlaces.size,
        )
    }

    private suspend fun Location.toCurrentLocationUiModel(): CurrentLocationUiModel {
        val resolvedPlace = resolveLocationToPlaceUseCase(this)
        val title =
            when (resolvedPlace) {
                is PlaceResolutionResult.UserDefinedPlace -> resolvedPlace.place.name
                is PlaceResolutionResult.ExternalSuggestion -> resolvedPlace.suggestion.name
                is PlaceResolutionResult.UnknownLocation -> "Current location"
            }
        val subtitle =
            when (resolvedPlace) {
                is PlaceResolutionResult.ExternalSuggestion -> resolvedPlace.suggestion.address
                is PlaceResolutionResult.UserDefinedPlace -> formatCoordinates(latitude, longitude)
                is PlaceResolutionResult.UnknownLocation -> formatCoordinates(latitude, longitude)
            }

        return CurrentLocationUiModel(
            title = title,
            subtitle = subtitle,
            latitude = latitude,
            longitude = longitude,
        )
    }

    private suspend fun LocationStop.toUiModel(): LocationStopUiModel {
        val resolvedPlace = resolveLocationToPlaceUseCase(location)
        val title: String
        val subtitle: String
        val sourceLabel: String
        val source: LocationLabelSource

        when (resolvedPlace) {
            is PlaceResolutionResult.UserDefinedPlace -> {
                title = resolvedPlace.place.name
                subtitle = formatCoordinates(location.latitude, location.longitude)
                sourceLabel = "Saved place"
                source = LocationLabelSource.USER_DEFINED
            }

            is PlaceResolutionResult.ExternalSuggestion -> {
                title = resolvedPlace.suggestion.name
                subtitle = resolvedPlace.suggestion.address
                sourceLabel = "Google Places"
                source = LocationLabelSource.GOOGLE_PLACES
            }

            is PlaceResolutionResult.UnknownLocation -> {
                title = "Pinned location"
                subtitle = formatCoordinates(location.latitude, location.longitude)
                sourceLabel = "Coordinates"
                source = LocationLabelSource.COORDINATES
            }
        }

        return LocationStopUiModel(
            id = id,
            title = title,
            subtitle = subtitle,
            latitude = location.latitude,
            longitude = location.longitude,
            startedAt = startTime.localTime,
            endedAt = endTime.localTime,
            timeRange = formatTimeRange(startTime, endTime),
            duration = formatDuration(duration),
            sourceLabel = sourceLabel,
            source = source,
            sampleCount = sampleCount,
            startTime = startTime,
            endTime = endTime,
        )
    }

    private suspend fun LocationMemoryPlace.toUiModel(mappedStops: List<LocationStopUiModel>): LocationPlaceUiModel {
        val resolvedPlace =
            if (semanticName.isNullOrBlank()) {
                resolveLocationToPlaceUseCase(
                    Location(
                        latitude = latitude,
                        longitude = longitude,
                        altitude = LocationAltitude(0.0, AltitudeUnit.METERS),
                    ),
                )
            } else {
                null
            }

        val title: String
        val subtitle: String
        val sourceLabel: String
        val source: LocationLabelSource

        when {
            !semanticName.isNullOrBlank() -> {
                title = semanticName.orEmpty()
                subtitle = formatCoordinates(latitude, longitude)
                sourceLabel = "Place"
                source = LocationLabelSource.USER_DEFINED
            }

            resolvedPlace is PlaceResolutionResult.UserDefinedPlace -> {
                title = resolvedPlace.place.name
                subtitle = formatCoordinates(latitude, longitude)
                sourceLabel = "Saved place"
                source = LocationLabelSource.USER_DEFINED
            }

            resolvedPlace is PlaceResolutionResult.ExternalSuggestion -> {
                title = resolvedPlace.suggestion.name
                subtitle = resolvedPlace.suggestion.address
                sourceLabel = "Nearby place"
                source = LocationLabelSource.GOOGLE_PLACES
            }

            else -> {
                title = "Pinned memory"
                subtitle = formatCoordinates(latitude, longitude)
                sourceLabel = "Coordinates"
                source = LocationLabelSource.COORDINATES
            }
        }

        return LocationPlaceUiModel(
            id = id,
            title = title,
            subtitle = subtitle,
            latitude = latitude,
            longitude = longitude,
            lastVisitedLabel = lastVisitedAt.toReadableDateShort(),
            memoryCount = memoryCount,
            sourceLabel = sourceLabel,
            source = source,
            memories =
                memories.map { memory ->
                    LocationMemoryPreviewUiModel(
                        noteId = memory.noteId,
                        title = memory.preview,
                        subtitle = memory.timestamp.toReadableDateShort(),
                        timestamp = memory.timestamp,
                        latitude = memory.latitude,
                        longitude = memory.longitude,
                        kind = memory.type.toUiKind(),
                    )
                },
            relatedStops =
                mappedStops.filter { stop ->
                    calculateDistanceMeters(
                        latitude,
                        longitude,
                        stop.latitude,
                        stop.longitude,
                    ) <= PLACE_TO_STOP_MATCH_RADIUS_METERS
                },
        )
    }

    private fun NoteType.toUiKind(): LocationMemoryKind =
        when (this) {
            NoteType.TEXT -> LocationMemoryKind.TEXT
            NoteType.IMAGE -> LocationMemoryKind.PHOTO
            NoteType.AUDIO -> LocationMemoryKind.AUDIO
            NoteType.VIDEO -> LocationMemoryKind.VIDEO
            NoteType.LOCATION -> LocationMemoryKind.TEXT
        }

    private fun formatTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): String {
        val sameDay = startTime.toReadableDateShort() == endTime.toReadableDateShort()
        return if (sameDay) {
            "${startTime.localTime} - ${endTime.localTime}"
        } else {
            "${startTime.toReadableDateShort()}, ${startTime.localTime} - ${endTime.toReadableDateShort()}, ${endTime.localTime}"
        }
    }

    private fun formatDuration(duration: Duration): String =
        when {
            duration >= 1.days -> "${duration.inWholeDays}d ${duration.inWholeHours % 24}h"
            duration >= 1.hours -> "${duration.inWholeHours}h ${duration.inWholeMinutes % 60}m"
            duration >= 1.minutes -> "${duration.inWholeMinutes}m"
            else -> "<1m"
        }

    private fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val earthRadius = 6371000.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1 * PI / 180) * cos(lat2 * PI / 180) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun formatCoordinates(
        latitude: Double,
        longitude: Double,
    ): String = "${formatCoordinateValue(latitude)}, ${formatCoordinateValue(longitude)}"

    private companion object {
        const val DEFAULT_PLACE_PAGE_SIZE = 12
        const val PLACE_TO_STOP_MATCH_RADIUS_METERS = 160.0
    }
}
