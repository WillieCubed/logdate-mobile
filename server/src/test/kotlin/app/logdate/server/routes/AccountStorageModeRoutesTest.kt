package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountIdentityRepository
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.configureAuthV1TestApp
import app.logdate.server.routes.support.googleAuthBody
import app.logdate.server.routes.support.googleClaims
import app.logdate.server.routes.support.googleClaimsByToken
import app.logdate.server.routes.support.signupPasskeyBeginBody
import app.logdate.server.routes.support.signupPasskeyCompleteBody
import app.logdate.shared.model.AccountStorageMode
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The server records how each account's data is kept and every account endpoint the apps read says
 * so. With personal spaces on, accounts created from then on start in the personal space; accounts
 * that already existed stay in legacy until they are migrated.
 */
@OptIn(ExperimentalUuidApi::class)
class AccountStorageModeRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val newUserClaims = googleClaims(subject = "google-sub-new", email = "new.user@example.com", name = "New User")

    @Test
    fun `with personal spaces on a passkey sign-up starts in the personal space`() =
        testApplication {
            configureAuthV1TestApp(personalSpaceEnabled = true)

            val created = signUpWithPasskey("space_user")

            assertEquals("personalSpaceV1", created.account().storageMode())
            assertEquals("personalSpaceV1", currentAccount(created.accessToken()).account().storageMode())
        }

    @Test
    fun `with personal spaces on a Google sign-up starts in the personal space`() =
        testApplication {
            configureAuthV1TestApp(personalSpaceEnabled = true, googleClaimsByToken = googleClaimsByToken("g-new" to newUserClaims))

            val created = signUpWithGoogle("g-new")

            assertEquals("personalSpaceV1", created.account().storageMode())
        }

    @Test
    fun `with personal spaces off new accounts start in legacy`() =
        testApplication {
            configureAuthV1TestApp(personalSpaceEnabled = false, googleClaimsByToken = googleClaimsByToken("g-new" to newUserClaims))

            assertEquals("legacy", signUpWithPasskey("legacy_user").account().storageMode())
            assertEquals("legacy", signUpWithGoogle("g-new").account().storageMode())
        }

    @Test
    fun `an account that existed before personal spaces were turned on stays in legacy`() =
        testApplication {
            val claims = googleClaims(subject = "google-sub-old", email = "old.user@example.com", name = "Old User")
            val env = configureAuthV1TestApp(personalSpaceEnabled = true, googleClaimsByToken = googleClaimsByToken("g-old" to claims))
            runBlocking {
                env.accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "old_user",
                        displayName = "Old User",
                        email = "old.user@example.com",
                        emailVerified = true,
                        createdAt = Clock.System.now(),
                    ),
                )
            }

            val signedIn =
                parse(
                    client.post("/api/v1/auth/signin/google") {
                        contentType(ContentType.Application.Json)
                        setBody(googleAuthBody("g-old"))
                    },
                    HttpStatusCode.OK,
                )

            assertEquals("legacy", signedIn.account().storageMode())
            val profile =
                parse(
                    client.put("/api/v1/auth/me") {
                        header(HttpHeaders.Authorization, "Bearer ${signedIn.accessToken()}")
                        contentType(ContentType.Application.Json)
                        setBody("""{"displayName":"Still Old"}""")
                    },
                    HttpStatusCode.OK,
                )
            assertEquals("legacy", profile.getValue("data").jsonObject.storageMode())
        }

    @Test
    fun `a personal space account keeps its mode after personal spaces are turned off`() {
        val accounts = InMemoryAccountRepository()
        val identities = InMemoryAccountIdentityRepository()
        val claims =
            googleClaimsByToken(
                "g-space" to googleClaims(subject = "google-sub-space", email = "space@example.com", name = "Space"),
            )
        lateinit var did: String
        testApplication {
            configureAuthV1TestApp(
                personalSpaceEnabled = true,
                accountRepository = accounts,
                identityRepository = identities,
                googleClaimsByToken = claims,
            )
            did =
                signUpWithGoogle("g-space")
                    .account()
                    .getValue("did")
                    .jsonPrimitive.content
        }

        testApplication {
            configureAuthV1TestApp(
                personalSpaceEnabled = false,
                accountRepository = accounts,
                identityRepository = identities,
                googleClaimsByToken = claims,
            )
            val signedIn =
                parse(
                    client.post("/api/v1/auth/signin/google") {
                        contentType(ContentType.Application.Json)
                        setBody(googleAuthBody("g-space"))
                    },
                    HttpStatusCode.OK,
                )
            assertEquals("personalSpaceV1", signedIn.account().storageMode())
            val token = signedIn.accessToken()
            val profile =
                parse(
                    client.put("/api/v1/auth/me") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"displayName":"Still Space"}""")
                    },
                    HttpStatusCode.OK,
                )
            assertEquals("personalSpaceV1", profile.getValue("data").jsonObject.storageMode())

            val space =
                client.get("/xrpc/app.logdate.space.listRepoOps") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    url.parameters.append("space", "at://$did/space/app.logdate.space.personal/self")
                }
            assertEquals(HttpStatusCode.NotImplemented, space.status, "the space answers that it is off, never that the data is missing")
            assertEquals(
                "MethodNotImplemented",
                json
                    .parseToJsonElement(space.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals("personalSpaceV1", currentAccount(token).account().storageMode())
        }
        assertEquals(AccountStorageMode.PERSONAL_SPACE_V1, runBlocking { accounts.findByDid(did) }?.storageMode)
    }

    @Test
    fun `the profile endpoint reports the personal space mode`() =
        testApplication {
            configureAuthV1TestApp(personalSpaceEnabled = true)
            val created = signUpWithPasskey("profile_user")

            val profile =
                parse(
                    client.put("/api/v1/auth/me") {
                        header(HttpHeaders.Authorization, "Bearer ${created.accessToken()}")
                        contentType(ContentType.Application.Json)
                        setBody("""{"displayName":"Profile User"}""")
                    },
                    HttpStatusCode.OK,
                )

            assertEquals(AccountStorageMode.PERSONAL_SPACE_V1.wireValue, profile.getValue("data").jsonObject.storageMode())
        }

    private suspend fun ApplicationTestBuilder.signUpWithPasskey(username: String): JsonObject {
        val begin =
            parse(
                client.post("/api/v1/auth/signup/passkey/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(signupPasskeyBeginBody(username = username, displayName = username))
                },
                HttpStatusCode.OK,
            )
        val sessionToken =
            begin
                .getValue("data")
                .jsonObject
                .getValue("sessionToken")
                .jsonPrimitive.content
        return parse(
            client.post("/api/v1/auth/signup/passkey/complete") {
                contentType(ContentType.Application.Json)
                setBody(signupPasskeyCompleteBody(sessionToken = sessionToken, credentialId = "credential-$username"))
            },
            HttpStatusCode.Created,
        )
    }

    private suspend fun ApplicationTestBuilder.signUpWithGoogle(idToken: String): JsonObject =
        parse(
            client.post("/api/v1/auth/signup/google") {
                contentType(ContentType.Application.Json)
                setBody("""{"idToken":"$idToken","requestedOwnerId":"${Uuid.random()}"}""")
            },
            HttpStatusCode.OK,
        )

    private suspend fun ApplicationTestBuilder.currentAccount(accessToken: String): JsonObject =
        parse(client.get("/api/v1/auth/me") { header(HttpHeaders.Authorization, "Bearer $accessToken") }, HttpStatusCode.OK)

    private suspend fun parse(
        response: HttpResponse,
        expected: HttpStatusCode,
    ): JsonObject {
        val body = response.bodyAsText()
        assertEquals(expected, response.status, body)
        return json.parseToJsonElement(body).jsonObject
    }

    private fun JsonObject.account(): JsonObject = getValue("data").jsonObject.getValue("account").jsonObject

    private fun JsonObject.accessToken(): String =
        getValue("data")
            .jsonObject
            .getValue("tokens")
            .jsonObject
            .getValue("accessToken")
            .jsonPrimitive.content

    private fun JsonObject.storageMode(): String? = get("storageMode")?.jsonPrimitive?.content
}
