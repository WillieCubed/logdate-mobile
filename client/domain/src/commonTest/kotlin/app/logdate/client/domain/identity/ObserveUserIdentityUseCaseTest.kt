package app.logdate.client.domain.identity

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.repository.account.AccountRepository
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.profile.LogDateProfile
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Unit tests for [ObserveUserIdentityUseCase].
 *
 * Verifies the consolidation of user identity data from multiple sources,
 * including local profile storage, cloud account metadata, and active
 * session state. It specifically tests the precedence logic where local
 * overrides (like display name or bio) take priority over cloud-provided values.
 */
class ObserveUserIdentityUseCaseTest {
    // region Fakes

    private class FakeProfileRepository(
        initialProfile: LogDateProfile = LogDateProfile(),
    ) : ProfileRepository {
        val profileFlow = MutableStateFlow(initialProfile)
        override val currentProfile: Flow<LogDateProfile> = profileFlow

        override suspend fun updateDisplayName(displayName: String): Result<LogDateProfile> = Result.success(profileFlow.value)

        override suspend fun updateBirthday(birthday: Instant?): Result<LogDateProfile> = Result.success(profileFlow.value)

        override suspend fun updateProfilePhoto(profilePhotoUri: String?): Result<LogDateProfile> = Result.success(profileFlow.value)

        override suspend fun updateBio(
            bio: String?,
            originalBio: String?,
        ): Result<LogDateProfile> = Result.success(profileFlow.value)

        override suspend fun getCurrentProfile(): LogDateProfile = profileFlow.value

        override suspend fun clearProfile(): Result<Unit> = Result.success(Unit)
    }

    private class FakeUserStateRepository(
        initialData: UserData = UserData(),
    ) : UserStateRepository {
        val dataFlow = MutableStateFlow(initialData)
        override val userData: Flow<UserData> = dataFlow

        override suspend fun setBirthday(birthday: Instant) {}

        override suspend fun setIsOnboardingComplete(isComplete: Boolean) {}

        override suspend fun setBiometricEnabled(isEnabled: Boolean) {}

        override suspend fun addFavoriteNote(vararg noteId: String) {}
    }

    private class FakeAccountRepository(
        initialAccount: LogDateAccount? = null,
    ) : AccountRepository {
        val accountFlow = MutableStateFlow(initialAccount)
        override val currentAccount: Flow<LogDateAccount?> = accountFlow

        override suspend fun updateProfile(
            displayName: String?,
            username: String?,
        ): Result<LogDateAccount> = Result.failure(UnsupportedOperationException())

        override suspend fun refreshAccount(): Result<LogDateAccount> = Result.failure(UnsupportedOperationException())

        override suspend fun checkUsernameAvailability(username: String): Result<Boolean> = Result.failure(UnsupportedOperationException())
    }

    private class FakeSessionStorage(
        initialSession: UserSession? = null,
    ) : SessionStorage {
        val sessionFlow = MutableStateFlow(initialSession)

        override fun getSession(): UserSession? = sessionFlow.value

        override fun getSessionFlow(): Flow<UserSession?> = sessionFlow

        override suspend fun hasValidSession(): Boolean = sessionFlow.value != null

        override fun saveSession(session: UserSession) {
            sessionFlow.value = session
        }

        override fun clearSession() {
            sessionFlow.value = null
        }
    }

    // endregion

    private fun createUseCase(
        profileRepository: FakeProfileRepository = FakeProfileRepository(),
        userStateRepository: FakeUserStateRepository = FakeUserStateRepository(),
        accountRepository: FakeAccountRepository = FakeAccountRepository(),
        sessionStorage: FakeSessionStorage = FakeSessionStorage(),
    ) = ObserveUserIdentityUseCase(
        profileRepository = profileRepository,
        userStateRepository = userStateRepository,
        accountRepository = accountRepository,
        sessionStorage = sessionStorage,
    )

    @Test
    fun `local display name wins over cloud display name`() =
        runTest {
            val profileRepo =
                FakeProfileRepository(
                    LogDateProfile(displayName = "Local Name"),
                )
            val accountRepo =
                FakeAccountRepository(
                    LogDateAccount(username = "user", displayName = "Cloud Name"),
                )
            val useCase =
                createUseCase(
                    profileRepository = profileRepo,
                    accountRepository = accountRepo,
                )

            val identity = useCase().first()

            assertEquals("Local Name", identity.displayName)
        }

    @Test
    fun `cloud display name used when local is empty`() =
        runTest {
            val profileRepo =
                FakeProfileRepository(
                    LogDateProfile(displayName = ""),
                )
            val accountRepo =
                FakeAccountRepository(
                    LogDateAccount(username = "user", displayName = "Cloud Name"),
                )
            val useCase =
                createUseCase(
                    profileRepository = profileRepo,
                    accountRepository = accountRepo,
                )

            val identity = useCase().first()

            assertEquals("Cloud Name", identity.displayName)
        }

    @Test
    fun `empty string when no display name set anywhere`() =
        runTest {
            val useCase = createUseCase()

            val identity = useCase().first()

            assertEquals("", identity.displayName)
        }

    @Test
    fun `isAuthenticated true when session exists`() =
        runTest {
            val session =
                UserSession(
                    accessToken = "token",
                    refreshToken = "refresh",
                    accountId = "account-1",
                )
            val sessionStorage = FakeSessionStorage(session)
            val useCase = createUseCase(sessionStorage = sessionStorage)

            val identity = useCase().first()

            assertTrue(identity.isAuthenticated)
        }

    @Test
    fun `isAuthenticated false when no session`() =
        runTest {
            val useCase = createUseCase()

            val identity = useCase().first()

            assertFalse(identity.isAuthenticated)
        }

    @Test
    fun `onboardedDate converts DISTANT_PAST to null`() =
        runTest {
            val userStateRepo =
                FakeUserStateRepository(
                    UserData(onboardedDate = Instant.DISTANT_PAST),
                )
            val useCase = createUseCase(userStateRepository = userStateRepo)

            val identity = useCase().first()

            assertNull(identity.onboardedDate)
        }

    @Test
    fun `onboardedDate preserved when set`() =
        runTest {
            val onboarded = Instant.fromEpochSeconds(1700000000)
            val userStateRepo =
                FakeUserStateRepository(
                    UserData(onboardedDate = onboarded),
                )
            val useCase = createUseCase(userStateRepository = userStateRepo)

            val identity = useCase().first()

            assertEquals(onboarded, identity.onboardedDate)
        }

    @Test
    fun `birthday resolves from local profile first`() =
        runTest {
            val localBirthday = Instant.fromEpochSeconds(946684800) // 2000-01-01
            val userDataBirthday = Instant.fromEpochSeconds(915148800) // 1999-01-01
            val profileRepo =
                FakeProfileRepository(
                    LogDateProfile(birthday = localBirthday),
                )
            val userStateRepo =
                FakeUserStateRepository(
                    UserData(birthday = userDataBirthday),
                )
            val useCase =
                createUseCase(
                    profileRepository = profileRepo,
                    userStateRepository = userStateRepo,
                )

            val identity = useCase().first()

            assertEquals(localBirthday, identity.birthday)
        }

    @Test
    fun `birthday falls back to UserData when local profile has none`() =
        runTest {
            val userDataBirthday = Instant.fromEpochSeconds(915148800)
            val profileRepo =
                FakeProfileRepository(
                    LogDateProfile(birthday = null),
                )
            val userStateRepo =
                FakeUserStateRepository(
                    UserData(birthday = userDataBirthday),
                )
            val useCase =
                createUseCase(
                    profileRepository = profileRepo,
                    userStateRepository = userStateRepo,
                )

            val identity = useCase().first()

            assertEquals(userDataBirthday, identity.birthday)
        }

    @Test
    fun `birthday null when UserData has DISTANT_PAST and local profile has none`() =
        runTest {
            val useCase = createUseCase()

            val identity = useCase().first()

            assertNull(identity.birthday)
        }

    @Test
    fun `username null when no cloud account`() =
        runTest {
            val useCase = createUseCase()

            val identity = useCase().first()

            assertNull(identity.username)
        }

    @Test
    fun `username null when cloud account has empty username`() =
        runTest {
            val accountRepo =
                FakeAccountRepository(
                    LogDateAccount(username = "", displayName = "Name"),
                )
            val useCase = createUseCase(accountRepository = accountRepo)

            val identity = useCase().first()

            assertNull(identity.username)
        }

    @Test
    fun `username present when cloud account has username`() =
        runTest {
            val accountRepo =
                FakeAccountRepository(
                    LogDateAccount(username = "johndoe", displayName = "John"),
                )
            val useCase = createUseCase(accountRepository = accountRepo)

            val identity = useCase().first()

            assertEquals("johndoe", identity.username)
        }

    @Test
    fun `cloudAccountId from cloud account`() =
        runTest {
            val account = LogDateAccount(username = "user", displayName = "Name")
            val accountRepo = FakeAccountRepository(account)
            val useCase = createUseCase(accountRepository = accountRepo)

            val identity = useCase().first()

            assertEquals(account.id.toString(), identity.cloudAccountId)
        }

    @Test
    fun `cloudAccountId null without cloud account`() =
        runTest {
            val useCase = createUseCase()

            val identity = useCase().first()

            assertNull(identity.cloudAccountId)
        }

    @Test
    fun `bio resolves from local profile first`() =
        runTest {
            val profileRepo =
                FakeProfileRepository(
                    LogDateProfile(bio = "Local bio"),
                )
            val accountRepo =
                FakeAccountRepository(
                    LogDateAccount(username = "user", displayName = "Name", bio = "Cloud bio"),
                )
            val useCase =
                createUseCase(
                    profileRepository = profileRepo,
                    accountRepository = accountRepo,
                )

            val identity = useCase().first()

            assertEquals("Local bio", identity.bio)
        }

    @Test
    fun `bio falls back to cloud when local is null`() =
        runTest {
            val profileRepo =
                FakeProfileRepository(
                    LogDateProfile(bio = null),
                )
            val accountRepo =
                FakeAccountRepository(
                    LogDateAccount(username = "user", displayName = "Name", bio = "Cloud bio"),
                )
            val useCase =
                createUseCase(
                    profileRepository = profileRepo,
                    accountRepository = accountRepo,
                )

            val identity = useCase().first()

            assertEquals("Cloud bio", identity.bio)
        }

    @Test
    fun `profilePhotoUri from local profile`() =
        runTest {
            val profileRepo =
                FakeProfileRepository(
                    LogDateProfile(profilePhotoUri = "file:///photo.jpg"),
                )
            val useCase = createUseCase(profileRepository = profileRepo)

            val identity = useCase().first()

            assertEquals("file:///photo.jpg", identity.profilePhotoUri)
        }
}
