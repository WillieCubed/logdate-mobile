package app.logdate.feature.location.timeline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.location.CaptureLocationForTimelineReviewUseCase
import app.logdate.client.domain.location.DeleteLocationRangeUseCase
import app.logdate.client.domain.location.LocationStop
import app.logdate.client.domain.location.ObserveLocationStopsUseCase
import app.logdate.client.domain.places.PlaceResolutionResult
import app.logdate.client.domain.places.ResolveLocationToPlaceUseCase
import app.logdate.client.domain.world.ObserveLocationUseCase
import app.logdate.feature.location.timeline.ui.model.CurrentLocationUiModel
import app.logdate.feature.location.timeline.ui.model.LocationLabelSource
import app.logdate.feature.location.timeline.ui.model.LocationStopUiModel
import app.logdate.feature.location.timeline.ui.model.LocationTimelineUiState
import app.logdate.shared.model.Location
import app.logdate.util.localTime
import app.logdate.util.toReadableDateShort
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class LocationTimelineViewModel(
    private val observeLocationUseCase: ObserveLocationUseCase,
    private val observeLocationStopsUseCase: ObserveLocationStopsUseCase,
    private val resolveLocationToPlaceUseCase: ResolveLocationToPlaceUseCase,
    private val deleteLocationRangeUseCase: DeleteLocationRangeUseCase,
    private val captureLocationForTimelineReviewUseCase: CaptureLocationForTimelineReviewUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LocationTimelineUiState>(LocationTimelineUiState.Loading)
    val uiState: StateFlow<LocationTimelineUiState> = _uiState.asStateFlow()

    init {
        captureLocationForTimelineReview()
        observeTimeline()
    }

    fun selectStop(stopId: String) {
        val state = _uiState.value
        if (state is LocationTimelineUiState.Success) {
            _uiState.value = state.copy(selectedStopId = stopId)
        }
    }

    fun deleteStop(stopId: String) {
        val state = _uiState.value as? LocationTimelineUiState.Success ?: return
        val stop = state.stops.firstOrNull { it.id == stopId } ?: return

        viewModelScope.launch {
            deleteLocationRangeUseCase(
                startTime = stop.startTime,
                endTime = stop.endTime + 1.milliseconds,
            ).onFailure { error ->
                Napier.e("Failed to delete location stop ${stop.id}", error)
            }
        }
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
        viewModelScope.launch {
            combine(
                observeLocationUseCase()
                    .mapLatest { it as Location? }
                    .catch { error ->
                        Napier.w("Failed to observe current location", error)
                        emit(null)
                    }.onStart { emit(null) },
                observeLocationStopsUseCase()
                    .catch { error ->
                        Napier.w("Failed to observe location stops", error)
                        emit(emptyList())
                    },
            ) { currentLocation, stops ->
                currentLocation to stops
            }.mapLatest { (currentLocation, stops) ->
                buildSuccessState(currentLocation, stops)
            }.catch { error ->
                Napier.e("Failed to build location timeline", error)
                emit(LocationTimelineUiState.Error(error.message ?: "Unable to load your location timeline."))
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private suspend fun buildSuccessState(
        currentLocation: Location?,
        stops: List<LocationStop>,
    ): LocationTimelineUiState {
        val mappedStops = stops.map { stop -> stop.toUiModel() }
        val mappedCurrentLocation = currentLocation?.toCurrentLocationUiModel()
        val existingSelection = (_uiState.value as? LocationTimelineUiState.Success)?.selectedStopId
        val selectedStopId =
            mappedStops.firstOrNull { it.id == existingSelection }?.id ?: mappedStops.firstOrNull()?.id

        return LocationTimelineUiState.Success(
            currentLocation = mappedCurrentLocation,
            stops = mappedStops,
            selectedStopId = selectedStopId,
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
                sourceLabel = "Saved"
                source = LocationLabelSource.USER_DEFINED
            }

            is PlaceResolutionResult.ExternalSuggestion -> {
                title = resolvedPlace.suggestion.name
                subtitle = resolvedPlace.suggestion.address
                sourceLabel = "Google"
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

    private fun formatCoordinates(
        latitude: Double,
        longitude: Double,
    ): String = "${formatCoordinateValue(latitude)}, ${formatCoordinateValue(longitude)}"
}
