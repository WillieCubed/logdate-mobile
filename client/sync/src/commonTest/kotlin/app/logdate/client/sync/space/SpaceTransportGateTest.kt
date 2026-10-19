package app.logdate.client.sync.space

import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncIssue
import app.logdate.client.sync.issue
import app.logdate.shared.model.AccountStorageMode
import app.logdate.shared.model.Journal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Every device of an account syncs the way the server stores it, or not at all. */
class SpaceTransportGateTest {
    private fun gate(
        mode: AccountStorageMode?,
        serverOffersSpaces: Boolean = true,
        enabled: Boolean = true,
    ) = SpaceTransportGate({ enabled }, { serverOffersSpaces }, { mode })

    @Test
    fun `a personal-space account syncs through its space where the server and this build support it`() =
        runTest {
            assertEquals(SpaceRoute.Space, gate(AccountStorageMode.PERSONAL_SPACE_V1).route())
        }

    @Test
    fun `a legacy account syncs through the per-kind endpoints whatever the server offers`() =
        runTest {
            assertEquals(SpaceRoute.Legacy, gate(AccountStorageMode.LEGACY).route())
            assertEquals(SpaceRoute.Legacy, gate(AccountStorageMode.LEGACY, serverOffersSpaces = false, enabled = false).route())
        }

    @Test
    fun `anything else blocks sync with its reason`() =
        runTest {
            val blocked =
                listOf(
                    gate(AccountStorageMode.UNRECOGNIZED),
                    gate(null),
                    gate(AccountStorageMode.PERSONAL_SPACE_V1, serverOffersSpaces = false),
                    gate(AccountStorageMode.PERSONAL_SPACE_V1, enabled = false),
                ).map { it.route() }

            blocked.forEach { assertIs<SpaceRoute.Blocked>(it) }
            assertEquals(4, blocked.map { (it as SpaceRoute.Blocked).reason }.distinct().size, "Each reason is specific")
        }

    @Test
    fun `a blocked account syncs nothing anywhere and shows why`() =
        runTest {
            val device = SpaceDevice(FakeDataHome()).apply { mode = AccountStorageMode.UNRECOGNIZED }
            device.create(Journal(title = "Weekend walks", created = Instant.parse("2026-10-01T09:00:00Z")))

            val uploaded = device.manager.uploadPendingChanges()
            val downloaded = device.manager.downloadRemoteChanges()
            val journals = device.manager.syncJournals()

            for (result in listOf(uploaded, downloaded, journals)) {
                assertFalse(result.success)
                assertEquals(SyncErrorType.ACCOUNT_STORAGE_ERROR, result.errors.single().type)
                assertFalse(result.errors.single().retryable)
            }
            assertTrue(device.home.tokensSeen.isEmpty(), "Nothing reached the personal space")
            assertEquals(1, device.pendingJournals().size, "Nothing went to the per-kind endpoints either")
            assertEquals(SyncIssue.Failure.AccountStorage, device.manager.getSyncStatus().issue())
        }

    @Test
    fun `a personal-space account on a server without spaces is blocked rather than split`() =
        runTest {
            val device = SpaceDevice(FakeDataHome()).apply { serverOffersSpaces = false }
            device.create(Journal(title = "Weekend walks", created = Instant.parse("2026-10-01T09:00:00Z")))

            val result = device.manager.uploadPendingChanges()

            assertEquals(SyncErrorType.ACCOUNT_STORAGE_ERROR, result.errors.single().type)
            assertEquals(1, device.pendingJournals().size)
        }
}
