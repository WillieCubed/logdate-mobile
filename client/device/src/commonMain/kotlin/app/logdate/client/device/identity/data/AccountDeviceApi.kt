package app.logdate.client.device.identity.data

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.shared.model.RegisterDeviceRequest
import app.logdate.shared.model.RegisteredDevice
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

class AccountDeviceApi(
    private val client: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(session: OriginBoundSession): List<RegisteredDevice> {
        val response =
            client.get("${session.origin.trimEnd('/')}/api/v1/devices") {
                header(HttpHeaders.Authorization, "Bearer ${session.session.accessToken}")
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
            client.put("${session.origin.trimEnd('/')}/api/v1/devices/$id") {
                header(HttpHeaders.Authorization, "Bearer ${session.session.accessToken}")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
        return response.status.value in 200..299
    }
}
