package app.logdate.feature.core.main

import app.logdate.feature.core.sync.AccountSyncStatus
import app.logdate.feature.core.sync.SyncAction
import app.logdate.feature.core.sync.SyncPresentation
import app.logdate.feature.core.sync.messageResource
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.sync_account_waiting
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountSyncPresentationTest {
    @Test
    fun `technical failures present waiting without troubleshooting`() {
        for (state in listOf(
            AccountSyncStatus.SERVER_UNAVAILABLE,
            AccountSyncStatus.CONNECTION_UNAVAILABLE,
            AccountSyncStatus.DEVICE_ACCESS_REQUIRED,
            AccountSyncStatus.CONFLICT,
            AccountSyncStatus.LOCAL_DATA_UNAVAILABLE,
            AccountSyncStatus.MEDIA_TOO_LARGE,
            AccountSyncStatus.UNKNOWN,
        )) {
            assertEquals(Res.string.sync_account_waiting, state.messageResource())
        }
    }

    @Test
    fun `fallback cannot expose failures while account status initializes`() {
        for (state in listOf(
            SyncPresentation.StatusUnavailable,
            SyncPresentation.NeedsRecovery,
            SyncPresentation.StorageError(1),
            SyncPresentation.ConflictError(1),
            SyncPresentation.NetworkError(1),
        )) {
            assertEquals(Res.string.sync_account_waiting, state.accountSummaryResource())
            assertEquals(null, state.accountRecoveryAction())
        }
        assertEquals(SyncAction.SignIn, SyncPresentation.AuthError.accountRecoveryAction())
    }
}
