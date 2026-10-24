package app.logdate.server.atproto.firehose

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRemovalEvents
import app.logdate.server.auth.AccountRepository
import app.logdate.server.identity.IdentityEvents
import studio.hypertext.atproto.identity.AtprotoDid
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tells the rest of the network about accounts: that one is now public, that its identity changed,
 * or that it is gone. It decides nothing about what is public; [RepoEventSequencer] refuses accounts
 * the server does not show.
 *
 * The first time an account is announced, the server also asks the configured relays to crawl it, at
 * most once every few minutes, since a relay that follows the server hears about later accounts from
 * the stream.
 */
@OptIn(ExperimentalUuidApi::class)
class PublicRepoAnnouncer(
    private val accounts: AccountRepository,
    private val sequencer: RepoEventSequencer,
    private val crawler: RelayCrawler,
) : IdentityEvents,
    AccountRemovalEvents {
    /** An account got an identity or its identity changed. Announces the account first if it never was. */
    override suspend fun identityChanged(account: Account) {
        val did = account.did?.let(AtprotoDid::parse)?.getOrNull() ?: return
        if (sequencer.hasAnnounced(did)) {
            sequencer.identityChanged(did, account.handle)
            return
        }
        if (sequencer.announce(did, account.handle)) crawler.requestIfDue()
    }

    /** The identity of [accountId] was published, so other apps can now find the account. */
    suspend fun accountPublished(accountId: Uuid) {
        accounts.findById(accountId)?.let { identityChanged(it) }
    }

    /** The account [did] no longer exists on this server; [status] is `deleted` or `deactivated`. */
    override suspend fun accountRemoved(
        did: String,
        status: String,
    ) {
        val parsed = AtprotoDid.parse(did).getOrNull() ?: return
        sequencer.accountStatusChanged(parsed, active = false, status = status)
    }
}
