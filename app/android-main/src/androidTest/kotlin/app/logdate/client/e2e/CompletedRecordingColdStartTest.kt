package app.logdate.client.e2e

import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.MainActivity
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.media.audio.CrashSafeRecording
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Orchestrator preserves data and starts a distinct app process for each managed stage. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CompletedRecordingColdStartTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val coldReceipt get() = File(context.noBackupFilesDir, "recording-cold-start-acceptance.json")

    @Test
    fun stage00RetainCompletedAndUnfinishedRecordingBeforeProcessDeath() =
        runBlocking {
            requireColdFixture()
            check(!coldReceipt.exists()) { "Use a fresh managed recording fixture" }
            val security = createAndroidMainDataStore(context).data.first()[LogdatePreferencesDataSource.SECURITY_LEVEL]
            val state =
                JSONObject()
                    .put("phase", 0)
                    .put("sourceProcess", Process.myPid())
                    .put("label", Uuid.random().toString())
                    .put("securityPresent", security != null)
                    .put("security", security.orEmpty())
            persistColdReceipt(state)
            createAndroidMainDataStore(context).edit { it[LogdatePreferencesDataSource.SECURITY_LEVEL] = "NONE" }
            val final = coldRecording(state)
            val staged = CrashSafeRecording.backupExcludedFile(final)
            check(!final.exists() && !staged.exists())
            FileOutputStream(staged).use { output ->
                output.write(completedBytes())
                output.fd.sync()
            }
            assertTrue(CrashSafeRecording.markCompletedForRetry(staged))
            val unfinished = CrashSafeRecording.backupExcludedFile(coldRecording(state, unfinished = true))
            unfinished.writeBytes(byteArrayOf(1, 2, 3))
            assertFalse(final.exists())
            persistColdReceipt(state.put("phase", 1))
        }

    @Test
    fun stage01ColdForegroundPublishesOnlyCompletedAudioWithoutStartingCapture() =
        runBlocking {
            requireColdFixture()
            val state = JSONObject(coldReceipt.readText())
            val final = coldRecording(state)
            val staged = CrashSafeRecording.backupExcludedFile(final)
            val unfinishedFinal = coldRecording(state, unfinished = true)
            val unfinished = CrashSafeRecording.backupExcludedFile(unfinishedFinal)
            try {
                assertTrue(state.getInt("phase") == 1)
                assertNotEquals(state.getInt("sourceProcess"), Process.myPid(), "Orchestrator must create a new process")
                assertFalse(final.exists(), "Completed audio must still be pending before foreground startup")
                assertTrue(staged.exists())
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                    withTimeout(30_000) { while (!final.isFile) delay(50) }
                    ensureLogDateApplicationStarted(context.applicationContext as android.app.Application)
                    assertContentEquals(completedBytes(), final.readBytes())
                    assertFalse(staged.exists())
                    assertFalse(File(staged.parentFile, "${staged.name}.ready").exists())
                    assertFalse(unfinishedFinal.exists(), "Unfinished container bytes cannot become a completed original")
                    assertContentEquals(byteArrayOf(1, 2, 3), unfinished.readBytes())
                    val services =
                        ParcelFileDescriptor
                            .AutoCloseInputStream(
                                instrumentation.uiAutomation.executeShellCommand("dumpsys activity services ${context.packageName}"),
                            ).bufferedReader()
                            .use { output -> output.readText() }
                    assertFalse(services.contains("Permission Denial"))
                    assertFalse(services.contains("AudioRecordingService"), "Recovery must not restart capture")
                    verifyRecoveredAudioPlayback(final)
                }
                persistColdReceipt(state.put("phase", 2).put("destinationProcess", Process.myPid()))
            } finally {
                restoreFixtureState(
                    {
                        val store = createAndroidMainDataStore(context)
                        val expected = state.getString("security").takeIf { state.getBoolean("securityPresent") }
                        store.edit { preferences ->
                            if (expected == null) {
                                preferences.remove(LogdatePreferencesDataSource.SECURITY_LEVEL)
                            } else {
                                preferences[LogdatePreferencesDataSource.SECURITY_LEVEL] = expected
                            }
                        }
                        kotlin.test.assertEquals(expected, store.data.first()[LogdatePreferencesDataSource.SECURITY_LEVEL])
                    },
                    { if (unfinished.exists()) check(unfinished.delete()) },
                )
            }
        }

    private fun requireColdFixture() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recordingColdStartAcceptance") == "true")
        require(Build.HARDWARE in setOf("ranchu", "goldfish"))
        require(context.packageName.endsWith(".debug"))
        require(InstrumentationRegistry.getArguments().getString("clearPackageData") != "true")
    }

    private fun completedBytes(): ByteArray =
        instrumentation.context.assets
            .open("recovery/completed-recording.m4a")
            .use { it.readBytes() }

    private fun coldRecording(
        state: JSONObject,
        unfinished: Boolean = false,
    ): File {
        val label = Uuid.parse(state.getString("label"))
        return File(context.filesDir, "audio_notes/managed-${if (unfinished) "unfinished" else "completed"}-$label.m4a")
    }

    private fun persistColdReceipt(state: JSONObject) {
        val temporary = File(coldReceipt.parentFile, coldReceipt.name + ".part")
        FileOutputStream(temporary).use {
            it.write(state.toString().encodeToByteArray())
            it.fd.sync()
        }
        check(temporary.renameTo(coldReceipt))
    }
}
