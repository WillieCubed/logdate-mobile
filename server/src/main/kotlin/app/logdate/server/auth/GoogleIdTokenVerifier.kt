package app.logdate.server.auth

import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

@Serializable
data class GoogleIdTokenClaims(
    val subject: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String? = null,
    val picture: String? = null,
    val issuer: String,
    val audience: String,
    val expiresAtEpochSeconds: Long,
    val issuedAtEpochSeconds: Long,
)

interface GoogleIdTokenVerifier {
    fun isConfigured(): Boolean = true

    suspend fun verify(
        idToken: String,
        nonce: String?,
    ): GoogleIdTokenClaims?
}

class HttpGoogleIdTokenVerifier(
    private val allowedClientIds: Set<String>,
    private val acceptedIssuers: Set<String> = setOf("https://accounts.google.com", "accounts.google.com"),
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : GoogleIdTokenVerifier {
    private val json = Json { ignoreUnknownKeys = true }

    override fun isConfigured(): Boolean = allowedClientIds.isNotEmpty()

    override suspend fun verify(
        idToken: String,
        nonce: String?,
    ): GoogleIdTokenClaims? {
        if (idToken.isBlank()) {
            return null
        }

        if (allowedClientIds.isEmpty()) {
            Napier.e("GOOGLE_OIDC_CLIENT_IDS is not configured")
            return null
        }

        val encodedToken = URLEncoder.encode(idToken, StandardCharsets.UTF_8)
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=$encodedToken"))
                .GET()
                .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                Napier.w("Google tokeninfo returned ${response.statusCode()}")
                null
            } else {
                parseAndValidate(response.body(), nonce)
            }
        } catch (e: Exception) {
            Napier.e("Failed to verify Google ID token", e)
            null
        }
    }

    private fun parseAndValidate(
        body: String,
        nonce: String?,
    ): GoogleIdTokenClaims? {
        val obj = json.parseToJsonElement(body).jsonObject

        val subject = obj["sub"]?.jsonPrimitive?.content ?: return null
        val email = obj["email"]?.jsonPrimitive?.content ?: return null
        val emailVerified = obj["email_verified"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val issuer = obj["iss"]?.jsonPrimitive?.content ?: return null
        val audience = obj["aud"]?.jsonPrimitive?.content ?: return null
        val exp = obj["exp"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val iat = obj["iat"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val tokenNonce = obj["nonce"]?.jsonPrimitive?.content
        val now = System.currentTimeMillis() / 1000

        if (!acceptedIssuers.contains(issuer)) {
            Napier.w("Google issuer not allowed: $issuer")
            return null
        }

        if (!allowedClientIds.contains(audience)) {
            Napier.w("Google audience not allowed: $audience")
            return null
        }

        if (exp <= now) {
            Napier.w("Google ID token is expired")
            return null
        }

        if (nonce != null && tokenNonce != null && nonce != tokenNonce) {
            Napier.w("Google ID token nonce mismatch")
            return null
        }

        return GoogleIdTokenClaims(
            subject = subject,
            email = email,
            emailVerified = emailVerified,
            name = obj["name"]?.jsonPrimitive?.content,
            picture = obj["picture"]?.jsonPrimitive?.content,
            issuer = issuer,
            audience = audience,
            expiresAtEpochSeconds = exp,
            issuedAtEpochSeconds = iat,
        )
    }
}

class FakeGoogleIdTokenVerifier(
    private val tokens: Map<String, GoogleIdTokenClaims>,
    private val configured: Boolean = true,
) : GoogleIdTokenVerifier {
    override fun isConfigured(): Boolean = configured

    override suspend fun verify(
        idToken: String,
        nonce: String?,
    ): GoogleIdTokenClaims? = tokens[idToken]
}
