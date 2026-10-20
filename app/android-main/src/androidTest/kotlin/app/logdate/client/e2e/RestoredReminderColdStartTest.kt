package app.logdate.client.e2e

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import app.logdate.client.MainActivity
import app.logdate.client.ambient.AmbientPromptAlarmReceiver
import app.logdate.client.ambient.AmbientPromptScheduler
import app.logdate.client.ambient.AmbientPromptWorker
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.PostRestoreType
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import app.logdate.client.reminders.MomentReminderWorker
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Six ordered integration stages; Orchestrator must preserve data and restart the process between them. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RestoredReminderColdStartTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val koin get() = GlobalContext.get()
    private val work get() = WorkManager.getInstance(context)
    private val settings get() = koin.get<MemoriesSettingsRepository>()
    private val flags get() = koin.get<FeatureFlagStore>()
    private val shared get() = koin.get<WatchPreferencesRepository>()
    private val receipt get() = File(context.noBackupFilesDir, "native-reminder-cold-start.json")
    private val periodic = "logdate:ambient:periodic"
    private val signal = "logdate:moment-reminder:signal"
    private val immediate = listOf("periodic", "morning_schedule", "evening_schedule").map { "logdate:ambient:immediate:$it" }
    private val actions = listOf(AmbientPromptScheduler.ACTION_MORNING_PROMPT, AmbientPromptScheduler.ACTION_EVENING_PROMPT)

    @Test fun stage00PreserveOriginalPreferencesAndJournal() =
        runBlocking {
            requireManagedFixture()
            check(!receipt.exists()) { "A previous fixture must be restored before starting another run" }
            assertTrue(
                koin
                    .get<JournalNotesRepository>()
                    .allNotesObserved
                    .first()
                    .isEmpty(),
                "Use a fresh managed device",
            )
            val original = settings.getSettings()
            val originalSecurity = createAndroidMainDataStore(context).data.first()[LogdatePreferencesDataSource.SECURITY_LEVEL]
            val state =
                JSONObject()
                    .put("phase", 0)
                    .put("processIds", JSONArray().put(Process.myPid()))
                    .put("contextual", original.contextualRecommendationsEnabled)
                    .put("ambient", original.ambientPromptsEnabled)
                    .put("capture", original.captureNudgesEnabled)
                    .put("morning", original.morningPromptEnabled)
                    .put("evening", original.eveningPromptEnabled)
                    .put("rollout", flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2))
                    .put("consent", shared.get(SharedPreferenceKey.RemindersEnabled))
                    .put("securityPresent", originalSecurity != null)
                    .put("security", originalSecurity.orEmpty())
            persist(state)
            // The seeded journal includes its explicit unlocked privacy policy, as a real backup does.
            koin.get<LogdatePreferencesDataSource>().setShouldShowBiometric(false)
            val journal = Uuid.random()
            val note = Uuid.random()
            val now = Clock.System.now()
            koin.get<JournalRepository>().create(Journal(id = journal, title = "Retained reminder journal"))
            koin.get<JournalNotesRepository>().create(JournalNote.Text(note, now, now, RETAINED_TEXT), journal)
            state.put("noteId", note.toString()).put("keyHash", keyHash())
            configure(moment = false, enabled = true)
            persist(state.put("phase", 1))
        }

    @Test fun stage01LegacyAlarmsReturnThroughColdForegroundStartup() =
        runBlocking {
            val state = beginColdStage(1)
            clearDeviceSchedules()
            assertEquals(listOf(0, 0), alarmCounts())
            markNativeDelivery(state)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                awaitState { alarmCounts() == listOf(1, 1) && active(periodic).size == 1 }
                assertEquals(TimeUnit.HOURS.toMillis(6), active(periodic).single().periodicityInfo?.repeatIntervalMillis)
                verifyRetainedJournal(state)
            }
            configure(moment = true, enabled = true)
            finishStage(state, 2)
        }

    @Test fun stage02MomentEvaluationReturnsThroughColdForegroundStartup() =
        runBlocking {
            val state = beginColdStage(2)
            clearDeviceSchedules()
            assertTrue(active(periodic).isEmpty())
            markNativeDelivery(state)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                awaitState {
                    alarmCounts() == listOf(0, 0) &&
                        active(periodic).singleOrNull()?.periodicityInfo?.repeatIntervalMillis == TimeUnit.MINUTES.toMillis(15)
                }
                verifyRetainedJournal(state)
            }
            configure(moment = false, enabled = false)
            finishStage(state, 3)
        }

    @Test fun stage03DisabledLegacyPreferencesCancelStaleDeviceWork() =
        runBlocking {
            val state = beginColdStage(3)
            verifyDisabledColdStartup(state)
            configure(moment = true, enabled = false)
            finishStage(state, 4)
        }

    @Test fun stage04DisabledMomentConsentCancelsStaleDeviceWork() =
        runBlocking {
            val state = beginColdStage(4)
            verifyDisabledColdStartup(state)
            finishStage(state, 5)
        }

    @Test fun stage05RestoreOriginalPreferencesAndVerifyEveryProcess() =
        runBlocking {
            requireManagedFixture()
            val state = readReceipt()
            val reachedFinalStage = state.getInt("phase") == 5
            try {
                assertTrue(reachedFinalStage, "All cold-start stages must finish before acceptance")
                assertNewProcess(state)
                verifyRetainedJournal(state)
            } finally {
                restoreFixtureState(
                    {
                        settings.updateSettings(
                            settings.getSettings().copy(
                                contextualRecommendationsEnabled = state.getBoolean("contextual"),
                                ambientPromptsEnabled = state.getBoolean("ambient"),
                                captureNudgesEnabled = state.getBoolean("capture"),
                                morningPromptEnabled = state.getBoolean("morning"),
                                eveningPromptEnabled = state.getBoolean("evening"),
                            ),
                        )
                        val original = settings.getSettings()
                        assertEquals(state.getBoolean("contextual"), original.contextualRecommendationsEnabled)
                        assertEquals(state.getBoolean("ambient"), original.ambientPromptsEnabled)
                        assertEquals(state.getBoolean("capture"), original.captureNudgesEnabled)
                        assertEquals(state.getBoolean("morning"), original.morningPromptEnabled)
                        assertEquals(state.getBoolean("evening"), original.eveningPromptEnabled)
                    },
                    {
                        shared.set(SharedPreferenceKey.RemindersEnabled, state.getBoolean("consent"))
                        assertEquals(state.getBoolean("consent"), shared.get(SharedPreferenceKey.RemindersEnabled))
                    },
                    {
                        flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, state.getBoolean("rollout"))
                        assertEquals(state.getBoolean("rollout"), flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2))
                    },
                    { clearDeviceSchedules() },
                    {
                        val store = createAndroidMainDataStore(context)
                        store.edit { preferences ->
                            val key = LogdatePreferencesDataSource.SECURITY_LEVEL
                            if (state.getBoolean(
                                    "securityPresent",
                                )
                            ) {
                                preferences[key] = state.getString("security")
                            } else {
                                preferences.remove(key)
                            }
                        }
                        assertEquals(
                            state.getString("security").takeIf { state.getBoolean("securityPresent") },
                            store.data.first()[LogdatePreferencesDataSource.SECURITY_LEVEL],
                        )
                    },
                )
            }
            finishStage(state, 6)
            val processes = state.getJSONArray("processIds")
            assertEquals(6, (0 until processes.length()).map { processes.getInt(it) }.toSet().size)
        }

    private fun requireManagedFixture() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("restoreAlarmColdStartAcceptance") == "true")
        require(Build.HARDWARE in setOf("ranchu", "goldfish"))
        require(context.packageName.endsWith(".debug"))
        require(InstrumentationRegistry.getArguments().getString("clearPackageData") != "true")
    }

    private fun readReceipt(): JSONObject = JSONObject(receipt.readText())

    private fun beginColdStage(phase: Int): JSONObject {
        requireManagedFixture()
        return readReceipt().also {
            assertEquals(phase, it.getInt("phase"), "Orchestrator must preserve stage ordering and application data")
            assertNewProcess(it)
        }
    }

    private fun assertNewProcess(state: JSONObject) {
        val processes = state.getJSONArray("processIds")
        assertTrue((0 until processes.length()).none { processes.getInt(it) == Process.myPid() }, "Each stage requires a fresh process")
    }

    private fun finishStage(
        state: JSONObject,
        next: Int,
    ) {
        state.getJSONArray("processIds").put(Process.myPid())
        persist(state.put("phase", next))
    }

    private fun persist(state: JSONObject) {
        val pending = File(receipt.parentFile, receipt.name + ".tmp")
        FileOutputStream(pending).use {
            it.write(state.toString().encodeToByteArray())
            it.fd.sync()
        }
        check(pending.renameTo(receipt)) { "Could not persist the native stage receipt" }
    }

    private suspend fun configure(
        moment: Boolean,
        enabled: Boolean,
    ) {
        settings.updateSettings(
            settings.getSettings().copy(
                contextualRecommendationsEnabled = true,
                ambientPromptsEnabled = !moment && enabled,
                captureNudgesEnabled = true,
                morningPromptEnabled = true,
                eveningPromptEnabled = true,
            ),
        )
        shared.set(SharedPreferenceKey.RemindersEnabled, moment && enabled)
        flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, moment)
    }

    private suspend fun clearDeviceSchedules() {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val cleanup =
            (listOf(periodic, signal) + immediate).map { name ->
                suspend {
                    work.cancelUniqueWork(name).await()
                    Unit
                }
            } +
                actions.mapIndexed { index, action ->
                    suspend {
                        PendingIntent
                            .getBroadcast(
                                context,
                                4101 + index,
                                Intent(context, AmbientPromptAlarmReceiver::class.java).setAction(action),
                                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                            )?.let(alarms::cancel)
                        Unit
                    }
                }
        restoreFixtureState(*cleanup.toTypedArray())
    }

    private fun active(name: String): List<WorkInfo> =
        work.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS).filterNot {
            it.state.isFinished
        }

    private fun markNativeDelivery(state: JSONObject) {
        PostRestoreDetector(context).apply {
            beginNativeRestoreSession()
            markNativeRestorePayloadReceived(databaseDelivered = true)
            markNativePrivacyPayloadReceived()
            recordRestoreCompleted()
            assertEquals(PostRestoreType.CONTENT_RESTORED, detect(isOnboarded = true))
            state.put("deliveryId", assertNotNull(pendingMediaQuarantineId()))
            persist(state)
        }
    }

    private fun keyHash(): String {
        val key = File(context.filesDir, "db_recovery/passphrase_backup")
        assertTrue(key.isFile, "The existing journal must retain its portable key")
        return MessageDigest.getInstance("SHA-256").digest(key.readBytes()).joinToString("") { "%02x".format(it) }
    }

    private suspend fun verifyRetainedJournal(state: JSONObject) {
        val note = koin.get<JournalNotesRepository>().getNoteById(Uuid.parse(state.getString("noteId"))) as JournalNote.Text
        assertEquals(RETAINED_TEXT, note.content)
        assertEquals(state.getString("keyHash"), keyHash())
    }

    private fun alarmCounts(): List<Int> {
        val dump =
            ParcelFileDescriptor
                .AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("dumpsys alarm"))
                .bufferedReader()
                .use { it.readText() }
        assertTrue(!dump.contains("Permission Denial"))
        return actions.map { action -> dump.lineSequence().count { it.trim() == "tag=*walarm*:$action" } }
    }

    private suspend fun verifyDisabledColdStartup(state: JSONObject) {
        clearDeviceSchedules()
        val stale = mutableListOf<java.util.UUID>()
        val recurring = PeriodicWorkRequestBuilder<AmbientPromptWorker>(15, TimeUnit.MINUTES).setInitialDelay(1, TimeUnit.DAYS).build()
        work.enqueueUniquePeriodicWork(periodic, ExistingPeriodicWorkPolicy.UPDATE, recurring).await()
        stale += recurring.id
        immediate.forEach { name ->
            val request = OneTimeWorkRequestBuilder<AmbientPromptWorker>().setInitialDelay(1, TimeUnit.DAYS).build()
            work.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request).await()
            stale += request.id
        }
        val request = OneTimeWorkRequestBuilder<MomentReminderWorker>().setInitialDelay(1, TimeUnit.DAYS).build()
        work.enqueueUniqueWork(signal, ExistingWorkPolicy.KEEP, request).await()
        stale += request.id
        assertTrue(stale.all { work.getWorkInfoById(it).get()?.state == WorkInfo.State.ENQUEUED })
        val alarms = context.getSystemService(AlarmManager::class.java)
        actions.forEachIndexed { index, action ->
            val pending =
                PendingIntent.getBroadcast(
                    context,
                    4101 + index,
                    Intent(context, AmbientPromptAlarmReceiver::class.java).setAction(action),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            alarms.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1),
                AlarmManager.INTERVAL_DAY,
                pending,
            )
        }
        assertEquals(listOf(1, 1), alarmCounts())
        markNativeDelivery(state)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            awaitState { stale.all { id -> work.getWorkInfoById(id).get()?.state == WorkInfo.State.CANCELLED } }
            assertTrue((listOf(periodic, signal) + immediate).all { name -> active(name).isEmpty() })
            assertEquals(listOf(0, 0), alarmCounts())
            verifyRetainedJournal(state)
        }
    }

    private suspend fun awaitState(condition: () -> Boolean) =
        withTimeout(30_000) {
            while (!condition()) delay(100)
        }

    companion object {
        private const val RETAINED_TEXT = "The retained journal stays readable while reminder work is reconstructed."
    }
}
