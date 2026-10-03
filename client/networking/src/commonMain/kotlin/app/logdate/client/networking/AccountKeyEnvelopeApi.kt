package app.logdate.client.networking

import app.logdate.shared.model.ServerProtocolFeature
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface AccountKeyEnvelopeApi {
    suspend fun isSupported(apiBaseUrl: String): Result<Boolean>

    suspend fun fetch(
        apiBaseUrl: String,
        accessToken: String,
        credentialId: String,
    ): Result<String?>

    suspend fun store(
        apiBaseUrl: String,
        accessToken: String,
        credentialId: String,
        ciphertext: String,
    ): Result<Unit>
}

class DefaultAccountKeyEnvelopeApi(
    private val client: HttpClient,
) : AccountKeyEnvelopeApi {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class Envelope(
        val ciphertext: String,
    )

    @Serializable private data class Info(
        val data: Descriptor,
    )

    @Serializable private data class Descriptor(
        val protocolFeatures: List<String> = emptyList(),
    )

    override suspend fun isSupported(apiBaseUrl: String): Result<Boolean> =
        runCatching {
            val response = client.get("${apiBaseUrl.trimEnd('/')}/server/info")
            require(response.status.value == 200) { "Secure unlock server discovery failed" }
            json
                .decodeFromString<Info>(response.bodyAsText())
                .data.protocolFeatures
                .contains(ServerProtocolFeature.ENCRYPTED_ACCOUNT_KEYS_V1)
        }

    override suspend fun fetch(
        apiBaseUrl: String,
        accessToken: String,
        credentialId: String,
    ): Result<String?> =
        runCatching {
            val response =
                client.get(path(apiBaseUrl, credentialId)) {
                    header("Authorization", "Bearer $accessToken")
                    header("Cache-Control", "no-store")
                }
            when (response.status.value) {
                200 -> json.decodeFromString<Envelope>(response.bodyAsText()).ciphertext
                404 -> null
                else -> throw PasskeyApiException("ENVELOPE_FETCH_FAILED", "Secure journal unlock could not be completed")
            }
        }

    override suspend fun store(
        apiBaseUrl: String,
        accessToken: String,
        credentialId: String,
        ciphertext: String,
    ): Result<Unit> =
        runCatching {
            val response =
                client.put(path(apiBaseUrl, credentialId)) {
                    header("Authorization", "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(Envelope(ciphertext)))
                }
            if (response.status.value != 204) throw PasskeyApiException("ENVELOPE_STORE_FAILED", "Secure journal unlock could not be saved")
        }

    private fun path(
        apiBaseUrl: String,
        credentialId: String,
    ): String {
        require(credentialId.matches(Regex("[A-Za-z0-9_-]{1,2048}"))) { "Invalid credential ID" }
        return "${apiBaseUrl.trimEnd('/')}/account/key-envelopes/$credentialId"
    }
}
