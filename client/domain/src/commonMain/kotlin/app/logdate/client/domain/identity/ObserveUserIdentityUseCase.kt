package app.logdate.client.domain.identity

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.repository.account.AccountRepository
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.client.repository.user.UserStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Instant

/**
 * Combines local profile, user state, cloud account, and session data into a
 * single [ResolvedUserIdentity] flow with local-first semantics.
 *
 * Read-only — writes continue through the individual repositories.
 */
class ObserveUserIdentityUseCase(
    private val profileRepository: ProfileRepository,
    private val userStateRepository: UserStateRepository,
    private val accountRepository: AccountRepository,
    private val sessionStorage: SessionStorage,
) {
    operator fun invoke(): Flow<ResolvedUserIdentity> =
        combine(
            profileRepository.currentProfile,
            userStateRepository.userData,
            accountRepository.currentAccount,
            sessionStorage.getSessionFlow(),
        ) { localProfile, userData, cloudAccount, session ->
            val resolvedDisplayName =
                localProfile.displayName.ifEmpty {
                    cloudAccount?.displayName ?: ""
                }
            val resolvedBirthday =
                localProfile.birthday
                    ?: userData.birthday.takeIf { it != Instant.DISTANT_PAST }
            val resolvedOnboardedDate =
                userData.onboardedDate.takeIf {
                    it != Instant.DISTANT_PAST
                }
            ResolvedUserIdentity(
                displayName = resolvedDisplayName,
                username = cloudAccount?.username?.takeIf { it.isNotEmpty() },
                profilePhotoUri = localProfile.profilePhotoUri,
                bio = localProfile.bio ?: cloudAccount?.bio,
                birthday = resolvedBirthday,
                onboardedDate = resolvedOnboardedDate,
                isAuthenticated = session != null,
                cloudAccountId = cloudAccount?.id?.toString(),
            )
        }
}
