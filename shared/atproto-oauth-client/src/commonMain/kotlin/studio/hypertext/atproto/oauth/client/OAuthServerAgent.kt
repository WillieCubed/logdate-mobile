package studio.hypertext.atproto.oauth.client

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

/**
 * A validated token endpoint response.
 */
internal data class TokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long?,
    val scope: String,
    val sub: String,
)

/**
 * Talks to one authorization server on behalf of one DPoP key and client key: pushed
 * authorization requests, token requests, and revocation.
 *
 * Every request carries a fresh DPoP proof and a fresh `private_key_jwt` client assertion. When
 * the server answers `use_dpop_nonce`, the request is sent once more with the nonce it supplied.
 */
internal class OAuthServerAgent(
    val metadata: OAuthAuthorizationServerMetadata,
    private val dpopKey: JsonWebKey,
    private val clientKey: OAuthClientKey,
    private val clientId: String,
    private val httpClient: HttpClient,
    private val crypto: OAuthCrypto,
    private val nonces: DpopNonceCache,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun pushAuthorizationRequest(parameters: Map<String, String>): String {
        val endpoint =
            metadata.pushedAuthorizationRequestEndpoint
                ?: throw OAuthServerMetadataException(metadata.issuer, "no pushed_authorization_request_endpoint")
        val body = post("pushed_authorization_request", endpoint, parameters)
        return body.string("request_uri")
            ?: throw OAuthServerResponseException(
                "pushed_authorization_request",
                HttpStatusCode.Created.value,
                null,
                "response has no request_uri",
            )
    }

    suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
        redirectUri: String,
    ): TokenResponse =
        tokenResponse(
            post(
                "token",
                metadata.tokenEndpoint,
                mapOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "code_verifier" to codeVerifier,
                    "redirect_uri" to redirectUri,
                ),
            ),
        )

    suspend fun refresh(refreshToken: String): TokenResponse =
        tokenResponse(
            post(
                "token",
                metadata.tokenEndpoint,
                mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken),
            ),
        )

    /**
     * Revokes [token] (RFC 7009). Returns normally only when the server confirmed it.
     */
    suspend fun revoke(token: String) {
        val endpoint =
            metadata.revocationEndpoint
                ?: throw OAuthServerResponseException("revocation", null, null, "the server has no revocation endpoint")
        post("revocation", endpoint, mapOf("token" to token))
    }

    private suspend fun post(
        endpointName: String,
        url: String,
        parameters: Map<String, String>,
    ): JsonObject {
        var nonceRetried = false
        while (true) {
            val response = send(endpointName, url, parameters)
            val text = response.bodyAsText()
            response.headers[DPOP_NONCE_HEADER]?.let { nonces.put(url, it) }
            if (response.status.value in 200..299) {
                if (text.isBlank()) return JsonObject(emptyMap())
                return parseObject(endpointName, response.status.value, text)
            }
            val error = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            val errorCode = error?.string("error")
            if (errorCode == "use_dpop_nonce" && response.headers[DPOP_NONCE_HEADER] != null && !nonceRetried) {
                nonceRetried = true
                continue
            }
            throw OAuthServerResponseException(
                endpoint = endpointName,
                statusCode = response.status.value,
                error = errorCode,
                errorDescription = error?.string("error_description") ?: text.takeIf { error == null && it.isNotBlank() }?.take(200),
            )
        }
    }

    private suspend fun send(
        endpointName: String,
        url: String,
        fields: Map<String, String>,
    ): HttpResponse {
        val now = clock.now()
        val proof =
            crypto.createDpopProof(
                dpopKey = dpopKey,
                htm = "POST",
                htu = dpopHtu(url),
                nonce = nonces.get(url),
                accessToken = null,
                now = now,
            )
        val assertion = crypto.createClientAssertion(clientKey, clientId, metadata.issuer, now)
        return try {
            httpClient.submitForm(
                url = url,
                formParameters =
                    parameters {
                        fields.forEach { (name, value) -> append(name, value) }
                        append("client_id", clientId)
                        append("client_assertion_type", CLIENT_ASSERTION_TYPE_JWT_BEARER)
                        append("client_assertion", assertion)
                    },
            ) {
                header(DPOP_HEADER, proof)
                header(HttpHeaders.Accept, "application/json")
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw OAuthServerResponseException(endpointName, null, null, "request failed", exception)
        }
    }

    private fun parseObject(
        endpointName: String,
        status: Int,
        text: String,
    ): JsonObject =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
            throw OAuthServerResponseException(endpointName, status, null, "response is not a JSON object", it)
        }

    private fun tokenResponse(body: JsonObject): TokenResponse {
        val accessToken =
            body.string("access_token")?.takeIf { it.isNotEmpty() }
                ?: throw OAuthInvalidTokenResponseException("access_token is missing")
        val tokenType = body.string("token_type")
        if (!tokenType.equals("DPoP", ignoreCase = true)) {
            throw OAuthInvalidTokenResponseException("token_type is $tokenType, expected DPoP")
        }
        val scope = body.string("scope") ?: throw OAuthInvalidTokenResponseException("scope is missing")
        if (ATPROTO_SCOPE !in scope.split(' ')) {
            throw OAuthInvalidTokenResponseException("the granted scope \"$scope\" does not include $ATPROTO_SCOPE")
        }
        val sub = body.string("sub") ?: throw OAuthInvalidTokenResponseException("sub is missing")
        val expiresIn =
            body["expires_in"]?.let { value ->
                (value as? JsonPrimitive)?.longOrNull ?: throw OAuthInvalidTokenResponseException("expires_in is not a number")
            }
        return TokenResponse(
            accessToken = accessToken,
            refreshToken = body.string("refresh_token"),
            expiresInSeconds = expiresIn,
            scope = scope,
            sub = sub,
        )
    }
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
