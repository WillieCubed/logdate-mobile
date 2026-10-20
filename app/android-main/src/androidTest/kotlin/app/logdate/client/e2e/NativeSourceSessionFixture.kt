package app.logdate.client.e2e

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import kotlin.uuid.Uuid

internal fun nativeSourceSession(
    sessionId: String,
    owner: String,
): UserSession {
    Uuid.parse(sessionId)
    return UserSession("native-acceptance-$sessionId-access", "native-acceptance-$sessionId-refresh", owner)
}

internal suspend fun clearNativeSourceSession(
    sessions: SessionStorage,
    expected: UserSession,
): Boolean {
    val current = sessions.getOriginBoundSession()
    if (current == null) {
        check(sessions.getSession() == null) { "Refusing cleanup of credentials without fixture ownership" }
        return false
    }
    check(current.session == expected) { "Refusing cleanup of a session owned by another sign-in" }
    check(sessions.clearSessionIfCurrent(current)) { "Source session changed during fixture cleanup" }
    return true
}
