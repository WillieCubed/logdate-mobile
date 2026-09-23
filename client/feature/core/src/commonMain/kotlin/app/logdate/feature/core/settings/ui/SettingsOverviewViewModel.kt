package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.identity.ObserveUserIdentityUseCase
import app.logdate.client.domain.identity.ResolvedUserIdentity
import app.logdate.client.domain.streak.ObserveStreakUseCase
import app.logdate.client.domain.streak.RefreshStreakUseCase
import app.logdate.client.domain.streak.StreakData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Supplies the identity card and streak badge at the top of the settings overview. */
class SettingsOverviewViewModel(
    observeUserIdentityUseCase: ObserveUserIdentityUseCase,
    observeStreakUseCase: ObserveStreakUseCase,
    private val refreshStreakUseCase: RefreshStreakUseCase,
) : ViewModel() {
    val resolvedIdentity: StateFlow<ResolvedUserIdentity> =
        observeUserIdentityUseCase()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                ResolvedUserIdentity(
                    displayName = "",
                    username = null,
                    profilePhotoUri = null,
                    bio = null,
                    birthday = null,
                    onboardedDate = null,
                    isAuthenticated = false,
                    cloudAccountId = null,
                ),
            )

    val streakData: StateFlow<StreakData> =
        observeStreakUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreakData())

    init {
        viewModelScope.launch { refreshStreakUseCase() }
    }
}
