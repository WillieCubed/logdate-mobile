package app.logdate.client.e2e

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.domain.dayboundary.DayBoundarySettings
import app.logdate.client.domain.dayboundary.DayBoundarySettingsRepository
import app.logdate.client.health.LocalFirstHealthRepository
import app.logdate.client.health.model.DayBounds
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIRequest
import app.logdate.client.intelligence.generativeai.GenerativeAIResponse
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import app.logdate.client.media.MediaPayload
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import app.logdate.client.repository.account.AccountRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.client.repository.streak.StreakSettingsRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.di.appModule
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.profile.LogDateProfile
import app.logdate.shared.model.user.AppSecurityLevel
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class OnboardingJourneyEnvironment {
    val userStateRepository = OnboardingFakeUserStateRepository()
    val profileRepository = OnboardingFakeProfileRepository()
    val accountRepository = OnboardingFakeAccountRepository()
    val sessionStorage = OnboardingFakeSessionStorage()
    val memoriesSettingsRepository = OnboardingFakeMemoriesSettingsRepository()
    val locationSettingsRepository = OnboardingFakeLocationTrackingSettingsRepository()
    val dayBoundarySettingsRepository = OnboardingFakeDayBoundarySettingsRepository()
    val onboardingDeviceStateRepository = OnboardingFakeOnboardingDeviceStateRepository()
    val mediaManager = OnboardingFakeMediaManager()
    val aiClient = OnboardingFakeGenerativeAiChatClient()
    val networkMonitor = OnboardingFakeNetworkAvailabilityMonitor()
    val healthRepository = OnboardingFakeHealthRepository()
    val journalNotesRepository = OnboardingFakeJournalNotesRepository()
    val streakSettingsRepository = OnboardingFakeStreakSettingsRepository()

    val module: Module =
        module {
            single<UserStateRepository> { userStateRepository }
            single<ProfileRepository> { profileRepository }
            single<AccountRepository> { accountRepository }
            single<SessionStorage> { sessionStorage }
            single<MemoriesSettingsRepository> { memoriesSettingsRepository }
            single<LocationTrackingSettingsRepository> { locationSettingsRepository }
            single<DayBoundarySettingsRepository> { dayBoundarySettingsRepository }
            single<app.logdate.feature.onboarding.flow.OnboardingDeviceStateRepository> { onboardingDeviceStateRepository }
            single<MediaManager> { mediaManager }
            single<GenerativeAIChatClient> { aiClient }
            single<NetworkAvailabilityMonitor> { networkMonitor }
            single<LocalFirstHealthRepository> { healthRepository }
            single<JournalNotesRepository> { journalNotesRepository }
            single<StreakSettingsRepository> { streakSettingsRepository }
        }

    fun reset() {
        userStateRepository.reset()
        profileRepository.reset()
        accountRepository.reset()
        sessionStorage.reset()
        memoriesSettingsRepository.reset()
        locationSettingsRepository.reset()
        dayBoundarySettingsRepository.reset()
        onboardingDeviceStateRepository.reset()
        mediaManager.reset()
        aiClient.reset()
        networkMonitor.reset()
        healthRepository.reset()
        journalNotesRepository.reset()
        streakSettingsRepository.reset()
    }

    fun seedFreshFlow(
        hasIntro: Boolean = false,
        hasBirthday: Boolean = false,
        hasCloudAccount: Boolean = false,
        notificationsHandled: Boolean = false,
        healthAvailable: Boolean = false,
        healthPermissionsGranted: Boolean = false,
    ) {
        reset()
        if (hasIntro) {
            runBlocking {
                profileRepository.updateDisplayName("Test User")
                profileRepository.updateBio("I like journaling", "I like journaling")
            }
        }
        if (hasBirthday) {
            runBlocking {
                userStateRepository.setBirthday(Clock.System.now())
            }
        }
        if (hasCloudAccount) {
            accountRepository.current.value =
                LogDateAccount(
                    username = "tester",
                    displayName = "Test User",
                )
            sessionStorage.saveSession(
                UserSession(
                    accessToken = "token",
                    refreshToken = "refresh",
                    accountId = "account",
                ),
            )
        }
        if (notificationsHandled) {
            runBlocking {
                onboardingDeviceStateRepository.markNotificationsHandled()
            }
        }
        healthRepository.available = healthAvailable
        healthRepository.sleepPermissionsGranted = healthPermissionsGranted
    }
}

internal class OnboardingKoinModuleOverrideRule(
    private val module: Module,
) : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                if (GlobalContext.getOrNull() == null) {
                    startKoin {
                        androidContext(context)
                        modules(appModule)
                    }
                }
                loadKoinModules(module)
                base.evaluate()
            }
        }
}

internal fun SemanticsNodeInteraction.captureStepScreenshot(
    composeRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
    testName: String,
    stepName: String,
) {
    val context = composeRule.activity
    val outputDir = File(context.getExternalFilesDir("test-artifacts"), "onboarding/$testName").apply { mkdirs() }
    val outputFile = File(outputDir, stepName)
    outputFile.outputStream().use { stream ->
        captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
    }
}

internal class OnboardingFakeUserStateRepository : UserStateRepository {
    private val _userData = MutableStateFlow(UserData())
    val current: MutableStateFlow<UserData> = _userData
    override val userData: Flow<UserData> = _userData.asStateFlow()

    fun reset() {
        _userData.value = UserData()
    }

    override suspend fun setBirthday(birthday: Instant) {
        _userData.value = _userData.value.copy(birthday = birthday)
    }

    override suspend fun setIsOnboardingComplete(isComplete: Boolean) {
        _userData.value =
            _userData.value.copy(
                isOnboarded = isComplete,
                onboardedDate = if (isComplete) Clock.System.now() else Instant.DISTANT_PAST,
            )
    }

    override suspend fun setBiometricEnabled(isEnabled: Boolean) {
        _userData.value =
            _userData.value.copy(
                securityLevel = if (isEnabled) AppSecurityLevel.BIOMETRIC else AppSecurityLevel.NONE,
            )
    }

    override suspend fun addFavoriteNote(vararg noteId: String) {
        _userData.value = _userData.value.copy(favoriteNotes = (_userData.value.favoriteNotes + noteId).distinct())
    }
}

internal class OnboardingFakeProfileRepository : ProfileRepository {
    private val _profile = MutableStateFlow(LogDateProfile())
    override val currentProfile: Flow<LogDateProfile> = _profile.asStateFlow()

    fun reset() {
        _profile.value = LogDateProfile()
    }

    override suspend fun updateDisplayName(displayName: String): Result<LogDateProfile> {
        _profile.value = _profile.value.copy(displayName = displayName)
        return Result.success(_profile.value)
    }

    override suspend fun updateBirthday(birthday: Instant?): Result<LogDateProfile> {
        _profile.value = _profile.value.copy(birthday = birthday)
        return Result.success(_profile.value)
    }

    override suspend fun updateProfilePhoto(profilePhotoUri: String?): Result<LogDateProfile> {
        _profile.value = _profile.value.copy(profilePhotoUri = profilePhotoUri)
        return Result.success(_profile.value)
    }

    override suspend fun updateBio(
        bio: String?,
        originalBio: String?,
    ): Result<LogDateProfile> {
        _profile.value = _profile.value.copy(bio = bio, originalBio = originalBio)
        return Result.success(_profile.value)
    }

    override suspend fun getCurrentProfile(): LogDateProfile = _profile.value

    override suspend fun clearProfile(): Result<Unit> {
        _profile.value = LogDateProfile()
        return Result.success(Unit)
    }
}

internal class OnboardingFakeAccountRepository : AccountRepository {
    val current = MutableStateFlow<LogDateAccount?>(null)
    override val currentAccount: Flow<LogDateAccount?> = current.asStateFlow()

    fun reset() {
        current.value = null
    }

    override suspend fun updateProfile(
        displayName: String?,
        username: String?,
    ): Result<LogDateAccount> {
        val updated =
            (current.value ?: LogDateAccount(username = username ?: "user", displayName = displayName ?: "User")).copy(
                username = username ?: current.value?.username ?: "user",
                displayName = displayName ?: current.value?.displayName ?: "User",
            )
        current.value = updated
        return Result.success(updated)
    }

    override suspend fun refreshAccount(): Result<LogDateAccount> = Result.success(current.value ?: LogDateAccount(username = "user", displayName = "User"))

    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> = Result.success(true)
}

internal class OnboardingFakeSessionStorage : SessionStorage {
    private val sessionFlow = MutableStateFlow<UserSession?>(null)

    fun reset() {
        sessionFlow.value = null
    }

    override fun getSession(): UserSession? = sessionFlow.value

    override fun getSessionFlow(): Flow<UserSession?> = sessionFlow.asStateFlow()

    override suspend fun hasValidSession(): Boolean = sessionFlow.value != null

    override fun saveSession(session: UserSession) {
        sessionFlow.value = session
    }

    override fun clearSession() {
        sessionFlow.value = null
    }
}

internal class OnboardingFakeMemoriesSettingsRepository : MemoriesSettingsRepository {
    private val state = MutableStateFlow(MemoriesSettings())

    fun reset() {
        state.value = MemoriesSettings()
    }

    override suspend fun getSettings(): MemoriesSettings = state.value

    override fun observeSettings(): Flow<MemoriesSettings> = state.asStateFlow()

    override suspend fun updateSettings(settings: MemoriesSettings) {
        state.value = settings
    }

    override suspend fun setContextualRecommendationsEnabled(enabled: Boolean) {
        state.value = state.value.copy(contextualRecommendationsEnabled = enabled)
    }

    override suspend fun setAiRecallEnabled(enabled: Boolean) {
        state.value = state.value.copy(aiRecallEnabled = enabled)
    }

    override suspend fun setRecallMode(mode: app.logdate.client.domain.recommendation.RecallMode) {
        state.value = state.value.copy(recallMode = mode)
    }

    override suspend fun setWidgetContentTypes(types: Set<app.logdate.client.domain.recommendation.WidgetContentType>) {
        state.value = state.value.copy(widgetContentTypes = types)
    }
}

internal class OnboardingFakeLocationTrackingSettingsRepository : LocationTrackingSettingsRepository {
    private val state = MutableStateFlow(LocationTrackingSettings())

    fun reset() {
        state.value = LocationTrackingSettings()
    }

    override suspend fun getSettings(): LocationTrackingSettings = state.value

    override fun observeSettings(): Flow<LocationTrackingSettings> = state.asStateFlow()

    override suspend fun updateSettings(settings: LocationTrackingSettings) {
        state.value = settings
    }

    override suspend fun setBackgroundTrackingEnabled(enabled: Boolean) {
        state.value = state.value.copy(backgroundTrackingEnabled = enabled)
    }
}

internal class OnboardingFakeDayBoundarySettingsRepository : DayBoundarySettingsRepository {
    private val state = MutableStateFlow(DayBoundarySettings())

    fun reset() {
        state.value = DayBoundarySettings()
    }

    override suspend fun getSettings(): DayBoundarySettings = state.value

    override fun observeSettings(): Flow<DayBoundarySettings> = state.asStateFlow()

    override suspend fun setSleepBasedBoundariesEnabled(enabled: Boolean) {
        state.value = DayBoundarySettings(sleepBasedBoundariesEnabled = enabled)
    }
}

internal class OnboardingFakeOnboardingDeviceStateRepository : app.logdate.feature.onboarding.flow.OnboardingDeviceStateRepository {
    private val state = MutableStateFlow(app.logdate.feature.onboarding.flow.OnboardingDeviceState())
    override val deviceState: Flow<app.logdate.feature.onboarding.flow.OnboardingDeviceState> = state.asStateFlow()

    fun reset() {
        state.value = app.logdate.feature.onboarding.flow.OnboardingDeviceState()
    }

    override suspend fun markNotificationsHandled() {
        state.value = state.value.copy(notificationsHandledOnThisDevice = true)
    }

    override suspend fun setActiveEntryMode(entryMode: app.logdate.feature.onboarding.flow.OnboardingEntryMode) {
        state.value = state.value.copy(activeEntryMode = entryMode)
    }

    override suspend fun clear() {
        reset()
    }
}

internal class OnboardingFakeMediaManager : MediaManager {
    private val sampleMedia =
        listOf(
            MediaObject.Image(
                uri = "content://test/image-1",
                size = 128_000,
                name = "sunrise.jpg",
                timestamp = Clock.System.now(),
            ),
            MediaObject.Image(
                uri = "content://test/image-2",
                size = 256_000,
                name = "friends.jpg",
                timestamp = Clock.System.now(),
            ),
        )
    private val imported = mutableListOf<String>()

    fun reset() {
        imported.clear()
    }

    override suspend fun getMedia(uri: String): MediaObject = sampleMedia.first { it.uri == uri }

    override suspend fun exists(mediaId: String): Boolean = sampleMedia.any { it.uri == mediaId }

    override suspend fun getRecentMedia(): Flow<List<MediaObject>> = flowOf(sampleMedia)

    override suspend fun queryMediaByDate(
        start: Instant,
        end: Instant,
    ): Flow<List<MediaObject>> = flowOf(sampleMedia)

    override suspend fun addToDefaultCollection(uri: String) {
        imported += uri
    }

    override suspend fun readMedia(uri: String): MediaPayload =
        MediaPayload(
            fileName = "test.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 1,
            data = byteArrayOf(1),
        )

    override suspend fun saveMedia(payload: MediaPayload): String = "content://saved/${payload.fileName}"

    override suspend fun saveMediaFromFile(
        sourceFilePath: String,
        fileName: String,
        mimeType: String,
    ): String = "content://saved/$fileName"
}

internal class OnboardingFakeGenerativeAiChatClient : GenerativeAIChatClient {
    override val providerId: String = "fake"
    override val defaultModel: String? = "fake-model"

    fun reset() = Unit

    override suspend fun submit(request: GenerativeAIRequest): AIResult<GenerativeAIResponse> =
        AIResult.Success(
            GenerativeAIResponse(
                content = """{"message":"It's good to meet you. You're ready to start logging."}""",
                model = defaultModel,
            ),
        )
}

internal class OnboardingFakeNetworkAvailabilityMonitor : NetworkAvailabilityMonitor {
    private val state = MutableSharedFlow<NetworkState>(replay = 1)

    init {
        reset()
    }

    fun reset() {
        state.tryEmit(NetworkState.Connected(Clock.System.now()))
    }

    override fun isNetworkAvailable(): Boolean = true

    override fun observeNetwork(): SharedFlow<NetworkState> = state.asSharedFlow()
}

internal class OnboardingFakeHealthRepository : LocalFirstHealthRepository {
    var available: Boolean = false
    var sleepPermissionsGranted: Boolean = false

    fun reset() {
        available = false
        sleepPermissionsGranted = false
    }

    override suspend fun isHealthDataAvailable(): Boolean = available

    override suspend fun getAvailableDataTypes(): List<String> = emptyList()

    override suspend fun hasSleepPermissions(): Boolean = sleepPermissionsGranted

    override suspend fun requestSleepPermissions(): Boolean {
        sleepPermissionsGranted = true
        return true
    }

    override suspend fun getSleepSessions(
        start: Instant,
        end: Instant,
    ): List<SleepSession> = emptyList()

    override suspend fun getAverageWakeUpTime(
        timeZone: kotlinx.datetime.TimeZone,
        days: Int,
    ): TimeOfDay? = null

    override suspend fun getAverageSleepTime(
        timeZone: kotlinx.datetime.TimeZone,
        days: Int,
    ): TimeOfDay? = null

    override suspend fun getDayBoundsForDate(
        date: LocalDate,
        timeZone: kotlinx.datetime.TimeZone,
        sleepBasedBoundariesEnabled: Boolean,
    ): DayBounds = DayBounds(start = Clock.System.now(), end = Clock.System.now())
}

internal class OnboardingFakeJournalNotesRepository : JournalNotesRepository {
    private val notes = MutableStateFlow<List<JournalNote>>(emptyList())
    override val allNotesObserved: Flow<List<JournalNote>> = notes.asStateFlow()

    fun reset() {
        notes.value = emptyList()
    }

    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

    override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()

    override fun observeNotesInRange(
        start: Instant,
        end: Instant,
    ): Flow<List<JournalNote>> = notes.asStateFlow()

    override fun observeNotesPage(
        pageSize: Int,
        offset: Int,
    ): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = notes.asStateFlow()

    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = notes.asStateFlow()

    override suspend fun getNoteById(noteId: Uuid): JournalNote? = notes.value.firstOrNull { it.uid == noteId }

    override suspend fun create(note: JournalNote): Uuid {
        notes.value = notes.value + note
        return note.uid
    }

    override suspend fun remove(note: JournalNote) {
        notes.value = notes.value - note
    }

    override suspend fun removeById(noteId: Uuid) {
        notes.value = notes.value.filterNot { it.uid == noteId }
    }

    override suspend fun create(
        note: JournalNote,
        journalId: Uuid,
    ) {
        notes.value = notes.value + note
    }

    override suspend fun removeFromJournal(
        noteId: Uuid,
        journalId: Uuid,
    ) = Unit
}

internal class OnboardingFakeStreakSettingsRepository : StreakSettingsRepository {
    private val enabled = MutableStateFlow(true)
    private val cached = MutableStateFlow(0)

    fun reset() {
        enabled.value = true
        cached.value = 0
    }

    override fun observeStreakEnabled(): Flow<Boolean> = enabled.asStateFlow()

    override suspend fun isStreakEnabled(): Boolean = enabled.value

    override suspend fun setStreakEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }

    override fun observeCachedStreak(): Flow<Int> = cached.asStateFlow()

    override suspend fun getCachedStreak(): Int = cached.value

    override suspend fun setCachedStreak(value: Int) {
        cached.value = value
    }
}
