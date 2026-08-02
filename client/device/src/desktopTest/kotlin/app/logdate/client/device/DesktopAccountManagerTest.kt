package app.logdate.client.device

import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopAccountManagerTest {
    @Test
    fun pendingAuthorizationRejectsOriginScopedAccountCreation() =
        runTest {
            val manager = DesktopAccountManager()

            val result =
                manager.addAccount(
                    account = LogDateAccount(username = "new-user", displayName = "New User"),
                    accessToken = "new-access-token",
                    refreshToken = "new-refresh-token",
                    backendUrl = "https://cloud.logdate.app",
                )

            assertTrue(result.isFailure, "IdentityFoundationPending must reject platform account writes")
        }

    @Test
    fun pendingAuthorizationDoesNotReportAnAuthoritativelyEmptyAccountList() =
        runTest {
            val manager = DesktopAccountManager()

            val result = manager.getStoredAccounts()

            assertTrue(result.isFailure, "Pending authorization must remain distinct from an empty account list")
        }
}
