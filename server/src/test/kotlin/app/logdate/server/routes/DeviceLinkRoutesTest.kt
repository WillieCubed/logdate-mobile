package app.logdate.server.routes

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.auth.TokenScope
import app.logdate.server.routes.support.assertApiErrorCode
import app.logdate.server.routes.support.configureDeviceLinkTestApp
import app.logdate.server.routes.support.jsonBody
import app.logdate.server.routes.support.redeemLinkCode
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class DeviceLinkRoutesTest {
    private val tokenService = JwtTokenService("device-link-routes-secret")

    @Test
    fun `a phone mints a one-time code and a watch redeems it for a sync session`() =
        testApplication {
            val kit = configureDeviceLinkTestApp(tokenService)
            val owner = kit.newAccount("link_owner")

            val minted = client.post("/api/v1/auth/devices/link") { header(HttpHeaders.Authorization, phone(owner)) }
            assertEquals(HttpStatusCode.OK, minted.status)
            assertEquals("no-store", minted.headers[HttpHeaders.CacheControl])
            val code =
                minted
                    .jsonBody()
                    .getValue("code")
                    .jsonPrimitive.content
            assertEquals(43, code.length)

            val redeemed = client.redeemLinkCode(code)
            assertEquals(HttpStatusCode.OK, redeemed.status)
            assertEquals("no-store", redeemed.headers[HttpHeaders.CacheControl])
            val body = redeemed.jsonBody()
            assertEquals(owner.toString(), body.getValue("accountId").jsonPrimitive.content)
            val access =
                body
                    .getValue("tokens")
                    .jsonObject
                    .getValue("accessToken")
                    .jsonPrimitive.content
            val claims = tokenService.decodeAccessToken(access)
            assertEquals(TokenScope.SYNC, claims?.scope)
            assertEquals(body.getValue("sessionId").jsonPrimitive.content, claims?.sessionId)

            assertApiErrorCode(client.redeemLinkCode(code), HttpStatusCode.BadRequest, DeviceLinkErrorCodes.LINK_CODE_INVALID)
        }

    @Test
    fun `minting needs a signed-in account and redeeming needs a well formed request`() =
        testApplication {
            val kit = configureDeviceLinkTestApp(tokenService)
            val owner = kit.newAccount("refusals_owner")
            val watch = kit.linkWatch(owner)

            assertApiErrorCode(client.post("/api/v1/auth/devices/link"), HttpStatusCode.Unauthorized, "INVALID_TOKEN")
            assertApiErrorCode(
                client.post("/api/v1/auth/devices/link") { header(HttpHeaders.Authorization, "Bearer ${watch.tokens.accessToken}") },
                HttpStatusCode.Forbidden,
                DeviceLinkErrorCodes.DEVICE_SCOPE_DENIED,
            )
            val malformed =
                client.post("/api/v1/auth/devices/link/redeem") {
                    contentType(ContentType.Application.Json)
                    setBody("{not json")
                }
            assertApiErrorCode(malformed, HttpStatusCode.BadRequest, "INVALID_REQUEST")

            val code = client.mint(phone(owner))
            assertApiErrorCode(client.redeemLinkCode(code, deviceName = ""), HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            assertEquals(HttpStatusCode.OK, client.redeemLinkCode(code).status)
        }

    @Test
    fun `the device list marks the caller and removing another device needs full access`() =
        testApplication {
            val kit = configureDeviceLinkTestApp(tokenService)
            val owner = kit.newAccount("list_owner")
            val watch = kit.linkWatch(owner, "Pixel Watch 3")
            val watchBearer = "Bearer ${watch.tokens.accessToken}"

            val fromPhone = client.devices(phone(owner))
            assertEquals(
                "Pixel Watch 3",
                fromPhone
                    .single()
                    .jsonObject
                    .getValue("deviceName")
                    .jsonPrimitive.content,
            )
            assertNull(fromPhone.single().jsonObject["current"])
            assertTrue(
                client
                    .devices(watchBearer)
                    .single()
                    .jsonObject
                    .getValue("current")
                    .jsonPrimitive.boolean,
            )

            val path = "/api/v1/auth/devices/${watch.sessionId}"
            assertApiErrorCode(client.deleteAs(watchBearer, path), HttpStatusCode.Forbidden, DeviceLinkErrorCodes.DEVICE_SCOPE_DENIED)
            val unknown = client.deleteAs(phone(owner), "/api/v1/auth/devices/${Uuid.random()}")
            assertApiErrorCode(unknown, HttpStatusCode.NotFound, DeviceLinkErrorCodes.DEVICE_NOT_FOUND)
            val malformedId = client.deleteAs(phone(owner), "/api/v1/auth/devices/not-a-uuid")
            assertApiErrorCode(malformedId, HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            val stranger = kit.newAccount("list_stranger")
            assertApiErrorCode(client.deleteAs(phone(stranger), path), HttpStatusCode.NotFound, DeviceLinkErrorCodes.DEVICE_NOT_FOUND)

            assertEquals(HttpStatusCode.NoContent, client.deleteAs(phone(owner), path).status)
            assertTrue(client.devices(phone(owner)).isEmpty())
        }

    @Test
    fun `a linked device can unlink itself but a signed-in phone cannot use that route`() =
        testApplication {
            val kit = configureDeviceLinkTestApp(tokenService)
            val owner = kit.newAccount("self_owner")
            val watchBearer = "Bearer ${kit.linkWatch(owner).tokens.accessToken}"

            assertApiErrorCode(
                client.deleteAs(phone(owner), "/api/v1/auth/devices/self"),
                HttpStatusCode.BadRequest,
                DeviceLinkErrorCodes.NOT_A_LINKED_DEVICE,
            )
            assertEquals(HttpStatusCode.NoContent, client.deleteAs(watchBearer, "/api/v1/auth/devices/self").status)
            assertApiErrorCode(
                client.get("/api/v1/auth/devices") { header(HttpHeaders.Authorization, watchBearer) },
                HttpStatusCode.Unauthorized,
                DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED,
            )
        }

    @Test
    fun `account passkey email identity and plan routes refuse linked devices`() =
        testApplication {
            val kit = configureDeviceLinkTestApp(tokenService)
            val owner = kit.newAccount("scope_owner")
            val watch = kit.linkWatch(owner)
            val bearer = "Bearer ${watch.tokens.accessToken}"

            listOf(
                client.put("/api/v1/auth/me") {
                    header(HttpHeaders.Authorization, bearer)
                    contentType(ContentType.Application.Json)
                    setBody("""{"displayName":"Watch"}""")
                },
                client.deleteAs(bearer, "/api/v1/auth/me"),
                client.get("/api/v1/auth/me/passkeys") { header(HttpHeaders.Authorization, bearer) },
                client.post("/api/v1/auth/me/passkeys/begin") { header(HttpHeaders.Authorization, bearer) },
                client.post("/api/v1/auth/me/email/verify/begin") { header(HttpHeaders.Authorization, bearer) },
                client.get("/api/v1/auth/me/entitlement") { header(HttpHeaders.Authorization, bearer) },
                client.get("/api/v1/auth/me/identities") { header(HttpHeaders.Authorization, bearer) },
            ).forEach { assertApiErrorCode(it, HttpStatusCode.Forbidden, DeviceLinkErrorCodes.DEVICE_SCOPE_DENIED) }
            assertApiErrorCode(
                client.get("/api/v1/identity/plc/operations") { header(HttpHeaders.Authorization, bearer) },
                HttpStatusCode.Unauthorized,
                "INVALID_TOKEN",
            )

            val me = client.get("/api/v1/auth/me") { header(HttpHeaders.Authorization, bearer) }
            assertEquals(HttpStatusCode.OK, me.status)
            val tokens =
                me
                    .jsonBody()
                    .getValue("data")
                    .jsonObject
                    .getValue("tokens")
                    .jsonObject
            val access = tokenService.decodeAccessToken(tokens.getValue("accessToken").jsonPrimitive.content)
            assertEquals(TokenScope.SYNC, access?.scope)
            assertEquals(watch.sessionId, access?.sessionId)
            assertEquals(watch.sessionId, tokenService.decodeRefreshToken(tokens.getValue("refreshToken").jsonPrimitive.content)?.sessionId)
        }

    @Test
    fun `redeeming is limited per address`() =
        testApplication {
            configureDeviceLinkTestApp(tokenService)

            repeat(10) {
                val guess = client.redeemLinkCode("not-a-real-code")
                assertApiErrorCode(guess, HttpStatusCode.BadRequest, DeviceLinkErrorCodes.LINK_CODE_INVALID)
            }
            assertApiErrorCode(client.redeemLinkCode("not-a-real-code"), HttpStatusCode.TooManyRequests, "RATE_LIMIT_EXCEEDED")
        }

    @Test
    fun `an account links at most five devices and mints at most ten codes an hour`() =
        testApplication {
            val kit = configureDeviceLinkTestApp(tokenService)
            val crowded = kit.newAccount("crowded_owner")
            repeat(5) { kit.linkWatch(crowded, "Watch $it") }
            assertApiErrorCode(client.mintResponse(phone(crowded)), HttpStatusCode.Conflict, DeviceLinkErrorCodes.DEVICE_LIMIT_REACHED)

            val busy = kit.newAccount("busy_owner")
            repeat(10) { assertEquals(HttpStatusCode.OK, client.mintResponse(phone(busy)).status) }
            val eleventh = client.mintResponse(phone(busy))
            assertApiErrorCode(eleventh, HttpStatusCode.TooManyRequests, DeviceLinkErrorCodes.DEVICE_LINK_RATE_LIMITED)
        }

    private fun phone(accountId: Uuid): String = "Bearer ${tokenService.generateAccessToken(accountId.toString())}"

    private suspend fun HttpClient.mintResponse(bearer: String): HttpResponse =
        post("/api/v1/auth/devices/link") { header(HttpHeaders.Authorization, bearer) }

    private suspend fun HttpClient.mint(bearer: String): String =
        mintResponse(bearer)
            .jsonBody()
            .getValue("code")
            .jsonPrimitive.content

    private suspend fun HttpClient.devices(bearer: String): JsonArray =
        get("/api/v1/auth/devices") { header(HttpHeaders.Authorization, bearer) }.jsonBody().getValue("devices").jsonArray

    private suspend fun HttpClient.deleteAs(
        bearer: String,
        path: String,
    ): HttpResponse = delete(path) { header(HttpHeaders.Authorization, bearer) }
}
