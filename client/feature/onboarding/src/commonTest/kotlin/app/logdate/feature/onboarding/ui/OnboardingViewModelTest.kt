package app.logdate.feature.onboarding.ui

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.domain.dayboundary.DayBoundarySettings
import app.logdate.client.domain.dayboundary.DayBoundarySettingsRepository
import app.logdate.client.domain.dayboundary.HealthConnectStatus
import app.logdate.client.domain.dayboundary.ObserveHealthConnectStatusUseCase
import app.logdate.client.domain.identity.ObserveUserIdentityUseCase
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
import app.logdate.client.domain.streak.CalculateStreakUseCase
import app.logdate.client.domain.streak.RefreshStreakUseCase
import app.logdate.client.health.LocalFirstHealthRepository
import app.logdate.client.health.model.DayBounds
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.repository.account.AccountRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.client.repository.streak.StreakSettingsRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.feature.onboarding.flow.OnboardingDeviceState
import app.logdate.feature.onboarding.flow.OnboardingDeviceStateRepository
import app.logdate.feature.onboarding.flow.OnboardingEntryMode
import app.logdate.feature.onboarding.flow.OnboardingStep
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.profile.LogDateProfile
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeNotesRepository: FakeJournalNotesRepository
    private lateinit var fakeUserStateRepository: FakeUserStateRepository
    private lateinit var fakeMemoriesSettingsRepository: FakeMemoriesSettingsRepository
    private lateinit var fakeLocationSettingsRepository: FakeLocationTrackingSettingsRepository
    private lateinit var fakeDayBoundarySettingsRepository: FakeDayBoundarySettingsRepository
    private lateinit var fakeHealthRepository: FakeLocalFirstHealthRepository
    private lateinit var fakeProfileRepository: FakeProfileRepository
    private lateinit var fakeAccountRepository: FakeAccountRepository
    private lateinit var fakeSessionStorage: FakeSessionStorage
    private lateinit var fakeStreakSettingsRepository: FakeStreakSettingsRepository
    private lateinit var fakeOnboardingDeviceStateRepository: FakeOnboardingDeviceStateRepository
    private lateinit var viewModel: OnboardingViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeNotesRepository = FakeJournalNotesRepository()
        fakeUserStateRepository = FakeUserStateRepository()
        fakeMemoriesSettingsRepository = FakeMemoriesSettingsRepository()
        fakeLocationSettingsRepository = FakeLocationTrackingSettingsRepository()
        fakeDayBoundarySettingsRepository = FakeDayBoundarySettingsRepository()
        fakeHealthRepository = FakeLocalFirstHealthRepository()
        fakeProfileRepository = FakeProfileRepository()
        fakeAccountRepository = FakeAccountRepository()
        fakeSessionStorage = FakeSessionStorage()
        fakeStreakSettingsRepository = FakeStreakSettingsRepository()
        fakeOnboardingDeviceStateRepository = FakeOnboardingDeviceStateRepository()
        viewModel = createViewModel()
    }

    private fun createViewModel(): OnboardingViewModel =
        OnboardingViewModel(
            journalNotesRepository = fakeNotesRepository,
            userStateRepository = fakeUserStateRepository,
            memoriesSettingsRepository = fakeMemoriesSettingsRepository,
            locationTrackingSettingsRepository = fakeLocationSettingsRepository,
            dayBoundarySettingsRepository = fakeDayBoundarySettingsRepository,
            observeHealthConnectStatus = ObserveHealthConnectStatusUseCase(fakeHealthRepository),
            observeUserIdentity =
                ObserveUserIdentityUseCase(
                    profileRepository = fakeProfileRepository,
                    userStateRepository = fakeUserStateRepository,
                    accountRepository = fakeAccountRepository,
                    sessionStorage = fakeSessionStorage,
                ),
            onboardingDeviceStateRepository = fakeOnboardingDeviceStateRepository,
            refreshStreakUseCase =
                RefreshStreakUseCase(
                    calculateStreakUseCase = CalculateStreakUseCase(fakeNotesRepository),
                    streakSettingsRepository = fakeStreakSettingsRepository,
                ),
        )

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun updateBirthday_delegatesToRepository() =
        runTest {
            val birthday = Instant.fromEpochMilliseconds(946684800000) // 2000-01-01

            viewModel.updateBirthday(birthday)
            advanceUntilIdle()

            assertEquals(birthday, fakeUserStateRepository.lastBirthday)
        }

    @Test
    fun setRecommendationsEnabled_true_delegatesToRepository() =
        runTest {
            viewModel.setRecommendationsEnabled(true)
            advanceUntilIdle()

            assertTrue(fakeMemoriesSettingsRepository.contextualRecommendationsEnabled)
        }

    @Test
    fun setRecommendationsEnabled_false_delegatesToRepository() =
        runTest {
            viewModel.setRecommendationsEnabled(false)
            advanceUntilIdle()

            assertEquals(false, fakeMemoriesSettingsRepository.contextualRecommendationsEnabled)
        }

    @Test
    fun enableLocationTracking_delegatesToRepository() =
        runTest {
            viewModel.enableLocationTracking()
            advanceUntilIdle()

            assertTrue(fakeLocationSettingsRepository.backgroundTrackingEnabled)
        }

    @Test
    fun enableSleepBasedDayBoundaries_delegatesToRepository() =
        runTest {
            fakeDayBoundarySettingsRepository.setSleepBasedBoundariesEnabled(false)

            viewModel.enableSleepBasedDayBoundaries()
            advanceUntilIdle()

            assertTrue(fakeDayBoundarySettingsRepository.sleepBasedBoundariesEnabled)
        }

    @Test
    fun disableSleepBasedDayBoundaries_delegatesToRepository() =
        runTest {
            viewModel.disableSleepBasedDayBoundaries()
            advanceUntilIdle()

            assertEquals(false, fakeDayBoundarySettingsRepository.sleepBasedBoundariesEnabled)
        }

    @Test
    fun refreshHealthStatus_whenUnavailable_disablesSleepBasedDayBoundaries() =
        runTest {
            fakeHealthRepository.isAvailable = false
            fakeDayBoundarySettingsRepository.setSleepBasedBoundariesEnabled(true)

            viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(HealthConnectStatus.NOT_AVAILABLE, viewModel.healthConnectStatus.value)
            assertEquals(false, fakeDayBoundarySettingsRepository.sleepBasedBoundariesEnabled)
        }

    @Test
    fun completeOnboarding_delegatesToRepository() =
        runTest {
            fakeProfileRepository.setProfile(
                LogDateProfile(
                    displayName = "Alex",
                    bio = "Bio",
                ),
            )
            fakeUserStateRepository.setBirthday(Instant.fromEpochMilliseconds(946684800000))
            fakeOnboardingDeviceStateRepository.markRecommendationsHandled()
            fakeOnboardingDeviceStateRepository.markLocationHandled()
            fakeOnboardingDeviceStateRepository.markDayBoundariesHandled()
            fakeOnboardingDeviceStateRepository.markNotificationsHandled()
            advanceUntilIdle()

            viewModel.completeOnboarding()
            advanceUntilIdle()

            assertTrue(fakeUserStateRepository.isOnboardingComplete)
        }

    @Test
    fun completeOnboardingIfEligible_fails_when_birthday_missing() =
        runTest {
            fakeProfileRepository.setProfile(
                LogDateProfile(
                    displayName = "Alex",
                    bio = "Bio",
                ),
            )
            fakeOnboardingDeviceStateRepository.markRecommendationsHandled()
            fakeOnboardingDeviceStateRepository.markLocationHandled()
            fakeOnboardingDeviceStateRepository.markDayBoundariesHandled()
            fakeOnboardingDeviceStateRepository.markNotificationsHandled()

            val result = viewModel.completeOnboardingIfEligible()

            assertTrue(result.isFailure)
            assertEquals(false, fakeUserStateRepository.isOnboardingComplete)
        }

    @Test
    fun completeOnboardingIfEligible_fails_when_location_step_not_handled() =
        runTest {
            fakeProfileRepository.setProfile(
                LogDateProfile(
                    displayName = "Alex",
                    bio = "Bio",
                ),
            )
            fakeUserStateRepository.setBirthday(Instant.fromEpochMilliseconds(946684800000))
            fakeOnboardingDeviceStateRepository.markRecommendationsHandled()
            fakeOnboardingDeviceStateRepository.markDayBoundariesHandled()
            fakeOnboardingDeviceStateRepository.markNotificationsHandled()
            advanceUntilIdle()

            val result = viewModel.completeOnboardingIfEligible()

            assertTrue(result.isFailure)
            assertEquals(false, fakeUserStateRepository.isOnboardingComplete)
            assertEquals(OnboardingStep.LOCATION, viewModel.firstIncompleteRequiredOnboardingStep())
        }

    @Test
    fun markNotificationsHandled_updatesProgressSnapshot() =
        runTest {
            viewModel.markNotificationsHandled()
            advanceUntilIdle()

            assertTrue(viewModel.progressSnapshot.value.notificationsHandledOnThisDevice)
        }

    @Test
    fun handledMarkers_updateProgressSnapshot() =
        runTest {
            viewModel.markRecommendationsHandled()
            viewModel.markDayBoundariesHandled()
            viewModel.markLocationHandled()
            advanceUntilIdle()

            assertTrue(viewModel.progressSnapshot.value.recommendationsHandledOnThisDevice)
            assertTrue(viewModel.progressSnapshot.value.dayBoundariesHandledOnThisDevice)
            assertTrue(viewModel.progressSnapshot.value.locationHandledOnThisDevice)
        }

    private class FakeStreakSettingsRepository : StreakSettingsRepository {
        private val streakEnabled = MutableStateFlow(true)
        private val cachedStreak = MutableStateFlow(0)

        override fun observeStreakEnabled(): Flow<Boolean> = streakEnabled

        override suspend fun isStreakEnabled(): Boolean = streakEnabled.value

        override suspend fun setStreakEnabled(enabled: Boolean) {
            streakEnabled.value = enabled
        }

        override fun observeCachedStreak(): Flow<Int> = cachedStreak

        override suspend fun getCachedStreak(): Int = cachedStreak.value

        override suspend fun setCachedStreak(value: Int) {
            cachedStreak.value = value
        }
    }

    @Test
    fun setActiveEntryMode_updatesState() =
        runTest {
            viewModel.setActiveEntryMode(OnboardingEntryMode.CONTINUE_SETUP)
            advanceUntilIdle()

            assertEquals(OnboardingEntryMode.CONTINUE_SETUP, viewModel.activeEntryMode.value)
        }
}

// region Fakes

private class FakeJournalNotesRepository : JournalNotesRepository {
    override val allNotesObserved: Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesInRange(
        start: Instant,
        end: Instant,
    ): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesPage(
        pageSize: Int,
        offset: Int,
    ): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = flowOf(emptyList())

    override suspend fun getNoteById(noteId: Uuid): JournalNote? = null

    override suspend fun create(note: JournalNote): Uuid = Uuid.random()

    override suspend fun create(
        note: JournalNote,
        journalId: Uuid,
    ) {}

    override suspend fun remove(note: JournalNote) {}

    override suspend fun removeById(noteId: Uuid) {}

    override suspend fun removeFromJournal(
        noteId: Uuid,
        journalId: Uuid,
    ) {}

    override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()
}

private class FakeUserStateRepository : UserStateRepository {
    private val state = MutableStateFlow(UserData())
    override val userData: Flow<UserData> = state
    var lastBirthday: Instant? = null
        private set
    var isOnboardingComplete: Boolean = false
        private set

    override suspend fun setBirthday(birthday: Instant) {
        lastBirthday = birthday
        state.value = state.value.copy(birthday = birthday)
    }

    override suspend fun setIsOnboardingComplete(isComplete: Boolean) {
        isOnboardingComplete = isComplete
        state.value = state.value.copy(isOnboarded = isComplete)
    }

    override suspend fun setBiometricEnabled(isEnabled: Boolean) {}

    override suspend fun addFavoriteNote(vararg noteId: String) {}
}

private class FakeProfileRepository : ProfileRepository {
    private val state = MutableStateFlow(LogDateProfile())
    override val currentProfile: Flow<LogDateProfile> = state

    override suspend fun updateDisplayName(displayName: String): Result<LogDateProfile> {
        state.value = state.value.copy(displayName = displayName)
        return Result.success(state.value)
    }

    override suspend fun updateBirthday(birthday: Instant?): Result<LogDateProfile> {
        state.value = state.value.copy(birthday = birthday)
        return Result.success(state.value)
    }

    override suspend fun updateProfilePhoto(profilePhotoUri: String?): Result<LogDateProfile> {
        state.value = state.value.copy(profilePhotoUri = profilePhotoUri)
        return Result.success(state.value)
    }

    override suspend fun updateBio(
        bio: String?,
        originalBio: String?,
    ): Result<LogDateProfile> {
        state.value = state.value.copy(bio = bio, originalBio = originalBio)
        return Result.success(state.value)
    }

    override suspend fun getCurrentProfile(): LogDateProfile = state.value

    override suspend fun clearProfile(): Result<Unit> {
        state.value = LogDateProfile()
        return Result.success(Unit)
    }

    fun setProfile(profile: LogDateProfile) {
        state.value = profile
    }
}

private class FakeAccountRepository : AccountRepository {
    private val state = MutableStateFlow<LogDateAccount?>(null)
    override val currentAccount: Flow<LogDateAccount?> = state

    override suspend fun updateProfile(
        displayName: String?,
        username: String?,
    ): Result<LogDateAccount> = Result.failure(NotImplementedError())

    override suspend fun refreshAccount(): Result<LogDateAccount> = Result.failure(NotImplementedError())

    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> = Result.success(true)
}

private class FakeSessionStorage : SessionStorage {
    private val state = MutableStateFlow<UserSession?>(null)

    override fun getSession(): UserSession? = state.value

    override fun getSessionFlow(): MutableStateFlow<UserSession?> = state

    override suspend fun hasValidSession(): Boolean = state.value != null

    override fun saveSession(session: UserSession) {
        state.value = session
    }

    override fun clearSession() {
        state.value = null
    }
}

private class FakeOnboardingDeviceStateRepository : OnboardingDeviceStateRepository {
    private val state = MutableStateFlow(OnboardingDeviceState())
    override val deviceState: StateFlow<OnboardingDeviceState> = state

    override suspend fun markRecommendationsHandled() {
        state.value = state.value.copy(recommendationsHandledOnThisDevice = true)
    }

    override suspend fun markDayBoundariesHandled() {
        state.value = state.value.copy(dayBoundariesHandledOnThisDevice = true)
    }

    override suspend fun markLocationHandled() {
        state.value = state.value.copy(locationHandledOnThisDevice = true)
    }

    override suspend fun markNotificationsHandled() {
        state.value = state.value.copy(notificationsHandledOnThisDevice = true)
    }

    override suspend fun setActiveEntryMode(entryMode: OnboardingEntryMode) {
        state.value = state.value.copy(activeEntryMode = entryMode)
    }

    override suspend fun clear() {
        state.value = OnboardingDeviceState()
    }
}

private class FakeMemoriesSettingsRepository : MemoriesSettingsRepository {
    var contextualRecommendationsEnabled: Boolean = true
        private set
    private val _settings = MutableStateFlow(MemoriesSettings())

    override suspend fun getSettings(): MemoriesSettings = _settings.value

    override fun observeSettings(): Flow<MemoriesSettings> = _settings

    override suspend fun updateSettings(settings: MemoriesSettings) {
        _settings.value = settings
    }

    override suspend fun setContextualRecommendationsEnabled(enabled: Boolean) {
        contextualRecommendationsEnabled = enabled
        _settings.value = _settings.value.copy(contextualRecommendationsEnabled = enabled)
    }

    override suspend fun setAiRecallEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(aiRecallEnabled = enabled)
    }

    override suspend fun setRecallMode(mode: RecallMode) {
        _settings.value = _settings.value.copy(recallMode = mode)
    }

    override suspend fun setWidgetContentTypes(types: Set<WidgetContentType>) {
        _settings.value = _settings.value.copy(widgetContentTypes = types)
    }
}

private class FakeLocationTrackingSettingsRepository : LocationTrackingSettingsRepository {
    var backgroundTrackingEnabled: Boolean = false
        private set
    private val _settings = MutableStateFlow(LocationTrackingSettings())

    override suspend fun getSettings(): LocationTrackingSettings = _settings.value

    override fun observeSettings(): Flow<LocationTrackingSettings> = _settings

    override suspend fun updateSettings(settings: LocationTrackingSettings) {
        _settings.value = settings
    }

    override suspend fun setBackgroundTrackingEnabled(enabled: Boolean) {
        backgroundTrackingEnabled = enabled
        _settings.value = _settings.value.copy(backgroundTrackingEnabled = enabled)
    }
}

private class FakeDayBoundarySettingsRepository : DayBoundarySettingsRepository {
    var sleepBasedBoundariesEnabled: Boolean = false
        private set
    private val _settings = MutableStateFlow(DayBoundarySettings())

    override suspend fun getSettings(): DayBoundarySettings = _settings.value

    override fun observeSettings(): Flow<DayBoundarySettings> = _settings

    override suspend fun setSleepBasedBoundariesEnabled(enabled: Boolean) {
        sleepBasedBoundariesEnabled = enabled
        _settings.value = _settings.value.copy(sleepBasedBoundariesEnabled = enabled)
    }
}

private class FakeLocalFirstHealthRepository : LocalFirstHealthRepository {
    var isAvailable: Boolean = true
    var hasPermissions: Boolean = true

    override suspend fun isHealthDataAvailable(): Boolean = isAvailable

    override suspend fun getAvailableDataTypes(): List<String> = emptyList()

    override suspend fun hasSleepPermissions(): Boolean = hasPermissions

    override suspend fun requestSleepPermissions(): Boolean = hasPermissions

    override suspend fun getSleepSessions(
        start: Instant,
        end: Instant,
    ): List<SleepSession> = emptyList()

    override suspend fun getAverageWakeUpTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? = null

    override suspend fun getAverageSleepTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? = null

    override suspend fun getDayBoundsForDate(
        date: LocalDate,
        timeZone: TimeZone,
        sleepBasedBoundariesEnabled: Boolean,
    ): DayBounds = error("Not used in onboarding tests")
}

// endregion
