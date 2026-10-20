package app.logdate.client.e2e

import android.app.Application
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Reads the retained edit before any content verifier can recreate it. */
@RunWith(AndroidJUnit4::class)
class NativeRetainedOfflineEditProbeTest {
    @Test
    fun offlineEditAlreadyExistsAfterRestart(): Unit =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            assumeTrue(InstrumentationRegistry.getArguments().getString("nativeRetainedEdit") == "true")
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
            val context = instrumentation.targetContext
            require(context.packageName.endsWith(".debug"))
            ensureLogDateApplicationStarted(context.applicationContext as Application)
            val koin = GlobalContext.get()
            val journals = koin.get<JournalRepository>().allJournalsObserved.first()
            assertEquals(2, journals.size, "Only the synthetic transfer fixture is eligible")
            assertTrue(journals.any { it.title == "Native transfer journal" })
            val note =
                assertNotNull(
                    koin.get<JournalNotesRepository>().getNoteById(Uuid.parse("00000000-0000-0000-0000-000000000008")),
                    "The offline edit must already exist after process restart",
                )
            assertEquals("Written offline on replacement phone", assertIs<JournalNote.Text>(note).content)
        }
}
