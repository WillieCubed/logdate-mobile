package app.logdate.server.auth

import app.logdate.server.database.toJavaUUID
import app.logdate.server.devices.DeviceLinkMint
import app.logdate.server.devices.DeviceLinkRedemption
import app.logdate.server.devices.DeviceLinkService
import app.logdate.server.devices.DeviceSessionGuard
import app.logdate.server.devices.InMemoryDeviceSessionRepository
import app.logdate.shared.model.devices.DeviceLinkRedeemRequest
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AccessTokenVerifierTest {
    private val tokens = JwtTokenService("access-token-verifier-secret")
    private val accounts = InMemoryAccountRepository()
    private val repository = InMemoryDeviceSessionRepository()
    private val guard = DeviceSessionGuard(repository, cacheTtl = Duration.ZERO)
    private val verifier = AccessTokenVerifier(tokens, guard)
    private val linkService = DeviceLinkService(repository, tokens, accounts, guard)

    @Test
    fun `full tokens pass and linked device tokens need sync scope and a live session`() =
        runTest {
            val owner = Uuid.random()
            accounts.create(Account(id = owner, username = "verifier_owner", displayName = "Owner", createdAt = Clock.System.now()))
            val code = assertIs<DeviceLinkMint.Minted>(linkService.mint(owner.toJavaUUID())).code.code
            val watch = assertIs<DeviceLinkRedemption.Redeemed>(linkService.redeem(DeviceLinkRedeemRequest(code, "Pixel Watch 3"))).response
            val phoneToken = tokens.generateAccessToken(owner.toString())

            assertEquals(AccessCheck.Missing, verifier.verify(null, allowSyncScope = true))
            assertEquals(AccessCheck.Invalid, verifier.verify("not-a-token", allowSyncScope = true))
            assertEquals(AccessCheck.Invalid, verifier.verify(watch.tokens.refreshToken, allowSyncScope = true))
            assertEquals(
                AccessCheck.Granted(AccessTokenClaims.full(owner.toString())),
                verifier.verify(phoneToken, allowSyncScope = false),
            )
            assertEquals(AccessCheck.ScopeDenied, verifier.verify(watch.tokens.accessToken, allowSyncScope = false))
            val granted = assertIs<AccessCheck.Granted>(verifier.verify(watch.tokens.accessToken, allowSyncScope = true))
            assertEquals(watch.sessionId, granted.claims.sessionId)
            assertEquals(TokenScope.SYNC, granted.claims.scope)
            assertEquals(
                AccessCheck.SessionRevoked,
                AccessTokenVerifier(tokens, DeviceSessionGuard.Disabled).verify(watch.tokens.accessToken, allowSyncScope = true),
            )

            linkService.revoke(owner.toJavaUUID(), UUID.fromString(watch.sessionId))
            assertEquals(AccessCheck.SessionRevoked, verifier.verify(watch.tokens.accessToken, allowSyncScope = true))
        }

    @Test
    fun `bearer tokens are read only from a Bearer header`() {
        assertEquals("abc", bearerToken("Bearer abc"))
        assertEquals("", bearerToken("Bearer   "))
        assertNull(bearerToken("Basic abc"))
        assertNull(bearerToken(null))
    }
}
