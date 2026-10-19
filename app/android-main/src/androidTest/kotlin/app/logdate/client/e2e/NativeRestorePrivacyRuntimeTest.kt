package app.logdate.client.e2e

import android.content.Context
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.logdate.client.MainActivity
import app.logdate.client.database.DatabaseStartupMonitor
import app.logdate.client.database.DatabaseStartupState
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.encryption.PassphraseBackupStore
import app.logdate.client.device.restore.NativeRestorePrivacyMetadataRequiredException
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The host supplies an isolated partial native delivery, then its original preferences. */
@RunWith(AndroidJUnit4::class)
class NativeRestorePrivacyRuntimeTest {
    @Test
    fun retainedJournalRequiresItsPrivacyMetadata() =
        runBlocking {
            val mode = InstrumentationRegistry.getArguments().getString("restorePrivacyMode")
            assumeTrue("requires the partial-delivery host sequence", mode == "blocked" || mode == "resolved")
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
            val context = ApplicationProvider.getApplicationContext<Context>()
            require(context.packageName.endsWith(".debug"))
            val databaseFile = context.getDatabasePath("logdate")
            assertTrue(databaseFile.isFile && databaseFile.length() > 0)
            val keyStore = PassphraseBackupStore(context)
            val key = assertNotNull(keyStore.read())
            val koin = GlobalContext.get()
            if (mode == "resolved") {
                val database = koin.get<LogDateDatabase>()
                assertTrue(database.textNoteDao().getAll().isNotEmpty(), "Original entries must remain readable")
                assertIs<DatabaseStartupState.Ready>(koin.get<DatabaseStartupMonitor>().state.value)
            } else {
                val error = runCatching { koin.get<LogDateDatabase>() }.exceptionOrNull()
                assertTrue(
                    generateSequence(error) { it.cause }.take(20).any { it is NativeRestorePrivacyMetadataRequiredException },
                    "Every database consumer must be refused unknown restored privacy",
                )
                assertIs<DatabaseStartupState.RecoveryRequired>(koin.get<DatabaseStartupMonitor>().state.value)
                ActivityScenario.launch(MainActivity::class.java).use { activity ->
                    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    assertTrue(device.wait(Until.hasObject(By.textContains("privacy settings weren’t restored")), 15_000))
                    assertFalse(device.hasObject(By.text("Write your first entry")))
                    device.findObject(By.text("Try again")).click()
                    assertTrue(device.wait(Until.hasObject(By.textContains("privacy settings weren’t restored")), 15_000))
                    activity.recreate()
                    assertTrue(device.wait(Until.hasObject(By.textContains("privacy settings weren’t restored")), 15_000))
                    assertFalse(device.hasObject(By.text("Write your first entry")))
                }
            }
            assertTrue(databaseFile.isFile && databaseFile.length() > 0)
            assertContentEquals(key, keyStore.read(), "Privacy recovery must retain the journal key")
        }
}
