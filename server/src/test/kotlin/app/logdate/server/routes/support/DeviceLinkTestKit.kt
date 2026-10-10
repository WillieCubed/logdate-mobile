package app.logdate.server.routes.support

import app.logdate.server.auth.AccessTokenVerifier
import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.TokenService
import app.logdate.server.database.toJavaUUID
import app.logdate.server.devices.DeviceLinkMint
import app.logdate.server.devices.DeviceLinkRedemption
import app.logdate.server.devices.DeviceLinkService
import app.logdate.server.devices.DeviceSessionGuard
import app.logdate.server.devices.InMemoryDeviceSessionRepository
import app.logdate.shared.model.devices.DeviceLinkRedeemRequest
import app.logdate.shared.model.devices.DeviceLinkRedeemResponse
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
