package studio.hypertext.atproto.oauth.client

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.prepareForm
import io.ktor.client.request.header
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
 *
 * [httpClient] must be the plugin-free client from [protocolHttpClient], so no caller plugin sees
 * the tokens or client assertions.
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
    private val strictDpopNonces: Boolean = false,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Pushes the authorization request and returns its `request_uri`.
     *
     * With [strictDpopNonces], a response without a `DPoP-Nonce` header is refused. Every sign-in
     * starts here, so a server that does not use nonces is refused before it issues any tokens.
     */
    suspend fun pushAuthorizationRequest(parameters: Map<String, String>): String {
        val endpoint =
            metadata.pushedAuthorizationRequestEndpoint
                ?: throw OAuthServerMetadataException(metadata.issuer, "no pushed_authorization_request_endpoint")
        val response = post("pushed_authorization_request", endpoint, parameters)
        if (strictDpopNonces && response.nonce == null) {
            throw OAuthServerResponseException(
                "pushed_authorization_request",
                response.status,
                null,
                "the response has no DPoP-Nonce header, which strict DPoP nonces require",
            )
        }
        return response.body.string("request_uri")
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
            ).body,
        )

    suspend fun refresh(refreshToken: String): TokenResponse =
        tokenResponse(
            post(
                "token",
                metadata.tokenEndpoint,
                mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken),
            ).body,
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

    /**
     * POSTs [parameters] to [url]. When the server answers `use_dpop_nonce` with a nonce other than
     * the one the proof carried, the request is sent once more with the new nonce.
     *
     * Exception messages carry the OAuth `error` and `error_description` only, never the raw body,
     * which may contain tokens.
     */
    private suspend fun post(
        endpointName: String,
        url: String,
        parameters: Map<String, String>,
    ): PostResult {
        var nonce = nonces.get(url)
        var nonceRetried = false
        while (true) {
            val response = send(endpointName, url, parameters, nonce)
            response.nonce?.let { nonces.put(url, it) }
            if (response.status in 200..299) {
                val body = response.body.takeIf { it.isNotBlank() }?.let { parseObject(endpointName, response.status, it) }
                return PostResult(response.status, response.nonce, body ?: JsonObject(emptyMap()))
            }
            val error = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
            val errorCode = error?.string("error")
            val freshNonce = response.nonce?.takeIf { it != nonce }
            if (errorCode == "use_dpop_nonce" && freshNonce != null && !nonceRetried) {
                nonceRetried = true
                nonce = freshNonce
                continue
            }
            throw OAuthServerResponseException(
                endpoint = endpointName,
                statusCode = response.status,
                error = errorCode?.let(::printableServerText),
                errorDescription = error?.string("error_description")?.let(::printableServerText),
            )
        }
    }

    private class PostResult(
        val status: Int,
        val nonce: String?,
        val body: JsonObject,
    )

    private class ServerResponse(
        val status: Int,
        val nonce: String?,
        val body: String,
    )

    private suspend fun send(
        endpointName: String,
        url: String,
        fields: Map<String, String>,
        nonce: String?,
    ): ServerResponse {
        val now = clock.now()
        val proof =
            crypto.createDpopProof(
                dpopKey = dpopKey,
                htm = "POST",
                htu = dpopHtu(url),
                nonce = nonce,
                accessToken = null,
                now = now,
            )
        val assertion = crypto.createClientAssertion(clientKey, clientId, metadata.issuer, now)
        val form =
            parameters {
                fields.forEach { (name, value) -> append(name, value) }
                append("client_id", clientId)
                append("client_assertion_type", CLIENT_ASSERTION_TYPE_JWT_BEARER)
                append("client_assertion", assertion)
            }
        return try {
            httpClient
                .prepareForm(url = url, formParameters = form) {
                    header(DPOP_HEADER, proof)
                    header(HttpHeaders.Accept, "application/json")
                }.execute { response ->
                    ServerResponse(
                        status = response.status.value,
                        nonce = response.headers[DPOP_NONCE_HEADER],
                        body = response.bodyAsTextCapped(MAX_AUTHORIZATION_SERVER_RESPONSE_BYTES),
                    )
                }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ResponseTooLargeException) {
            throw OAuthServerResponseException(endpointName, null, null, "the response is larger than ${exception.limit} bytes")
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
            // The parser's message quotes the body, which may hold tokens, so it is not kept as the cause.
            throw OAuthServerResponseException(endpointName, status, null, "response is not a JSON object")
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

/**
 * Shortens server-supplied text for an exception message and drops control characters, so a
 * hostile server cannot flood or forge log lines.
 */
private fun printableServerText(text: String): String =
    text
        .filter { !it.isISOControl() }
        .let { if (it.length > MAX_SERVER_TEXT_LENGTH) it.take(MAX_SERVER_TEXT_LENGTH) + "…" else it }

private const val MAX_SERVER_TEXT_LENGTH = 300
