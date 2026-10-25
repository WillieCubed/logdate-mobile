package app.logdate.client.repository.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Proof from the server that [accountId] confirmed with a passkey at [receivedAt], handed over with a
 * passkey sign-in or sign-up. It can be spent once, in place of another passkey prompt, to register
 * the account's first recovery key.
 */
data class PasskeyConfirmation(
    val accountId: String,
    val grant: String,
    val receivedAt: Instant,
)

/**
 * Holds the latest [PasskeyConfirmation] so a recovery key can be registered with the passkey prompt
 * the person already answered when signing in, instead of asking again.
 *
 * The server accepts a confirmation for five minutes; this hands one out only while it is
 * comfortably inside that, so a request does not leave with a confirmation that expires on the way.
 */
class PasskeyConfirmations(
    private val clock: Clock = Clock.System,
    private val usableFor: Duration = 4.minutes,
) {
    private val _latest = MutableStateFlow<PasskeyConfirmation?>(null)

    /** The latest unspent confirmation, which may be too old to use; `null` before any or once spent. */
    val latest: StateFlow<PasskeyConfirmation?> = _latest.asStateFlow()

    /** Records the [grant] from a passkey sign-in or sign-up for [accountId]. */
    fun record(
        accountId: String,
        grant: String,
    ) {
        _latest.value = PasskeyConfirmation(accountId, grant, clock.now())
    }

    /** The grant for [accountId] if one is still usable, without spending it. */
    fun usableGrant(accountId: String): String? =
        _latest.value
            ?.takeIf { it.accountId == accountId && clock.now() - it.receivedAt < usableFor }
            ?.grant

    /** Forgets [grant] once the server has seen it, since the server accepts each one only once. */
    fun spend(grant: String) {
        _latest.update { current -> current?.takeUnless { it.grant == grant } }
    }
}
