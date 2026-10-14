package app.logdate.feature.onboarding.ui

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.repository.account.AccountRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.feature.onboarding.flow.OnboardingDeviceState
import app.logdate.feature.onboarding.flow.OnboardingDeviceStateRepository
import app.logdate.feature.onboarding.flow.OnboardingEntryMode
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.profile.LogDateProfile
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class FakeJournalNotesRepository : JournalNotesRepository {
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

    val created = mutableListOf<JournalNote>()

    override suspend fun create(note: JournalNote): Uuid {
        created += note
        return note.uid
    }

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

internal class FakeUserStateRepository : UserStateRepository {
    private val state = MutableStateFlow(UserData())
    override val userData: Flow<UserData> = state
    var lastBirthday: Instant? = null
        private set
    var isOnboardingComplete: Boolean = false
        private set
    var setOnboardingCompleteFailure: Throwable? = null

    override suspend fun setBirthday(birthday: Instant) {
        lastBirthday = birthday
        state.value = state.value.copy(birthday = birthday)
    }

    override suspend fun setIsOnboardingComplete(isComplete: Boolean) {
        setOnboardingCompleteFailure?.let { throw it }
        isOnboardingComplete = isComplete
        state.value = state.value.copy(isOnboarded = isComplete)
    }

    override suspend fun setBiometricEnabled(isEnabled: Boolean) {}

    override suspend fun addFavoriteNote(vararg noteId: String) {}
}

internal class FakeProfileRepository : ProfileRepository {
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

internal class FakeAccountRepository : AccountRepository {
    private val state = MutableStateFlow<LogDateAccount?>(null)
    override val currentAccount: Flow<LogDateAccount?> = state

    override suspend fun updateProfile(
        displayName: String?,
        username: String?,
    ): Result<LogDateAccount> = Result.failure(NotImplementedError())

    override suspend fun refreshAccount(): Result<LogDateAccount> = Result.failure(NotImplementedError())

    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> = Result.success(true)
}

internal class FakeSessionStorage : SessionStorage {
    private val state = MutableStateFlow<UserSession?>(null)

    override fun getSession(): UserSession? = state.value

    override fun getSessionFlow(): MutableStateFlow<UserSession?> = state

    override suspend fun hasValidSession(): Boolean = state.value != null

    override suspend fun saveSession(session: UserSession) {
        state.value = session
    }

    override suspend fun clearSession() {
        state.value = null
    }
}

internal class FakeOnboardingDeviceStateRepository : OnboardingDeviceStateRepository {
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

    override suspend fun markWatchHandled() {
        state.value = state.value.copy(watchHandledOnThisDevice = true)
    }

    override suspend fun markAccountHandled() {
        state.value = state.value.copy(accountHandledOnThisDevice = true)
    }

    override suspend fun setActiveEntryMode(entryMode: OnboardingEntryMode) {
        state.value = state.value.copy(activeEntryMode = entryMode)
    }

    override suspend fun clear() {
        state.value = OnboardingDeviceState()
    }
}
