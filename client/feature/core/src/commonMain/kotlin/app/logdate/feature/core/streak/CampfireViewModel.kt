package app.logdate.feature.core.streak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.streak.CampfireState
import app.logdate.client.domain.streak.FirePhase
import app.logdate.client.domain.streak.FireSize
import app.logdate.client.domain.streak.ObserveCampfireUseCase
import app.logdate.ui.streak.CampfirePhase
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.streak.CampfireSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the journaling campfire for any surface that draws it, or `null` when there is nothing
 * to draw: the campfire flag is off, streak tracking is off, or the fire could not be read.
 */
class CampfireViewModel(
    campfireFlow: Flow<CampfireState?>,
    campfireEnabledFlow: Flow<Boolean>,
) : ViewModel() {
    constructor(
        observeCampfire: ObserveCampfireUseCase,
        featureFlagStore: FeatureFlagStore,
    ) : this(
        campfireFlow = observeCampfire(),
        campfireEnabledFlow = featureFlagStore.observe(FeatureFlag.CAMPFIRE_STREAKS),
    )

    /**
     * Whether the campfire replaces the old streak counter, or `null` until the flag has been read.
     *
     * Surfaces show neither the campfire nor the old counter while this is `null`, so a flagged
     * screen never flashes the old streak UI before switching.
     */
    val isCampfireEnabled: StateFlow<Boolean?> =
        campfireEnabledFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val presentation: StateFlow<CampfirePresentation?> =
        campfireEnabledFlow
            .flatMapLatest { enabled ->
                if (enabled) campfireFlow.map { it?.toPresentation() } else flowOf(null)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = null,
            )
}

internal fun CampfireState.toPresentation(): CampfirePresentation =
    CampfirePresentation(
        phase =
            when (phase) {
                FirePhase.UNLIT -> CampfirePhase.UNLIT
                FirePhase.BURNING -> CampfirePhase.BURNING
                FirePhase.EMBERS -> CampfirePhase.EMBERS
                FirePhase.OUT -> CampfirePhase.OUT
            },
        loggedToday = loggedToday,
        runDays = runDays,
        size =
            when (size) {
                null -> null
                FireSize.SPARK -> CampfireSize.SPARK
                FireSize.SMALL -> CampfireSize.SMALL
                FireSize.CAMPFIRE -> CampfireSize.CAMPFIRE
                FireSize.BONFIRE -> CampfireSize.BONFIRE
                FireSize.BEACON -> CampfireSize.BEACON
            },
        longestRunDays = longestRunDays,
        totalDaysJournaled = totalDaysJournaled,
        isRekindled = isRekindled,
    )
