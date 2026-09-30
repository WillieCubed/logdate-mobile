package app.logdate.client.data.account

import app.logdate.client.database.entities.HistoryRecordEntity
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.location.HistoryPayload
import app.logdate.shared.model.location.LocationObservation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class HistoryOwnerAdoptionTest {
    @Test
    fun `crash after moving rows resumes only the consented owner and clears journal last`() =
        runTest {
            val storage = HistoryAdoptionTestStorage()
            val owner = AdoptionOwner()
            val moves = mutableListOf<List<String>>()

            fun coordinator() =
                HistoryOwnerAdoption(
                    storage,
                    owner,
                    DefaultLogDateConfigRepository(initialBackendUrl = "https://server.test"),
                    { "device" },
                    { old, new, origin, device -> moves += listOf(old, new, origin, device) },
                )
            owner.failBinding = true
            assertFailsWith<IllegalStateException> { coordinator().adopt("account") }
            assertEquals("offline", owner.id)
            assertTrue(storage.values.isNotEmpty())
            assertFalse(coordinator().adopt("other-account"))
            assertEquals(1, moves.size)
            owner.failBinding = false
            assertTrue(coordinator().adopt("account"))
            assertEquals("account", owner.id)
            assertEquals(2, moves.size)
            assertEquals(listOf("offline", "account", "https://server.test", "device"), moves.last())
            assertEquals(setOf("identity.completed_history_owner_adoption.v1"), storage.values.keys)
            assertTrue(coordinator().adopt("account"))
            assertEquals(2, moves.size)
            assertFalse(coordinator().adopt("other-account"))
        }

    @Test
    fun `guard invokes adoption hook only after consent and resumes it for a bound matching account`() =
        runTest {
            val owner = AdoptionOwner()
            val account = LogDateAccount(username = "person", displayName = "Person")
            var calls = 0
            val guard =
                CanonicalOwnerBindingGuard(owner, { true }, DefaultLogDateConfigRepository(), {
                    calls++
                    owner.adoptRemoteOwnerIfUninitialized(it)
                })
            assertEquals(CanonicalOwnerBindingGuard.OwnerBinding.NEEDS_LOCAL_DATA_CONSENT, guard.ownerBindingFor(account, false))
            assertEquals(0, calls)
            assertEquals("offline", owner.id)
            assertEquals(CanonicalOwnerBindingGuard.OwnerBinding.ALLOWED, guard.ownerBindingFor(account, true))
            assertEquals(1, calls)
            assertEquals(CanonicalOwnerBindingGuard.OwnerBinding.ALLOWED, guard.ownerBindingFor(account, false))
            assertEquals(2, calls)
        }

    @Test
    fun `rewrites local payload owner before encryption and rejects inconsistent ownership`() {
        val payload = HistoryPayload.Observation(LocationObservation("sample", "old", "device", Instant.fromEpochMilliseconds(1), 1.0, 2.0))
        val record =
            HistoryRecordEntity(
                "old",
                "origin",
                "sample",
                "observation",
                Json.encodeToString<HistoryPayload>(payload),
                "device",
                1,
                0,
                false,
                true,
            )
        val result = Json.decodeFromString<HistoryPayload>(record.rewriteHistoryOwner("new").payload!!)
        assertEquals("new", (result as HistoryPayload.Observation).value.ownerId)
        assertFailsWith<IllegalStateException> { record.copy(ownerId = "foreign").rewriteHistoryOwner("new") }
    }

    @Test
    fun `failed row move leaves owner unchanged and journal recoverable`() =
        runTest {
            val storage = HistoryAdoptionTestStorage()
            val owner = AdoptionOwner()
            val coordinator =
                HistoryOwnerAdoption(
                    storage,
                    owner,
                    DefaultLogDateConfigRepository(initialBackendUrl = "https://server.test"),
                    { "device" },
                    { _, _, _, _ -> error("conflict") },
                )
            assertFailsWith<IllegalStateException> { coordinator.adopt("account") }
            assertEquals("offline", owner.id)
            assertTrue(storage.values.isNotEmpty())
            assertFalse(owner.bound)
        }
}

private class AdoptionOwner : CanonicalOwnerProvider {
    var id = "offline"
    var bound = false
    var failBinding = false

    override suspend fun getCanonicalOwnerId() = id

    override suspend fun hasBoundOwner() = bound

    override suspend fun adoptRemoteOwnerIfUninitialized(remoteOwnerId: String): Boolean {
        if (failBinding) error("simulated crash after rows committed")
        if (bound) return id == remoteOwnerId
        id = remoteOwnerId
        bound = true
        return true
    }
}
