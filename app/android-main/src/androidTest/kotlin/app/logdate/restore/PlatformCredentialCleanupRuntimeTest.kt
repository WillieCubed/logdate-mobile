package app.logdate.restore

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.device.AndroidAccountManager
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PlatformCredentialCleanupRuntimeTest {
    @Test
    fun delayedWriterRechecksIdentityAtThePlatformBoundary() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val platform = mockk<AccountManager>()
            val manager = AndroidAccountManager(context, platform)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val current = MutableStateFlow(true)
            val writing =
                async(Dispatchers.Default) {
                    manager.updateTokensIfCurrent("synthetic", "https://cleanup.example", "access", "refresh") {
                        entered.complete(Unit)
                        release.await()
                        current.value
                    }
                }
            entered.await()
            current.value = false
            release.complete(Unit)
            assertTrue(writing.await().isFailure)
            confirmVerified(platform)
        }

    @Test
    fun partialAccountIsRemovedOnlyWhileItsSignOutDecisionStillApplies() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val platform = mockk<AccountManager>()
            val account = Account("synthetic", "${context.packageName}.account")
            every { platform.getAccountsByType(account.type) } returns arrayOf(account)
            every { platform.getUserData(account, AndroidAccountManager.KEY_USERNAME) } returns "synthetic"
            every { platform.getUserData(account, AndroidAccountManager.KEY_BACKEND_URL) } returns "https://cleanup.example"
            every { platform.removeAccountExplicitly(account) } returns true
            val manager = AndroidAccountManager(context, platform)
            assertTrue(manager.removeAccountIfSignedOut("synthetic", "https://cleanup.example") { false }.isSuccess)
            verify(exactly = 0) { platform.removeAccountExplicitly(any()) }
            assertTrue(manager.removeAccountIfSignedOut("synthetic", "https://cleanup.example") { true }.isSuccess)
            verify(exactly = 1) { platform.removeAccountExplicitly(account) }
            verify(exactly = 0) { platform.peekAuthToken(any(), any()) }
        }
}
