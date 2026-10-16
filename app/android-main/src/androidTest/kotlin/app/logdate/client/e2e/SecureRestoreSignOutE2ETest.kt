package app.logdate.client.e2e

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.storage.AndroidSecureStorage
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.shared.config.DefaultLogDateConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class SecureRestoreSignOutE2ETest {
    @Test
    fun signedOutIntentSurvivesSecureStoreRecreationAndRejectsOlderRecovery() =
        runBlocking {
            val application = ApplicationProvider.getApplicationContext<Context>()
            val preferenceName = "restore-signout-fixture-${Uuid.random()}"
            val context =
                object : ContextWrapper(application) {
                    override fun getApplicationContext(): Context = this

                    override fun getSharedPreferences(
                        name: String,
                        mode: Int,
                    ): SharedPreferences = application.getSharedPreferences(preferenceName, mode)
                }
            val job = SupervisorJob()
            try {
                val scope = CoroutineScope(job + Dispatchers.Default)
                val origin = "https://restore-fixture.example"
                val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
                val background = SecureSessionStorage(AndroidSecureStorage(context), config, scope)
                val foreground = SecureSessionStorage(AndroidSecureStorage(context), config, scope)
                val guard = assertNotNull(background.captureInstallationGuard())
                foreground.clearSession()
                val recovered = UserSession("synthetic-access", "synthetic-refresh", "synthetic-owner")
                assertFalse(background.installSessionIfCurrent(guard, recovered))
                val restarted = SecureSessionStorage(AndroidSecureStorage(context), config, scope)
                assertNull(restarted.captureInstallationGuard())
                restarted.saveSession(recovered)
                assertNotNull(restarted.captureInstallationGuard())
                Unit
            } finally {
                job.cancelAndJoin()
                application.deleteSharedPreferences(preferenceName)
            }
        }
}
