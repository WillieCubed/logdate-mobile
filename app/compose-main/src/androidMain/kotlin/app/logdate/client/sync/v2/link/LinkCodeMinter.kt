package app.logdate.client.sync.v2.link

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.networking.DeviceLinkApiClientContract
import app.logdate.client.networking.DeviceLinkApiException
import app.logdate.client.sync.cloud.SessionRefreshingAccountRepository
import app.logdate.shared.model.devices.DeviceLinkCodeResponse
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/**
 * Mints a one-time link code with this phone's own session. An access token that expired is refreshed
 * once; the refreshed session is saved only if it is still the one the phone has.
 */
class LinkCodeMinter(
    private val sessions: SessionStorage,
    private val clientsFor: (OriginBoundSession) -> Clients?,
) {
    data class Clients(
        val links: DeviceLinkApiClientContract,
        val refresher: SessionRefreshingAccountRepository?,
    )

    /** For clients already fixed to one endpoint, including test doubles. */
    constructor(
        links: DeviceLinkApiClientContract,
        sessions: SessionStorage,
        refresher: SessionRefreshingAccountRepository?,
    ) : this(sessions, { Clients(links, refresher) })

    suspend fun mint(): Result<DeviceLinkCodeResponse> {
        val bound = sessions.getOriginBoundSession() ?: return Result.failure(IllegalStateException("This phone is not signed in"))
        val (links, refresher) = clientsFor(bound) ?: return changedSession()
        if (sessions.getOriginBoundSession() != bound) return changedSession()
        val first = links.mintLinkCode(bound.session.accessToken)
        (first.exceptionOrNull() as? CancellationException)?.let { throw it }
        if (sessions.getOriginBoundSession() != bound) return changedSession()
        val refused = first.exceptionOrNull() as? DeviceLinkApiException ?: return first
        if (refused.status != HTTP_UNAUTHORIZED || refresher == null) return first
        val refreshed =
            refresher.refreshSession(bound.session.refreshToken).getOrElse { error ->
                if (error is CancellationException) throw error
                Napier.w("Refreshing this phone's session to mint a link code failed", error)
                return Result.failure(error)
            }
        val updated =
            bound.session.copy(
                accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken ?: bound.session.refreshToken,
            )
        if (!sessions.replaceSessionIfCurrent(bound, updated)) {
            return Result.failure(IllegalStateException("The session changed while minting a link code"))
        }
        val second = links.mintLinkCode(updated.accessToken)
        (second.exceptionOrNull() as? CancellationException)?.let { throw it }
        if (sessions.getOriginBoundSession() != bound.copy(session = updated)) return changedSession()
        return second
    }

    private fun changedSession(): Result<DeviceLinkCodeResponse> =
        Result.failure(IllegalStateException("The session changed while minting a link code"))

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
