package app.logdate.feature.core.main

import app.logdate.feature.core.sync.AccountSyncStatus
import app.logdate.feature.core.sync.SyncAction
import app.logdate.feature.core.sync.SyncPresentation
import app.logdate.feature.core.sync.messageResource
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.sync_account_offline
import logdate.client.feature.core.generated.resources.sync_account_unknown
import logdate.client.feature.core.generated.resources.sync_account_wifi
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountSyncPresentationTest {
    @Test
    fun `known pauses explain why syncing cannot proceed`() {
        assertEquals(Res.string.sync_account_offline, AccountSyncStatus.OFFLINE.messageResource())
        assertEquals(Res.string.sync_account_wifi, AccountSyncStatus.WAITING_FOR_WIFI.messageResource())
        assertEquals(Res.string.sync_account_unknown, AccountSyncStatus.SERVER_UNAVAILABLE.messageResource())
    }

    @Test
    fun `fallback uses the same explanation without creating troubleshooting chores`() {
        for ((presentation, status) in listOf(
            SyncPresentation.StatusUnavailable to AccountSyncStatus.LOCAL_DATA_UNAVAILABLE,
            SyncPresentation.NeedsRecovery to AccountSyncStatus.DEVICE_ACCESS_REQUIRED,
            SyncPresentation.StorageError(1) to AccountSyncStatus.STORAGE_FULL,
            SyncPresentation.ConflictError(1) to AccountSyncStatus.CONFLICT,
            SyncPresentation.NetworkError(1) to AccountSyncStatus.CONNECTION_UNAVAILABLE,
        )) {
            assertEquals(status.messageResource(), presentation.accountSummaryResource())
            assertEquals(null, presentation.accountRecoveryAction())
        }
        assertEquals(SyncAction.SignIn, SyncPresentation.AuthError.accountRecoveryAction())
    }
}
