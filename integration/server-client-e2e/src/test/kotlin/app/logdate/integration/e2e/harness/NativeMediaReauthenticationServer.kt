@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.harness

import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.networking.PasskeyApiException
import app.logdate.integration.e2e.fixtures.SyntheticPasskeyAccount
import app.logdate.server.auth.TokenService
import app.logdate.shared.model.PasskeyAssertionAuthenticatorResponse
import app.logdate.shared.model.PasskeyAssertionResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/** The owned server signs a real ceremony; Android still uses its ordinary sign-in repository. */
internal class NativeMediaReauthenticationServer(
    private val control: Path,
    private val account: SyntheticPasskeyAccount,
    private val origin: String,
    private val tokens: TokenService,
    private val passkeys: PasskeyApiClientContract,
    private val publish: (Path, String) -> Unit,
) {
    private val observedClientRefresh = AtomicBoolean(false)

    fun observeRequest(path: String) {
        if (path != "/api/v1/auth/token/refresh" || !Files.isRegularFile(control.resolve("media-reauth-expired-session.json"))) return
        if (observedClientRefresh.compareAndSet(false, true)) {
            Files.createFile(
                control.resolve("media-reauth-client-refresh-observed"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
            )
        }
    }

    fun requireCompleted() {
        check(observedClientRefresh.get()) { "The client must encounter its revoked refresh token" }
        check(Files.isRegularFile(control.resolve("media-reauth-assertion-response.json")))
        check(Files.isRegularFile(control.resolve("media-reauth-complete")))
    }

    suspend fun processRequests() {
        revokeRefreshToken()
        answerPasskeyChallenge()
    }

    private fun request(name: String): Map<String, kotlinx.serialization.json.JsonElement>? {
        val path = control.resolve(name)
        if (!Files.exists(path)) return null
        require(Files.isRegularFile(path) && Files.size(path) in 1..32_768)
        require(Files.getPosixFilePermissions(path) == PosixFilePermissions.fromString("rw-------"))
        val request = Json.parseToJsonElement(Files.readString(path)).jsonObject
        require(
            request.getValue("owner").jsonPrimitive.content ==
                account.response.data.account.id
                    .toString(),
        )
        require(request.getValue("origin").jsonPrimitive.content == origin)
        Uuid.parse(request.getValue("operationId").jsonPrimitive.content)
        return request
    }

    private suspend fun revokeRefreshToken() {
        val destination = control.resolve("media-reauth-expired-session.json")
        if (Files.exists(destination)) return
        val request = request("media-reauth-revoke-request.json") ?: return
        val owner =
            account.response.data.account.id
                .toString()
        val refresh = request.getValue("refreshToken").jsonPrimitive.content
        check(tokens.validateRefreshToken(refresh) == owner) { "Fixture refresh must belong to the retained owner" }
        passkeys.logout(refresh).getOrThrow()
        val refusal = passkeys.refreshToken(refresh).exceptionOrNull()
        check(refusal is PasskeyApiException && refusal.errorCode == "REFRESH_TOKEN_REVOKED") {
            "The production refresh route must reject the revoked token"
        }
        val operationId = request.getValue("operationId").jsonPrimitive.content
        val expired = tokens.generateEnrollmentTokens(owner, null, operationId, Clock.System.now() - 2.hours).accessToken
        check(tokens.validateAccessToken(expired) == null) { "The fixture access token must already be expired" }
        publish(
            destination,
            buildJsonObject {
                put("owner", owner)
                put("origin", origin)
                put("operationId", operationId)
                put("expiredAccessToken", expired)
                put("refreshRevokedByServer", true)
            }.toString(),
        )
    }

    private fun answerPasskeyChallenge() {
        val destination = control.resolve("media-reauth-assertion-response.json")
        if (Files.exists(destination)) return
        val request = request("media-reauth-assertion-request.json") ?: return
        check(Files.isRegularFile(control.resolve("media-reauth-expired-session.json")))
        require(request.getValue("rpId").jsonPrimitive.content == java.net.URI(account.authenticator.origin).host)
        val challenge = request.getValue("challenge").jsonPrimitive.content
        require(challenge.length in 16..4096 && challenge.matches(Regex("[A-Za-z0-9_-]+")))
        val (clientData, authenticatorData, signature) = account.authenticator.assert(challenge)
        val assertion =
            PasskeyAssertionResponse(
                account.authenticator.credentialIdB64,
                account.authenticator.credentialIdB64,
                PasskeyAssertionAuthenticatorResponse(clientData, authenticatorData, signature, ""),
            )
        publish(destination, Json.encodeToString(assertion))
    }
}
