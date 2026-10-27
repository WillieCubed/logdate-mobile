package app.logdate.server.atproto.firehose

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class PrivateIdentityRepoDiscoveryTest {
    @Test
    fun `private accounts are never listed read or announced through the public repository`() =
        testApplication {
            val h = FirehoseHarness().also { it.install(this) }
            val account = h.createAccount("private", publicIdentity = false)
            h.put(
                client,
                account,
                PublicRepoPolicy.PROFILE.toString(),
                "self",
                buildJsonObject {
                    put("\$type", PublicRepoPolicy.PROFILE.toString())
                    put("displayName", "Private")
                },
            )
            h.announcer.identityChanged(account)

            assertFalse(h.gate.mayShow(h.did(account)))
            assertNull(h.gate.standing(h.did(account)))
            assertEquals(emptyList(), h.eventStore.read(0, 100))
            assertEquals(emptyList(), h.crawlRequests.toList())
            val repos = client.get("/xrpc/com.atproto.sync.listRepos").bodyAsText()
            assertFalse(repos.contains(requireNotNull(account.did)))
            val status = client.get("/xrpc/com.atproto.sync.getRepoStatus?did=${account.did}")
            assertFalse(status.status == HttpStatusCode.OK)
        }
}
