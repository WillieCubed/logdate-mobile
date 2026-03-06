package app.logdate.server.auth

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
enum class IdentityProvider {
    PASSKEY,
    GOOGLE,
}

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class AccountIdentity(
    val id: Uuid,
    val accountId: Uuid,
    val provider: IdentityProvider,
    val providerSubject: String,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val createdAt: Instant,
    val lastSignInAt: Instant? = null,
    val metadataJson: String = "{}",
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class AccountLinkEvent(
    val id: Uuid,
    val accountId: Uuid,
    val provider: IdentityProvider,
    val providerSubject: String,
    val reason: String,
    val ipHash: String? = null,
    val userAgentHash: String? = null,
    val createdAt: Instant,
    val metadataJson: String = "{}",
)

@OptIn(ExperimentalUuidApi::class)
interface AccountIdentityRepository {
    suspend fun save(identity: AccountIdentity): AccountIdentity

    suspend fun findByProviderSubject(
        provider: IdentityProvider,
        providerSubject: String,
    ): AccountIdentity?

    suspend fun findByAccountId(accountId: Uuid): List<AccountIdentity>

    suspend fun findByVerifiedEmail(email: String): List<AccountIdentity>

    suspend fun touchLastSignIn(identityId: Uuid): Boolean

    suspend fun saveLinkEvent(event: AccountLinkEvent): AccountLinkEvent
}

@OptIn(ExperimentalUuidApi::class)
class InMemoryAccountIdentityRepository : AccountIdentityRepository {
    private val identities = mutableMapOf<Uuid, AccountIdentity>()
    private val providerIndex = mutableMapOf<Pair<IdentityProvider, String>, Uuid>()
    private val accountIndex = mutableMapOf<Uuid, MutableSet<Uuid>>()
    private val linkEvents = mutableListOf<AccountLinkEvent>()

    override suspend fun save(identity: AccountIdentity): AccountIdentity {
        val key = identity.provider to identity.providerSubject
        val existingId = providerIndex[key]
        val saved =
            if (existingId != null) {
                val updated = identity.copy(id = existingId)
                identities[existingId] = updated
                updated
            } else {
                identities[identity.id] = identity
                providerIndex[key] = identity.id
                accountIndex.computeIfAbsent(identity.accountId) { mutableSetOf() }.add(identity.id)
                identity
            }
        return saved
    }

    override suspend fun findByProviderSubject(
        provider: IdentityProvider,
        providerSubject: String,
    ): AccountIdentity? {
        val id = providerIndex[provider to providerSubject] ?: return null
        return identities[id]
    }

    override suspend fun findByAccountId(accountId: Uuid): List<AccountIdentity> =
        accountIndex[accountId].orEmpty().mapNotNull { identities[it] }

    override suspend fun findByVerifiedEmail(email: String): List<AccountIdentity> =
        identities.values.filter { it.emailVerified && it.email?.equals(email, ignoreCase = true) == true }

    override suspend fun touchLastSignIn(identityId: Uuid): Boolean {
        val identity = identities[identityId] ?: return false
        identities[identityId] =
            identity.copy(
                lastSignInAt =
                    kotlin.time.Clock.System
                        .now(),
            )
        return true
    }

    override suspend fun saveLinkEvent(event: AccountLinkEvent): AccountLinkEvent {
        linkEvents += event
        return event
    }
}
