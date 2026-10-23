package app.logdate.client.sync.location

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.sameOwnerId
import app.logdate.client.sync.FullSyncScope
import app.logdate.client.sync.space.mappers.HistoryScope
import app.logdate.client.sync.space.mappers.HistoryScopeProvider
import app.logdate.shared.config.LogDateConfigRepository
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** Selects local history custody independently of the server's account serialization. */
internal class CanonicalHistoryScopeProvider(
    private val sessions: SessionStorage,
    private val config: LogDateConfigRepository,
    private val owner: CanonicalOwnerProvider,
) : HistoryScopeProvider {
    override suspend fun current(): HistoryScope? {
        val bound = sessions.getOriginBoundSession() ?: return null
        val origin = config.getCurrentBackendUrl().trimEnd('/')
        if (bound.origin.trimEnd('/') != origin) return null
        val existing = owner.readExistingOwner() ?: return null
        if (!sameOwnerId(existing.rawId, bound.session.accountId)) return null
        val scope = HistoryScope(existing.rawId, origin)
        coroutineContext[HistorySyncBinding]?.let { pinned ->
            if (scope != pinned.scope || bound.session != pinned.session) return null
        }
        coroutineContext[FullSyncScope]?.let { run ->
            val expected = run.owner ?: return null
            if (expected.serverOrigin != origin || !sameOwnerId(expected.ownerId, existing.rawId)) return null
        }
        if (sessions.getOriginBoundSession() != bound ||
            config.getCurrentBackendUrl().trimEnd('/') != origin ||
            owner.readExistingOwner() != existing
        ) {
            return null
        }
        return scope
    }
}

/** Local adapters share the exact scope and credentials captured before a history phase. */
internal class HistorySyncBinding(
    val scope: HistoryScope,
    val session: UserSession,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<HistorySyncBinding>
}
