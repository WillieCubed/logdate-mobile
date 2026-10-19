package app.logdate.integration.e2e.journeys

import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.ServerClientE2EHarness
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.lexicon.app.logdate.crypto.SealedPayload
import app.logdate.space.RecordSwap
import app.logdate.space.SealedRecordCodec
import app.logdate.space.SealingKeys
import app.logdate.space.SpaceRef
import app.logdate.space.XrpcDataHome
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.xrpc.BearerToken
import studio.hypertext.atproto.xrpc.KtorXrpcClient
import studio.hypertext.atproto.xrpc.XrpcProtocolException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonalSpaceE2ETest {
    @Test
    fun `a record sealed on one device opens on another, and the server never sees inside`() =
        runTest {
            withServerClientHarness {
                val owner = signUp()
                val space = SpaceRef.personal(owner.did)
                val payload = lakeDayEntry()

                val write = SealedRecordCodec.seal(SealingKeys.personal(IDENTITY_KEY), space, owner.did, payload)
                owner.home.putRecord(space, write.rkey, write.record, swap = RecordSwap.Absent)

                // A second device has only the identity key and the account's access token.
                val secondDevice = home(owner.token)
                val change = secondDevice.listChanges(space, since = 0).changes.single()
                assertFalse(change.deleted)
                val opened =
                    SealedRecordCodec.open(
                        SealingKeys.personal(IDENTITY_KEY.copyOf()),
                        space,
                        owner.did,
                        change.rkey,
                        assertNotNull(change.record),
                    )
                assertEquals(payload, opened)
                assertEquals(write.rkey, change.rkey)

                val stored = rawChanges(owner.token, space)
                assertTrue(stored.contains(write.rkey), "The sealed key should be what the server stores")
                assertFalse(LAKE_DAY in stored, "The server received the entry's text")
                assertFalse(COLLECTION in stored, "The server received the record's collection")
                assertFalse(INNER_RKEY in stored, "The server received the record's own key")
            }
        }

    @Test
    fun `a deletion reaches the second device as a tombstone`() =
        runTest {
            withServerClientHarness {
                val owner = signUp()
                val space = SpaceRef.personal(owner.did)
                val write = SealedRecordCodec.seal(SealingKeys.personal(IDENTITY_KEY), space, owner.did, lakeDayEntry())
                owner.home.putRecord(space, write.rkey, write.record, swap = RecordSwap.Absent)

                val secondDevice = home(owner.token)
                val created = secondDevice.listChanges(space, since = 0)
                assertEquals(listOf(false), created.changes.map { it.deleted })

                owner.home.deleteRecord(space, write.rkey)

                val afterDelete = secondDevice.listChanges(space, since = created.cursor).changes.single()
                assertEquals(write.rkey, afterDelete.rkey)
                assertTrue(afterDelete.deleted)
                assertNull(afterDelete.record)
                assertNull(afterDelete.cid)
                assertNull(secondDevice.getRecord(space, write.rkey))
            }
        }

    @Test
    fun `another account cannot read or list a personal space`() =
        runTest {
            withServerClientHarness {
                val owner = signUp()
                val intruder = signUp()
                val space = SpaceRef.personal(owner.did)
                val write = SealedRecordCodec.seal(SealingKeys.personal(IDENTITY_KEY), space, owner.did, lakeDayEntry())
                owner.home.putRecord(space, write.rkey, write.record, swap = RecordSwap.Absent)

                assertNull(intruder.home.getRecord(space, write.rkey))
                val listing = assertFailsWith<XrpcProtocolException> { intruder.home.listChanges(space, since = 0) }
                assertEquals(400, listing.statusCode)
                assertEquals("RecordNotFound", listing.error)

                val stillThere = owner.home.listChanges(space, since = 0)
                assertEquals(1, stillThere.changes.size)
            }
        }

    private class Account(
        val did: String,
        val token: String,
        val home: XrpcDataHome,
    )

    private suspend fun ServerClientE2EHarness.signUp(): Account {
        val created = apiClient.createAccountWithSyntheticPasskey("space_${Random.nextInt(100000, 999999)}")
        val did = requireNotNull(created.data.account.did) { "The new account has no DID" }
        val token = created.data.tokens.accessToken
        return Account(did, token, home(token))
    }

    private fun ServerClientE2EHarness.home(token: String): XrpcDataHome {
        val origin = baseUrl.removeSuffix("/api/v1")
        return XrpcDataHome(KtorXrpcClient(httpClient, origin), httpClient, origin) { BearerToken(token) }
    }

    private suspend fun ServerClientE2EHarness.rawChanges(
        token: String,
        space: SpaceRef,
    ): String =
        httpClient
            .get("${baseUrl.removeSuffix("/api/v1")}/xrpc/app.logdate.space.listRepoOps") {
                parameter("space", space.uri)
                parameter("since", "0")
                header(HttpHeaders.Authorization, "Bearer $token")
            }.bodyAsText()

    private fun lakeDayEntry() =
        SealedPayload(
            collection = COLLECTION,
            record =
                buildJsonObject {
                    put("\$type", COLLECTION)
                    put("text", LAKE_DAY)
                },
            rkey = INNER_RKEY,
        )

    private companion object {
        const val COLLECTION = "app.logdate.journal.entry"
        const val INNER_RKEY = "3k2aqx7vd4m5n"
        const val LAKE_DAY = "Lake day"
        val IDENTITY_KEY = ByteArray(32) { it.toByte() }
    }
}
