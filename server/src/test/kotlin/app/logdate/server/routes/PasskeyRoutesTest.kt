package app.logdate.server.routes

import app.logdate.server.auth.TokenService
import app.logdate.server.passkeys.PasskeyRepository
import app.logdate.server.passkeys.StoredPasskeyData
import app.logdate.shared.model.PasskeyInfo
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Pins the contract the settings screen reads its passkey list from.
 *
 * The account payload carries credential IDs and nothing else, so without this endpoint a person
 * choosing which passkey to revoke has nothing to tell them apart. What matters here is that the
 * list belongs to the caller and to nobody else.
 */
@OptIn(ExperimentalUuidApi::class)
class PasskeyRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val accountId = Uuid.parse("11111111-1111-4111-8111-111111111111")
    private val otherAccountId = Uuid.parse("22222222-2222-4222-8222-222222222222")

    private fun passkey(
        nickname: String,
        credentialId: String,
    ) = PasskeyInfo(
        id = Uuid.random(),
        credentialId = credentialId,
        nickname = nickname,
        deviceType = "platform",
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        lastUsedAt = Instant.parse("2026-06-01T00:00:00Z"),
    )

    @Test
    fun `returns the callers passkeys`() =
        testApplication {
            val repository =
                FakePasskeyRepository(
                    mapOf(
                        accountId to listOf(passkey("Pixel", "cred-a"), passkey("MacBook", "cred-b")),
                        otherAccountId to listOf(passkey("Somebody else", "cred-c")),
                    ),
                )
            application {
                install(ContentNegotiation) { json() }
                routing { passkeyRoutes(FakeTokenService(accountId.toString()), repository) }
            }

            val response = client.get("/passkeys") { header("Authorization", "Bearer valid") }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.bodyAsText()).jsonArray
            assertEquals(2, body.size)
            assertEquals(
                listOf("Pixel", "MacBook"),
                body.map { it.jsonObject["nickname"]!!.jsonPrimitive.content },
            )
            assertTrue(
                body.none { it.jsonObject["credentialId"]!!.jsonPrimitive.content == "cred-c" },
                "another account's passkey must never appear in this list",
            )
        }

    @Test
    fun `carries the metadata the client cannot otherwise know`() =
        testApplication {
            val repository = FakePasskeyRepository(mapOf(accountId to listOf(passkey("Pixel", "cred-a"))))
            application {
                install(ContentNegotiation) { json() }
                routing { passkeyRoutes(FakeTokenService(accountId.toString()), repository) }
            }

            val entry =
                json
                    .parseToJsonElement(
                        client.get("/passkeys") { header("Authorization", "Bearer valid") }.bodyAsText(),
                    ).jsonArray
                    .single()
                    .jsonObject

            assertEquals("Pixel", entry["nickname"]!!.jsonPrimitive.content)
            assertEquals("platform", entry["deviceType"]!!.jsonPrimitive.content)
            assertTrue(entry.containsKey("createdAt"))
            assertTrue(entry.containsKey("lastUsedAt"))
        }

    @Test
    fun `rejects a request with no bearer token`() =
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { passkeyRoutes(FakeTokenService(accountId.toString()), FakePasskeyRepository(emptyMap())) }
            }

            assertEquals(HttpStatusCode.Unauthorized, client.get("/passkeys").status)
        }

    @Test
    fun `rejects a token the service does not recognise`() =
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { passkeyRoutes(FakeTokenService(null), FakePasskeyRepository(emptyMap())) }
            }

            val response = client.get("/passkeys") { header("Authorization", "Bearer nonsense") }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    /**
     * A token whose subject is not a UUID cannot identify an account, so it must not be treated as
     * one -- answering with an empty list would read as "you have no passkeys".
     */
    @Test
    fun `rejects a token whose subject is not an account`() =
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { passkeyRoutes(FakeTokenService("not-a-uuid"), FakePasskeyRepository(emptyMap())) }
            }

            val response = client.get("/passkeys") { header("Authorization", "Bearer valid") }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}

private class FakeTokenService(
    private val subject: String?,
) : TokenService {
    override fun validateAccessToken(token: String): String? = subject

    override fun generateAccessToken(
        accountId: String,
        did: String?,
    ): String = "access"

    override fun generateRefreshToken(
        accountId: String,
        did: String?,
    ): String = "refresh"

    override fun validateRefreshToken(token: String): String? = subject

    override fun generateSessionToken(sessionId: String): String = "session"

    override fun validateSessionToken(token: String): String? = subject
}

@OptIn(ExperimentalUuidApi::class)
private class FakePasskeyRepository(
    private val byAccount: Map<Uuid, List<PasskeyInfo>>,
) : PasskeyRepository {
    override suspend fun getPasskeysForUser(userId: Uuid): List<PasskeyInfo> = byAccount[userId].orEmpty()

    override suspend fun storePasskey(
        userId: Uuid,
        credentialId: String,
        publicKey: ByteArray,
        signCount: Long,
        info: PasskeyInfo,
    ): Boolean = true

    override suspend fun getPasskeyByCredentialId(credentialId: String): Pair<Uuid, StoredPasskeyData>? = null

    override suspend fun updateSignCount(
        credentialId: String,
        newSignCount: Long,
    ): Boolean = true

    override suspend fun deactivatePasskey(
        credentialId: String,
        userId: Uuid,
    ): Boolean = true

    override suspend fun credentialBelongsToUser(
        credentialId: String,
        userId: Uuid,
    ): Boolean = byAccount[userId].orEmpty().any { it.credentialId == credentialId }

    override suspend fun getCredentialIdsForUser(userId: Uuid): List<String> = byAccount[userId].orEmpty().map { it.credentialId }

    override suspend fun credentialExists(credentialId: String): Boolean =
        byAccount.values.flatten().any { it.credentialId == credentialId }
}
