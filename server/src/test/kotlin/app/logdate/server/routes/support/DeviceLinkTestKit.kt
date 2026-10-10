package app.logdate.server.routes.support

import app.logdate.server.auth.AccessTokenVerifier
import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.auth.TokenService
import app.logdate.server.configureAuthV1TestApp
import app.logdate.server.database.toJavaUUID
import app.logdate.server.devices.DeviceLinkMint
import app.logdate.server.devices.DeviceLinkRedemption
import app.logdate.server.devices.DeviceLinkService
import app.logdate.server.devices.DeviceSessionGuard
import app.logdate.server.devices.InMemoryDeviceSessionRepository
import app.logdate.server.logdate.asLogDateBackupRepository
import app.logdate.server.logdate.asLogDateCollectionsRepository
import app.logdate.server.logdate.asLogDateMediaBlobRepository
import app.logdate.server.logdate.asLogDateMediaRepository
import app.logdate.server.routes.deviceRoutes
import app.logdate.server.routes.syncRoutes
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.devices.DeviceLinkRedeemRequest
import app.logdate.shared.model.devices.DeviceLinkRedeemResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Device sessions for route tests: an uncached guard, so a revocation applies to the next request. */
@OptIn(ExperimentalUuidApi::class)
internal class DeviceLinkTestKit(
    tokenService: TokenService,
    private val accounts: AccountRepository,
    clock: Clock = Clock.System,
) {
    val sessions = InMemoryDeviceSessionRepository()
    val guard = DeviceSessionGuard(sessions, cacheTtl = Duration.ZERO, clock = clock)
    val verifier = AccessTokenVerifier(tokenService, guard)
    val linkService = DeviceLinkService(sessions, tokenService, accounts, guard, clock)

    suspend fun newAccount(username: String): Uuid {
        val id = Uuid.random()
        accounts.create(Account(id = id, username = username, displayName = username, createdAt = Clock.System.now()))
        return id
    }

    suspend fun linkWatch(
        accountId: Uuid,
        deviceName: String = "Pixel Watch 3",
    ): DeviceLinkRedeemResponse {
        val code = assertIs<DeviceLinkMint.Minted>(linkService.mint(accountId.toJavaUUID())).code.code
        return assertIs<DeviceLinkRedemption.Redeemed>(linkService.redeem(DeviceLinkRedeemRequest(code, deviceName))).response
    }
}

/** The auth routes, `/auth/devices` and the sync routes in one test app, sharing tokens and device sessions. */
internal fun ApplicationTestBuilder.configureDeviceLinkTestApp(
    tokenService: JwtTokenService,
    clock: Clock = Clock.System,
): DeviceLinkTestKit {
    val accounts = InMemoryAccountRepository()
    val kit = DeviceLinkTestKit(tokenService, accounts, clock)
    configureAuthV1TestApp(
        tokenService = tokenService,
        accountRepository = accounts,
        deviceLinkService = kit.linkService,
        accessTokenVerifier = kit.verifier,
    )
    val sync = InMemorySyncRepository()
    application {
        routing {
            route("/api/v1") {
                deviceRoutes(kit.linkService, kit.verifier)
                syncRoutes(
                    tokenService = tokenService,
                    metrics = SyncMetricsRegistry(),
                    collectionsRepository = sync.asLogDateCollectionsRepository(),
                    mediaBlobRepository = sync.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                    backupRepository = sync.asLogDateBackupRepository(),
                    accessTokenVerifier = kit.verifier,
                )
            }
        }
    }
    return kit
}

internal suspend fun HttpClient.redeemLinkCode(
    code: String,
    deviceName: String = "Pixel Watch 3",
): HttpResponse =
    post("/api/v1/auth/devices/link/redeem") {
        contentType(ContentType.Application.Json)
        setBody("""{"code":"$code","deviceName":"$deviceName"}""")
    }

internal suspend fun HttpClient.refreshTokens(refreshToken: String): HttpResponse =
    post("/api/v1/auth/token/refresh") {
        contentType(ContentType.Application.Json)
        setBody("""{"refreshToken":"$refreshToken"}""")
    }

internal suspend fun HttpResponse.jsonBody(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

/** An Authentication-family error: `{"error":{"code":…}}`. */
internal suspend fun assertApiErrorCode(
    response: HttpResponse,
    status: HttpStatusCode,
    code: String,
) {
    assertEquals(status, response.status)
    assertEquals(
        code,
        response
            .jsonBody()
            .getValue("error")
            .jsonObject
            .getValue("code")
            .jsonPrimitive.content,
    )
}

/** A sync-family error: `{"code":…}`. */
internal suspend fun assertSyncErrorCode(
    response: HttpResponse,
    status: HttpStatusCode,
    code: String,
) {
    assertEquals(status, response.status)
    assertEquals(
        code,
        response
            .jsonBody()
            .getValue("code")
            .jsonPrimitive.content,
    )
}
