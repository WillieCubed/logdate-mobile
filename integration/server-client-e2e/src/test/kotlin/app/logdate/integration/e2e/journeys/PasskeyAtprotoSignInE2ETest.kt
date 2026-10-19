package app.logdate.integration.e2e.journeys

import app.logdate.integration.e2e.fixtures.DpopKey
import app.logdate.integration.e2e.fixtures.createPasskeyOnlyAccount
import app.logdate.integration.e2e.fixtures.pkceChallenge
import app.logdate.integration.e2e.harness.withServerClientHarness
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.parameters
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.ServerSocket
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A LogDate account that only has a passkey signs in to another AT Protocol app with its LogDate
 * identity, the way Bluesky and other OAuth apps sign people in.
 *
 * The other app is a separate server publishing its own client metadata document. It pushes an
 * authorization request, this test plays the person's browser through LogDate's authorization page
 * and passkey ceremony, and the app exchanges the code with PKCE and DPoP and then acts as the
 * account. Nothing here sets or uses a password.
 */
class PasskeyAtprotoSignInE2ETest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a passkey-only account signs in to another AT Protocol app with its LogDate identity`() =
        kotlinx.coroutines.test.runTest {
            withServerClientHarness {
                withThirdPartyApp { app ->
                    val account = apiClient.createPasskeyOnlyAccount("oauth_${Random.nextInt(1000, 9999)}")
                    val did = assertNotNull(account.response.data.account.did)
                    val handle = assertNotNull(account.response.data.account.handle)
                    val logDate = originOf(baseUrl)

                    HttpClient(OkHttp) { followRedirects = false }.use { http ->
                        val passwordAttempt =
                            http.postJson(
                                "$logDate/xrpc/com.atproto.server.createSession",
                                buildJsonObject {
                                    put("identifier", handle)
                                    put("password", "any-password-at-all")
                                },
                            )
                        assertEquals(HttpStatusCode.Unauthorized, passwordAttempt.status, "a passkey-only account has no password")

                        // The app pushes its authorization request, as every AT Protocol OAuth client must.
                        val dpop = DpopKey()
                        val verifier = randomToken()
                        val par =
                            http.submitForm(
                                "$logDate/oauth/par",
                                parameters {
                                    append("client_id", app.clientId)
                                    append("redirect_uri", app.redirectUri)
                                    append("response_type", "code")
                                    append("scope", "atproto")
                                    append("code_challenge", pkceChallenge(verifier))
                                    append("code_challenge_method", "S256")
                                    append("state", "journal-viewer-state")
                                    append("login_hint", handle)
                                },
                            ) { header("DPoP", dpop.proof("POST", "$logDate/oauth/par")) }
                        assertEquals(HttpStatusCode.Created, par.status, par.bodyAsText())
                        val requestUri =
                            par
                                .json()
                                .getValue("request_uri")
                                .jsonPrimitive.content
                        val nonce = assertNotNull(par.headers["DPoP-Nonce"])

                        // The person's browser lands on LogDate's authorization page.
                        val page =
                            http.get(
                                "$logDate/oauth/authorize?client_id=${app.clientId.encodeURLParameter()}" +
                                    "&request_uri=${requestUri.encodeURLParameter()}",
                            ) { header(HttpHeaders.Accept, "text/html") }
                        assertEquals(HttpStatusCode.OK, page.status, page.bodyAsText())
                        val pageHtml = page.bodyAsText()
                        assertTrue("Sign in to Journal Viewer" in pageHtml, pageHtml)
                        assertTrue("@$handle" in pageHtml, pageHtml)

                        // The page runs the passkey ceremony with the account's only credential.
                        val options =
                            http
                                .postJson("$logDate/oauth/authorize/passkey/options", buildJsonObject { put("request_uri", requestUri) })
                                .also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                                .json()
                        assertEquals(
                            listOf(account.authenticator.credentialIdB64),
                            options.getValue("allow_credentials").jsonArray.map { it.jsonPrimitive.content },
                        )
                        val challenge = options.getValue("challenge").jsonPrimitive.content
                        val (clientDataJson, authenticatorData, signature) = account.authenticator.assert(challenge)
                        val approval =
                            http.postJson(
                                "$logDate/oauth/authorize/passkey",
                                buildJsonObject {
                                    put("request_uri", requestUri)
                                    put("challenge", challenge)
                                    putJsonObject("credential") {
                                        put("id", account.authenticator.credentialIdB64)
                                        put("rawId", account.authenticator.credentialIdB64)
                                        put("type", "public-key")
                                        putJsonObject("response") {
                                            put("clientDataJSON", clientDataJson)
                                            put("authenticatorData", authenticatorData)
                                            put("signature", signature)
                                            put("userHandle", "")
                                        }
                                    }
                                },
                            )
                        assertEquals(HttpStatusCode.OK, approval.status, approval.bodyAsText())
                        val redirect =
                            Url(
                                approval
                                    .json()
                                    .getValue("redirect_uri")
                                    .jsonPrimitive.content,
                            )
                        assertEquals(app.redirectUri, redirect.toString().substringBefore('?'))
                        assertEquals("journal-viewer-state", redirect.parameters["state"])
                        assertEquals("https://logdate.app", redirect.parameters["iss"])
                        val code = assertNotNull(redirect.parameters["code"])

                        // The app trades the code for DPoP-bound tokens.
                        val token =
                            http.submitForm(
                                "$logDate/oauth/token",
                                parameters {
                                    append("grant_type", "authorization_code")
                                    append("code", code)
                                    append("redirect_uri", app.redirectUri)
                                    append("client_id", app.clientId)
                                    append("code_verifier", verifier)
                                },
                            ) { header("DPoP", dpop.proof("POST", "$logDate/oauth/token", nonce = nonce)) }
                        assertEquals(HttpStatusCode.OK, token.status, token.bodyAsText())
                        val tokens = token.json()
                        assertEquals(did, tokens["sub"]?.jsonPrimitive?.content)
                        assertEquals("DPoP", tokens["token_type"]?.jsonPrimitive?.content)
                        val accessToken = tokens.getValue("access_token").jsonPrimitive.content
                        val resourceNonce = assertNotNull(token.headers["DPoP-Nonce"])

                        // The app now acts as the account: it uploads a file into the account's repository.
                        val uploadBlobUrl = "$logDate/xrpc/com.atproto.repo.uploadBlob"
                        val anonymousUpload =
                            http.post(uploadBlobUrl) {
                                contentType(ContentType.Text.Plain)
                                setBody("Posted by Journal Viewer")
                            }
                        assertEquals(HttpStatusCode.Unauthorized, anonymousUpload.status)
                        val upload =
                            http.post(uploadBlobUrl) {
                                header(HttpHeaders.Authorization, "DPoP $accessToken")
                                header("DPoP", dpop.proof("POST", uploadBlobUrl, nonce = resourceNonce, accessToken = accessToken))
                                contentType(ContentType.Text.Plain)
                                setBody("Posted by Journal Viewer")
                            }
                        assertEquals(HttpStatusCode.OK, upload.status, upload.bodyAsText())
                        assertNotNull(upload.json()["blob"], upload.bodyAsText())
                    }
                }
            }
        }

    private suspend fun HttpResponse.json(): JsonObject = json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpClient.postJson(
        url: String,
        body: JsonObject,
    ): HttpResponse =
        post(url) {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }

    private class ThirdPartyApp(
        val clientId: String,
        val redirectUri: String,
    )

    /** Runs a stand-in third-party app that serves its OAuth client metadata document over loopback HTTP. */
    private suspend fun <T> withThirdPartyApp(block: suspend (ThirdPartyApp) -> T): T {
        val port = ServerSocket(0).use { it.localPort }
        val app =
            ThirdPartyApp(
                clientId = "http://localhost:$port/client-metadata.json",
                redirectUri = "http://localhost:$port/callback",
            )
        val metadata =
            buildJsonObject {
                put("client_id", app.clientId)
                put("client_name", "Journal Viewer")
                put("redirect_uris", Json.parseToJsonElement("""["${app.redirectUri}"]"""))
                put("grant_types", Json.parseToJsonElement("""["authorization_code","refresh_token"]"""))
                put("response_types", Json.parseToJsonElement("""["code"]"""))
                put("scope", "atproto")
                put("token_endpoint_auth_method", "none")
                put("dpop_bound_access_tokens", true)
            }.toString()
        val server =
            embeddedServer(Netty, host = "127.0.0.1", port = port) {
                routing {
                    get("/client-metadata.json") { call.respondText(metadata, ContentType.Application.Json) }
                }
            }
        server.start(wait = false)
        try {
            return block(app)
        } finally {
            server.stop(gracePeriodMillis = 100, timeoutMillis = 2_000)
        }
    }

    private fun originOf(baseUrl: String): String {
        val uri = URI(baseUrl)
        return "${uri.scheme}://${uri.host}:${uri.port}"
    }

    private fun randomToken(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
}
