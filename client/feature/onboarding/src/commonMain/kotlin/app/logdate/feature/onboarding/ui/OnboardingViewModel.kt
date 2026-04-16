package app.logdate.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.billing.model.LogDateBackupPlanOption
import app.logdate.client.domain.dayboundary.DayBoundarySettingsRepository
import app.logdate.client.domain.dayboundary.HealthConnectStatus
import app.logdate.client.domain.dayboundary.ObserveHealthConnectStatusUseCase
import app.logdate.client.domain.identity.ObserveUserIdentityUseCase
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.streak.RefreshStreakUseCase
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.feature.onboarding.flow.OnboardingDeviceState
import app.logdate.feature.onboarding.flow.OnboardingDeviceStateRepository
import app.logdate.feature.onboarding.flow.OnboardingEntryMode
import app.logdate.feature.onboarding.flow.OnboardingProgressSnapshot
import app.logdate.feature.onboarding.flow.OnboardingStep
import app.logdate.feature.onboarding.flow.canCompleteOnboarding
import app.logdate.feature.onboarding.flow.firstIncompleteRequiredOnboardingStep
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * A view model for the onboarding flow.
 */
class OnboardingViewModel(
    private val journalNotesRepository: JournalNotesRepository,
    private val userStateRepository: UserStateRepository,
    private val memoriesSettingsRepository: MemoriesSettingsRepository,
    private val locationTrackingSettingsRepository: LocationTrackingSettingsRepository,
    private val dayBoundarySettingsRepository: DayBoundarySettingsRepository,
    private val observeHealthConnectStatus: ObserveHealthConnectStatusUseCase,
    observeUserIdentity: ObserveUserIdentityUseCase,
    private val onboardingDeviceStateRepository: OnboardingDeviceStateRepository,
    private val refreshStreakUseCase: RefreshStreakUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    private val _healthConnectStatus = MutableStateFlow(HealthConnectStatus.CHECKING)
    private var healthStatusJob: Job? = null

    val uiState: StateFlow<OnboardingUiState> =
        _uiState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), OnboardingUiState())

    val healthConnectStatus: StateFlow<HealthConnectStatus> = _healthConnectStatus

    val activeEntryMode: StateFlow<OnboardingEntryMode> =
        onboardingDeviceStateRepository.deviceState
            .map { it.activeEntryMode }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = onboardingDeviceStateRepository.deviceState.value.activeEntryMode,
            )

    val progressSnapshot: StateFlow<OnboardingProgressSnapshot> =
        combine(
            observeUserIdentity(),
            combine(
                combine(
                    onboardingDeviceStateRepository.deviceState,
                    healthConnectStatus,
                    memoriesSettingsRepository.observeSettings(),
                    locationTrackingSettingsRepository.observeSettings(),
                ) { deviceState, healthStatus, memoriesSettings, locationSettings ->
                    PartialOnboardingProgressInputs(
                        deviceState = deviceState,
                        healthStatus = healthStatus,
                        recommendationsEnabled = memoriesSettings.contextualRecommendationsEnabled,
                        locationTrackingEnabled = locationSettings.backgroundTrackingEnabled,
                    )
                },
                dayBoundarySettingsRepository.observeSettings(),
            ) { partialInputs, dayBoundarySettings ->
                OnboardingProgressInputs(
                    deviceState = partialInputs.deviceState,
                    healthStatus = partialInputs.healthStatus,
                    recommendationsEnabled = partialInputs.recommendationsEnabled,
                    locationTrackingEnabled = partialInputs.locationTrackingEnabled,
                    sleepBasedDayBoundariesEnabled = dayBoundarySettings.sleepBasedBoundariesEnabled,
                )
            },
        ) { identity, inputs ->
            OnboardingProgressSnapshot(
                hasPersonalIntro = identity.displayName.isNotBlank() && !identity.bio.isNullOrBlank(),
                hasBirthday = identity.birthday != null,
                hasCloudAccount =
                    identity.isAuthenticated ||
                        identity.cloudAccountId != null ||
                        !identity.username.isNullOrBlank(),
                recommendationsHandledOnThisDevice = inputs.deviceState.recommendationsHandledOnThisDevice,
                contextualRecommendationsEnabled = inputs.recommendationsEnabled,
                dayBoundariesHandledOnThisDevice = inputs.deviceState.dayBoundariesHandledOnThisDevice,
                sleepBasedDayBoundariesEnabled = inputs.sleepBasedDayBoundariesEnabled,
                locationHandledOnThisDevice = inputs.deviceState.locationHandledOnThisDevice,
                locationTrackingEnabled = inputs.locationTrackingEnabled,
                notificationsHandledOnThisDevice = inputs.deviceState.notificationsHandledOnThisDevice,
                healthConnectStatus = inputs.healthStatus,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue =
                OnboardingProgressSnapshot(
                    recommendationsHandledOnThisDevice =
                        onboardingDeviceStateRepository.deviceState.value.recommendationsHandledOnThisDevice,
                    dayBoundariesHandledOnThisDevice =
                        onboardingDeviceStateRepository.deviceState.value.dayBoundariesHandledOnThisDevice,
                    locationHandledOnThisDevice =
                        onboardingDeviceStateRepository.deviceState.value.locationHandledOnThisDevice,
                    notificationsHandledOnThisDevice =
                        onboardingDeviceStateRepository.deviceState.value.notificationsHandledOnThisDevice,
                    healthConnectStatus = healthConnectStatus.value,
                ),
        )

    /**
     * Whether contextual recommendations are currently enabled.
     *
     * Used by the notifications screen to adapt its messaging.
     */
    val recommendationsEnabled: StateFlow<Boolean> =
        memoriesSettingsRepository
            .observeSettings()
            .map { it.contextualRecommendationsEnabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    init {
        refreshHealthStatus()
    }

    /**
     * Creates a new journal entry.
     */
    fun addEntry(newEntryData: NewEntryData) {
        viewModelScope.launch {
            // TODO: Support non-text entry types
            journalNotesRepository.create(
                JournalNote.Text(
                    content = newEntryData.textContent,
                    creationTimestamp = newEntryData.timestamp,
                    lastUpdated = newEntryData.timestamp,
                ),
            )
            _uiState.update {
                it.copy(entrySubmitted = true)
            }
            Napier.d(
                tag = "OnboardingViewModel",
                message = "Successfully added entry: $newEntryData",
            )
        }
    }

    /**
     * Sends a test notification to the user.
     *
     * This should be used to confirm how users will be notified for journaling reminders.
     */
    fun sendTestNotification() {
        viewModelScope.launch {
//            notifier.sendNotification(SystemNotification(
//                label = "",
//                bodyContent = "It's time to write.",
//            ))
        }
    }

    fun updateBirthday(birthday: Instant) {
        viewModelScope.launch {
            persistBirthday(birthday)
        }
    }

    suspend fun persistBirthday(birthday: Instant): Result<Unit> =
        runCatching {
            userStateRepository.setBirthday(birthday)
        }

    /**
     * Enables or disables contextual recommendations.
     */
    fun setRecommendationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            persistRecommendationsEnabled(enabled)
        }
    }

    suspend fun persistRecommendationsEnabled(enabled: Boolean): Result<Unit> =
        runCatching {
            memoriesSettingsRepository.setContextualRecommendationsEnabled(enabled)
        }

    /**
     * Enables background location tracking after the user opts in.
     */
    fun enableLocationTracking() {
        viewModelScope.launch {
            persistLocationTrackingEnabled()
        }
    }

    suspend fun persistLocationTrackingEnabled(): Result<Unit> =
        runCatching {
            locationTrackingSettingsRepository.setBackgroundTrackingEnabled(true)
        }

    fun enableSleepBasedDayBoundaries() {
        viewModelScope.launch {
            persistSleepBasedDayBoundariesEnabled(enabled = true)
        }
    }

    fun disableSleepBasedDayBoundaries() {
        viewModelScope.launch {
            persistSleepBasedDayBoundariesEnabled(enabled = false)
        }
    }

    suspend fun persistSleepBasedDayBoundariesEnabled(enabled: Boolean): Result<Unit> =
        runCatching {
            dayBoundarySettingsRepository.setSleepBasedBoundariesEnabled(enabled)
        }

    fun refreshHealthStatus() {
        healthStatusJob?.cancel()
        healthStatusJob =
            viewModelScope.launch {
                observeHealthConnectStatus().collect { status ->
                    _healthConnectStatus.value = status
                }
            }
    }

    /**
     * Selects a backup plan option.
     */
    fun selectPlan(option: LogDateBackupPlanOption) {
        _uiState.update {
            it.copy(planOption = option)
        }
    }

    fun capturePhoto() {
    }

    /**
     * Marks the onboarding flow as complete.
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            completeOnboardingIfEligible()
        }
    }

    suspend fun markNotificationsHandled(): Result<Unit> =
        runCatching {
            onboardingDeviceStateRepository.markNotificationsHandled()
        }

    suspend fun markRecommendationsHandled(): Result<Unit> =
        runCatching {
            onboardingDeviceStateRepository.markRecommendationsHandled()
        }

    suspend fun markDayBoundariesHandled(): Result<Unit> =
        runCatching {
            onboardingDeviceStateRepository.markDayBoundariesHandled()
        }

    suspend fun markLocationHandled(): Result<Unit> =
        runCatching {
            onboardingDeviceStateRepository.markLocationHandled()
        }

    suspend fun setActiveEntryMode(entryMode: OnboardingEntryMode): Result<Unit> =
        runCatching {
            onboardingDeviceStateRepository.setActiveEntryMode(entryMode)
        }

    suspend fun completeOnboardingIfEligible(): Result<Unit> =
        runCatching {
            require(progressSnapshot.value.canCompleteOnboarding()) {
                "Required onboarding steps are still incomplete"
            }
            userStateRepository.setIsOnboardingComplete(true)
            refreshStreakUseCase()
        }

    fun firstIncompleteRequiredOnboardingStep(): OnboardingStep? = progressSnapshot.value.firstIncompleteRequiredOnboardingStep()

    override fun onCleared() {
        healthStatusJob?.cancel()
        super.onCleared()
    }

    private data class OnboardingProgressInputs(
        val deviceState: OnboardingDeviceState,
        val healthStatus: HealthConnectStatus,
        val recommendationsEnabled: Boolean,
        val locationTrackingEnabled: Boolean,
        val sleepBasedDayBoundariesEnabled: Boolean,
    )

    private data class PartialOnboardingProgressInputs(
        val deviceState: OnboardingDeviceState,
        val healthStatus: HealthConnectStatus,
        val recommendationsEnabled: Boolean,
        val locationTrackingEnabled: Boolean,
    )
}
