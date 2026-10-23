package app.logdate.server.routes

import app.logdate.server.configureAuthV1TestApp
import app.logdate.server.migration.MigrationFixture
import app.logdate.server.migration.done
import app.logdate.server.migration.sealed
import app.logdate.server.migration.switchedAccount
import app.logdate.shared.model.sync.SpaceMigrationSwitchBackRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

/**
 * While a device downloads the personal space to switch the account back, other devices' space writes
 * wait; once the account is back on legacy storage, a device that hasn't noticed is told to read the
 * account again instead of writing where nobody will read.
 */
@OptIn(ExperimentalUuidApi::class)
class SpaceWriteHoldTest {
    @Test
    fun `space writes wait while the account is switched back and are refused after`() =
        testApplication {
            val fixture = MigrationFixture()
            val env =
                configureAuthV1TestApp(
                    accountRepository = fixture.accounts,
                    personalSpaceEnabled = true,
                    spaceStore = fixture.spaces,
                    spaceMigrationHolds = fixture.holds,
                )
            val created = fixture.legacyAccount(did = null)
            env.atprotoIdentityService.ensureIdentity(requireNotNull(fixture.accounts.findById(created.toKotlinUuid())))
            val account = fixture.switchedAccount(created)
            val did = fixture.did(account)
            val token = env.tokenService.generateAccessToken(account.toString())
            val space = "at://$did/space/app.logdate.space.personal/self"
            put(token, space, "before-the-hold").let { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }

            fixture.service.hold(account, "move-1").done()
            val held = put(token, space, "during-the-hold")
            assertEquals(HttpStatusCode.ServiceUnavailable, held.status)
            assertTrue("ServerBusy" in held.bodyAsText())
            assertEquals("60", held.headers[HttpHeaders.RetryAfter])
            assertEquals(HttpStatusCode.OK, client.get("/xrpc/app.logdate.space.listRepoOps?space=$space") { bearer(token) }.status)

            fixture.clock.advance(fixture.config.holdGrace)
            val seq = fixture.spaces.changes(space, did, 0, 100).cursor
            fixture.service.switchBack(account, SpaceMigrationSwitchBackRequest("move-1", seq)).done()

            val stale = put(token, space, "after-switching-back")
            assertEquals(HttpStatusCode.Conflict, stale.status)
            assertTrue("AccountStorageChanged" in stale.bodyAsText())
        }

    private suspend fun ApplicationTestBuilder.put(
        token: String,
        space: String,
        rkey: String,
    ): HttpResponse =
        client.post("/xrpc/app.logdate.space.putRecord") {
            bearer(token)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"space":"$space","rkey":"$rkey","record":${sealed()}}""")
        }

    private fun HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }
}
