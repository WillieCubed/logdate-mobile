package app.logdate.feature.library.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import app.logdate.core.data.location.LocationHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A view model that provides data for the location log screen.
 */
class LocationLogViewModel @Inject constructor(
    getLocationHistoryUseCase: GetLocationHistoryUseCase,
) : ViewModel() {

    /**
     * UI state that exposes a flow of past location logs
     */
    val uiState: StateFlow<PagingData<LocationLogUiState>> =
        getLocationHistoryUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), PagingData.empty())

    @OptIn(ExperimentalUuidApi::class)
    fun deleteLocationLog(uid: Uuid) {

    }
}

/**
 *
 */
class DeleteLocationLogUseCase @Inject constructor() {
    @OptIn(ExperimentalUuidApi::class)
    operator fun invoke(uid: Uuid) {

    }
}

/**
 * A use case for observing the user's location history.
 */
class GetLocationHistoryUseCase @Inject constructor(
    private val locationHistoryRepository: LocationHistoryRepository,
) {
    operator fun invoke(
        /**
         *
         */
        start: Instant = Instant.DISTANT_PAST,
        end: Instant = Instant.DISTANT_FUTURE,
        pageSize: Int = 20,
    ): Flow<PagingData<LocationLogUiState>> =
        Pager(
            config = PagingConfig(pageSize),
            pagingSourceFactory = {
                LocationHistoryPagingSource(
                    locationHistoryRepository,
                    LocationQueryData(start, end),
                )
            },
        )
            .flow
            .map { pagingData ->
                pagingData.map { location ->
                    LocationLogUiState(
                        // TODO: Properly paginate
                        records = listOf(
                            LocationLogEntryUiState(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                altitude = location.altitude.value,
                                altitudeUnits = location.altitude.units,
                                start = Clock.System.now(),
                                end = Clock.System.now(),
                            )
                        )
                    )
                }
            }

}

