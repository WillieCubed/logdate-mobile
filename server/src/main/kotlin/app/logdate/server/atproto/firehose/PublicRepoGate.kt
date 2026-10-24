package app.logdate.server.atproto.firehose

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.shared.model.AccountStorageMode
import studio.hypertext.atproto.identity.AtprotoDid

/**
 * Decides whether an account's repository may be shown to people other than its owner.
 *
 * A repository is shown only while the account is active, keeps its journal in its personal space,
 * and has an identity other apps can look up. An account on legacy storage keeps every entry,
 * journal and draft as a record in the same repository, so it is never shown, not even for the
 * records that would be public: its repository is erased when the journal moves into a personal
 * space, and what other apps held of it would be left pointing at nothing. An account whose
 * `did:plc` has not been published to the PLC directory is not part of the network yet, so
 * nothing about it is announced.
 */
fun interface PublicRepoGate {
    /** True when the repository of [did] may be shown, as far as the account is concerned. */
    suspend fun mayShow(did: AtprotoDid): Boolean
}

/** The gate that reads the account's storage mode and state from [accounts]. */
class AccountPublicRepoGate(
    private val accounts: AccountRepository,
    private val identityConfig: AtprotoIdentityConfig,
) : PublicRepoGate {
    override suspend fun mayShow(did: AtprotoDid): Boolean = accounts.findByDid(did.toString())?.let(::mayShow) == true

    /** True when [account] is active, keeps its journal in its personal space, and can be looked up. */
    fun mayShow(account: Account): Boolean =
        account.isActive && account.storageMode == AccountStorageMode.PERSONAL_SPACE_V1 && hasPublicIdentity(account)

    /**
     * True when other apps can resolve [account]'s DID: a `did:web` is served by this server, and a
     * `did:plc` exists for them only once its operations are sent to the PLC directory.
     */
    fun hasPublicIdentity(account: Account): Boolean {
        val did = account.did ?: return false
        return did.startsWith("did:web:") || (did.startsWith("did:plc:") && identityConfig.publishesPlcOperations)
    }
}
