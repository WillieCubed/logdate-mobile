@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.harness

import app.logdate.client.networking.PasskeyApiClient
import app.logdate.integration.e2e.fixtures.createPasskeyOnlyAccount
import app.logdate.server.auth.TokenService
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.BeginAuthenticationRequest
import app.logdate.shared.model.CompleteAuthenticationRequest
import app.logdate.shared.model.PasskeyAssertionResponse
import io.ktor.server.request.path
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.core.context.GlobalContext
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class NativeMediaReauthenticationServerTest {
    @Test
    fun revokedSessionRequiresARealPasskeyBeforeAcceptedRecoveryCanResume() =
        runBlocking {
            val observer = AtomicReference<NativeMediaReauthenticationServer>()
            withServerClientHarness(beforeRequest = { observer.get()?.observeRequest(it.request.path()) }) {
                val account = apiClient.createPasskeyOnlyAccount("reauth_${Uuid.random().toString().take(8)}")
                val owner =
                    account.response.data.account.id
                        .toString()
                val origin = baseUrl.removeSuffix("/api/v1").replace("127.0.0.1", "10.0.2.2")
                val control = Files.createTempDirectory("native-media-reauth-")
                try {
                    fun publish(
                        path: java.nio.file.Path,
                        value: String,
                    ) {
                        check(!Files.exists(path))
                        Files.writeString(path, value)
                        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
                    }
                    val passkeys =
                        PasskeyApiClient(httpClient, DefaultLogDateConfigRepository(initialBackendUrl = baseUrl.removeSuffix("/api/v1")))
                    val tokens = GlobalContext.get().get<TokenService>()
                    val helper = NativeMediaReauthenticationServer(control, account, origin, tokens, passkeys, ::publish)
                    observer.set(helper)
                    val operation = Uuid.random().toString()
                    publish(
                        control.resolve("media-reauth-revoke-request.json"),
                        buildJsonObject {
                            put("owner", owner)
                            put("origin", origin)
                            put("operationId", operation)
                            put("refreshToken", account.response.data.tokens.refreshToken)
                        }.toString(),
                    )
                    helper.processRequests()
                    val expired = Json.parseToJsonElement(Files.readString(control.resolve("media-reauth-expired-session.json"))).jsonObject
                    assertEquals(operation, expired.getValue("operationId").jsonPrimitive.content)
                    assertTrue(tokens.validateAccessToken(expired.getValue("expiredAccessToken").jsonPrimitive.content) == null)
                    assertTrue(passkeys.refreshToken(account.response.data.tokens.refreshToken).isFailure)
                    assertTrue(Files.isRegularFile(control.resolve("media-reauth-client-refresh-observed")))
                    helper.processRequests() // Repeated host observations must not repeat the server ceremony.
                    val begin =
                        passkeys
                            .beginAuthentication(
                                BeginAuthenticationRequest(account.response.data.account.username),
                            ).getOrThrow()
                    publish(
                        control.resolve("media-reauth-assertion-request.json"),
                        buildJsonObject {
                            put("owner", owner)
                            put("origin", origin)
                            put("operationId", operation)
                            put("rpId", begin.rpId)
                            put("challenge", begin.challenge)
                        }.toString(),
                    )
                    helper.processRequests()
                    val assertion =
                        Json.decodeFromString<PasskeyAssertionResponse>(
                            Files.readString(control.resolve("media-reauth-assertion-response.json")),
                        )
                    val signedIn = passkeys.completeAuthentication(CompleteAuthenticationRequest(assertion, begin.challenge)).getOrThrow()
                    assertEquals(owner, signedIn.account.id.toString())
                    assertTrue(tokens.validateAccessToken(signedIn.tokens.accessToken) == owner)
                    publish(control.resolve("media-reauth-complete"), "complete")
                    helper.requireCompleted()
                } finally {
                    control.toFile().deleteRecursively()
                }
            }
        }
}
