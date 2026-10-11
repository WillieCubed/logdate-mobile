package app.logdate.client.sync.v2

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** A session store for the phone's version 2 tests, bound to one server. */
class FakeSessions(
    bound: OriginBoundSession?,
) : SessionStorage {
    private val state = MutableStateFlow(bound)
    var replaced: UserSession? = null

    var bound: OriginBoundSession?
        get() = state.value
        set(value) {
            state.value = value
        }

    override fun getSession(): UserSession? = state.value?.session

    override fun getOriginBoundSession(): OriginBoundSession? = state.value

    override suspend fun replaceSessionIfCurrent(
        expected: OriginBoundSession,
        updated: UserSession,
    ): Boolean {
        if (state.value != expected) return false
        state.value = expected.copy(session = updated)
        replaced = updated
        return true
    }

    override fun getSessionFlow(): Flow<UserSession?> = state.map { it?.session }

    override suspend fun hasValidSession(): Boolean = state.value != null

    override suspend fun saveSession(session: UserSession) {
        state.value = OriginBoundSession(state.value?.origin ?: "https://logdate.test", session)
    }

    override suspend fun clearSession() {
        state.value = null
    }
}
