package app.logdate.client.device.storage

import app.logdate.client.datastore.UserSession
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
private data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val accountId: String,
)

internal object SessionRecordKeys {
    const val RECORD = "session_record_v2"
    const val ACCESS_TOKEN = "session_access_token"
    const val REFRESH_TOKEN = "session_refresh_token"
    const val ACCOUNT_ID = "session_account_id"
}

internal suspend fun SecureStorage.persistStoredSession(
    origin: String,
    session: UserSession,
) {
    putString(scopedSessionKey(SessionRecordKeys.RECORD, origin), encodeStoredSession(session))
}

internal fun encodeStoredSession(session: UserSession): String =
    Json.encodeToString(StoredSession(session.accessToken, session.refreshToken, session.accountId))

internal suspend fun SecureStorage.loadStoredSession(
    backendUrl: String,
    canRestore: suspend () -> Boolean,
): UserSession? {
    try {
        if (!canRestore()) return null
        val record = getString(scopedSessionKey(SessionRecordKeys.RECORD, backendUrl))
        if (record != null) {
            if (record == "null") return null
            val stored = Json.decodeFromString<StoredSession>(record)
            return UserSession(stored.accessToken, stored.refreshToken, stored.accountId)
        }
        migrateLegacySessionKeys(backendUrl)
        val accessToken = getString(scopedSessionKey(SessionRecordKeys.ACCESS_TOKEN, backendUrl))
        val refreshToken = getString(scopedSessionKey(SessionRecordKeys.REFRESH_TOKEN, backendUrl))
        val accountId = getString(scopedSessionKey(SessionRecordKeys.ACCOUNT_ID, backendUrl))
        if (accessToken == null || refreshToken == null || accountId == null) return null
        return UserSession(accessToken, refreshToken, accountId).also { persistStoredSession(backendUrl, it) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Napier.e("SESSION_STORAGE_READ_FAILED")
        return null
    }
}

private suspend fun SecureStorage.migrateLegacySessionKeys(backendUrl: String) {
    if (getString(scopedSessionKey(SessionRecordKeys.ACCESS_TOKEN, backendUrl)) != null) return
    val access = getString(SessionRecordKeys.ACCESS_TOKEN) ?: return
    val refresh = getString(SessionRecordKeys.REFRESH_TOKEN) ?: return
    val account = getString(SessionRecordKeys.ACCOUNT_ID) ?: return
    putString(scopedSessionKey(SessionRecordKeys.ACCESS_TOKEN, backendUrl), access)
    putString(scopedSessionKey(SessionRecordKeys.REFRESH_TOKEN, backendUrl), refresh)
    putString(scopedSessionKey(SessionRecordKeys.ACCOUNT_ID, backendUrl), account)
    remove(SessionRecordKeys.ACCESS_TOKEN)
    remove(SessionRecordKeys.REFRESH_TOKEN)
    remove(SessionRecordKeys.ACCOUNT_ID)
}

@OptIn(ExperimentalEncodingApi::class)
internal fun scopedSessionKey(
    baseKey: String,
    backendUrl: String,
): String = "${baseKey}_${Base64.UrlSafe.encode(backendUrl.trim().encodeToByteArray()).trimEnd('=')}"
