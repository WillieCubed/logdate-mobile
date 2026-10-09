package app.logdate.client.device.identity.data

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.shared.model.RegisterDeviceRequest
import app.logdate.shared.model.RegisteredDevice
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

class AccountDeviceApi(
    private val client: HttpClient,
    private val sessions: SessionStorage? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshMutex = Mutex()

    suspend fun list(session: OriginBoundSession): List<RegisteredDevice> {
        val response =
            authenticated(session) { bound ->
                client.get("${bound.origin.trimEnd('/')}/api/v1/devices") {
                    expectSuccess = false
                    header(HttpHeaders.Authorization, "Bearer ${bound.session.accessToken}")
                }
            }
        check(response.status.value in 200..299) { "Device list request failed (${response.status.value})" }
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun register(
        session: OriginBoundSession,
        id: Uuid,
        request: RegisterDeviceRequest,
    ): Boolean {
        val response =
            authenticated(session) { bound ->
                client.put("${bound.origin.trimEnd('/')}/api/v1/devices/$id") {
                    expectSuccess = false
                    header(HttpHeaders.Authorization, "Bearer ${bound.session.accessToken}")
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(request))
                }
            }
        return response.status.value in 200..299
    }

    private suspend fun authenticated(
        captured: OriginBoundSession,
        send: suspend (OriginBoundSession) -> HttpResponse,
    ): HttpResponse {
        val bound = currentSession(captured)
        val response = send(bound)
        currentSession(bound)
        if (response.status != HttpStatusCode.Unauthorized || sessions == null) return response
        val refreshed = refresh(bound) ?: return response
        val retried = send(currentSession(refreshed))
        currentSession(refreshed)
        return retried
    }

    private fun currentSession(captured: OriginBoundSession): OriginBoundSession {
        if (sessions == null) return captured
        val current = sessions.getOriginBoundSession()
        if (current == null ||
            current.origin != captured.origin ||
            current.session.accountId != captured.session.accountId ||
            current.session.refreshToken != captured.session.refreshToken
        ) {
            throw CancellationException("The device request's account session changed")
        }
        return current
    }

    private suspend fun refresh(expired: OriginBoundSession): OriginBoundSession? =
        refreshMutex.withLock {
            val storage = sessions ?: return@withLock null
            val current = currentSession(expired)
            if (current.session.accessToken != expired.session.accessToken) return@withLock current
            val response =
                client.post("${expired.origin.trimEnd('/')}/api/v1/auth/token/refresh") {
                    expectSuccess = false
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(RefreshRequest(expired.session.refreshToken)))
                }
            currentSession(expired)
            if (response.status.value !in 200..299) return@withLock null
            val refreshed = json.decodeFromString<RefreshResponse>(response.bodyAsText())
            val accessToken = refreshed.data?.accessToken?.takeIf { it.isNotBlank() }
            if (!refreshed.success || accessToken == null) return@withLock null
            val updated = expired.session.copy(accessToken = accessToken)
            if (storage.replaceSessionIfCurrent(expired, updated)) {
                expired.copy(session = updated)
            } else {
                currentSession(expired).takeIf { it.session.accessToken != expired.session.accessToken }
            }
        }

    @Serializable
    private data class RefreshRequest(
        val refreshToken: String,
    )

    @Serializable
    private data class RefreshResponse(
        val success: Boolean,
        val data: RefreshData? = null,
    )

    @Serializable
    private data class RefreshData(
        val accessToken: String,
    )
}
