package app.logdate.feature.onboarding.ui

import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    private lateinit var viewModel: OnboardingViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeNotesRepository = FakeJournalNotesRepository()
        fakeUserStateRepository = FakeUserStateRepository()
        fakeMemoriesSettingsRepository = FakeMemoriesSettingsRepository()
        fakeLocationSettingsRepository = FakeLocationTrackingSettingsRepository()
        viewModel =
            OnboardingViewModel(
                journalNotesRepository = fakeNotesRepository,
                userStateRepository = fakeUserStateRepository,
                memoriesSettingsRepository = fakeMemoriesSettingsRepository,
                locationTrackingSettingsRepository = fakeLocationSettingsRepository,
            )
    }

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
    fun completeOnboarding_delegatesToRepository() =
        runTest {
            viewModel.completeOnboarding()
            advanceUntilIdle()

            assertTrue(fakeUserStateRepository.isOnboardingComplete)
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
}

private class FakeUserStateRepository : UserStateRepository {
    override val userData: Flow<UserData> = flowOf(UserData())
    var lastBirthday: Instant? = null
        private set
    var isOnboardingComplete: Boolean = false
        private set

    override suspend fun setBirthday(birthday: Instant) {
        lastBirthday = birthday
    }

    override suspend fun setIsOnboardingComplete(isComplete: Boolean) {
        isOnboardingComplete = isComplete
    }

    override suspend fun setBiometricEnabled(isEnabled: Boolean) {}

    override suspend fun addFavoriteNote(vararg noteId: String) {}
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

// endregion
