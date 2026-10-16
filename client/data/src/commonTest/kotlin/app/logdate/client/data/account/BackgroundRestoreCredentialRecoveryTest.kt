package app.logdate.client.data.account

import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.CompleteAuthenticationData
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class BackgroundRestoreCredentialRecoveryTest {
    private val account =
        LogDateAccount(
            Uuid.random(),
            "synthetic",
            "Synthetic",
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0),
        )
    private val response = CompleteAuthenticationData(account, AccountTokens("access", "refresh"))
    private val policy = NativeRestoreAccountPolicy(account.id.toString(), "true", true)

    @Test
    fun `acknowledges after durable persistence only`() =
        runTest {
            val events = mutableListOf<String>()
            val recover =
                BackgroundRestoreCredentialRecovery(policy, { Result.success(response) }, { events += "persisted" }, {
                    events +=
                        "acknowledged"
                })
            assertTrue(recover())
            assertEquals(listOf("persisted", "acknowledged"), events)
        }

    @Test
    fun `failed persistence keeps launcher recovery pending`() =
        runTest {
            var acknowledged = false
            val recover =
                BackgroundRestoreCredentialRecovery(policy, { Result.success(response) }, { error("disk full") }, {
                    acknowledged =
                        true
                })
            assertFalse(recover())
            assertFalse(acknowledged)
        }

    @Test
    fun `mismatched returned owner cannot persist or acknowledge`() =
        runTest {
            var persisted = false
            val other = response.copy(account = account.copy(id = Uuid.random()))
            val recover =
                BackgroundRestoreCredentialRecovery(policy, { Result.success(other) }, { persisted = true }, { error("acknowledged") })
            assertFalse(recover())
            assertFalse(persisted)
        }

    @Test
    fun `unbound local content never consumes the credential`() =
        runTest {
            val recover =
                BackgroundRestoreCredentialRecovery(NativeRestoreAccountPolicy(null, null, true), { error("credential consumed") }, {}, {})
            assertFalse(recover())
        }

    @Test
    fun `an interrupted write leaves acknowledgment pending and propagates cancellation`() =
        runTest {
            var acknowledged = false
            val recover =
                BackgroundRestoreCredentialRecovery(policy, {
                    Result.success(
                        response,
                    )
                }, { throw CancellationException("restore timed out") }, {
                    acknowledged =
                        true
                })
            assertFailsWith<CancellationException> { recover() }
            assertFalse(acknowledged)
        }
}
