package app.logdate.feature.core.settings.ui.labs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One unreleased feature on the Labs page, and whether it is on for this installation. */
data class LabsFlag(
    val flag: FeatureFlag,
    val enabled: Boolean,
)

/** What the Labs page shows. */
sealed interface LabsUiState {
    /** The stored flag values have not been read yet. */
    data object Loading : LabsUiState

    /** Every flag Labs offers, in declaration order; empty when no feature is unreleased. */
    data class Ready(
        val flags: List<LabsFlag>,
    ) : LabsUiState
}

/**
 * Lists the features that have not launched and turns them on or off for this installation.
 *
 * Release builds have no other switch for an unfinished feature, so this is how a tester tries one
 * before it ships.
 *
 * @param flags the flags to offer; [labsFlags] everywhere except tests
 */
class LabsSettingsViewModel(
    private val featureFlagStore: FeatureFlagStore,
    flags: List<FeatureFlag> = labsFlags(),
) : ViewModel() {
    val uiState: StateFlow<LabsUiState> =
        observe(flags).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LabsUiState.Loading)

    /** Turns [flag] on or off for this installation. */
    fun setEnabled(
        flag: FeatureFlag,
        enabled: Boolean,
    ) {
        viewModelScope.launch { featureFlagStore.setEnabled(flag, enabled) }
    }

    private fun observe(flags: List<FeatureFlag>): Flow<LabsUiState> {
        if (flags.isEmpty()) return flowOf(LabsUiState.Ready(emptyList()))
        val rows = flags.map { flag -> featureFlagStore.observe(flag).map { enabled -> LabsFlag(flag, enabled) } }
        return combine(rows) { values -> LabsUiState.Ready(values.toList()) }
    }
}

/**
 * The flags Labs offers: off by default, allowed to turn on, and given wording in [labsCopy].
 *
 * A flag deferred from launch is left out: the store reports it off whatever is saved, so its
 * switch would do nothing. A flag without Labs wording is left out too, so a feature that is still
 * being built behind its own flag does not appear here until someone decides to offer it.
 */
fun labsFlags(entries: List<FeatureFlag> = FeatureFlag.entries): List<FeatureFlag> =
    entries.filter { it.availableForLaunch && !it.defaultEnabled && it.labsCopy() != null }
