package app.logdate.client.e2e

import android.app.backup.BackupAgent
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.device.restore.NativeBackupAgent
import app.logdate.client.device.restore.NativeRestoredPreferenceFiles
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.PostRestoreType
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class NativePreferenceDeliveryRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext.applicationContext
    private val folder =
        File(
            application.createDeviceProtectedStorageContext().getDir("native-preference-probe", Context.MODE_PRIVATE),
            Uuid.random().toString(),
        ).apply { check(mkdirs()) }
    private val context =
        object : ContextWrapper(instrumentation.context) {
            override fun getApplicationContext(): Context = this

            override fun getApplicationInfo(): ApplicationInfo =
                ApplicationInfo(super.getApplicationInfo()).apply { dataDir = File(folder, "data").absolutePath }

            override fun getFilesDir(): File = File(folder, "files").apply { mkdirs() }

            override fun getCacheDir(): File = File(folder, "cache").apply { mkdirs() }

            override fun getNoBackupFilesDir(): File = File(folder, "private").apply { mkdirs() }

            override fun getDatabasePath(name: String): File = File(folder, "databases/$name")
        }
    private val names = setOf("logdate_widget_instances")
    private val detector = PostRestoreDetector(context)
    private val retained = NativeRestoredPreferenceFiles(context)

    @After
    fun cleanup() {
        folder.deleteRecursively()
    }

    @Test
    fun truncatedPreferenceDeliveryStaysPendingUntilACompleteRetry() {
        val agent = agent()
        try {
            val destination = File(context.applicationInfo.dataDir, "shared_prefs/logdate_widget_instances.xml")
            val incomplete = "<map/>".encodeToByteArray()
            assertFailsWith<IOException> { deliverFile(agent, destination, incomplete, declaredSize = 100) }
            assertTrue(!destination.exists(), "Incomplete incoming bytes must never reach the destination")
            val generation = assertNotNull(detector.retainedPreferenceSessionId())
            assertFailsWith<IOException> { agent.onRestoreFinished() }
            assertEquals(generation, detector.retainedPreferenceSessionId())
            assertTrue(!detector.hasCompletedNativeRestoreDelivery())

            val complete = "<map><string name=\"7.chosenNoteId\">complete-entry</string></map>".encodeToByteArray()
            deliverFile(agent, destination, complete)
            agent.onRestoreFinished()

            assertTrue(detector.hasCompletedNativeRestoreDelivery())
            assertContentEquals(complete, destination.readBytes())
            assertContentEquals(complete, NativeRestoredPreferenceFiles(context).read(names.single()))
        } finally {
            agent.onDestroy()
        }
    }

    @Test
    fun preparedPreferenceInputRepairsInterruptedDestinationOnRecreatedCompletion() {
        assertPreparedRecovery(failReplayReceipt = false)
    }

    @Test
    fun preparedPreferenceReplaySurvivesAnotherReceiptFailureWithoutRedelivery() {
        assertPreparedRecovery(failReplayReceipt = true)
    }

    private fun assertPreparedRecovery(failReplayReceipt: Boolean) {
        val original = agent()
        var completion = original
        try {
            deliverFile(original, context.getDatabasePath("logdate"), "synthetic database payload".encodeToByteArray())
            val generation = assertNotNull(detector.retainedPreferenceSessionId())
            val destination = File(context.applicationInfo.dataDir, "shared_prefs/logdate_widget_instances.xml")
            assertEquals(names.single(), retained.observe(destination, names))
            val transferred = "<map><string name=\"7.chosenNoteId\">prepared-entry</string></map>".encodeToByteArray()
            val snapshot = File(context.noBackupFilesDir, "native-restored-preferences/$generation/logdate_widget_instances.json")
            val encoded = Base64.getEncoder().encodeToString(transferred)
            snapshot.writeText("""{"generation":"$generation","state":"prepared","source":"$encoded"}""")
            destination.parentFile?.mkdirs()
            destination.writeText("<map>")
            original.onDestroy()
            completion = agent()
            if (failReplayReceipt) {
                val obstruction = File(snapshot.path + ".new")
                assertTrue(obstruction.mkdir())
                val retainedObstruction = File(obstruction, "keep").apply { writeText("Synthetic receipt-write fault") }
                assertFailsWith<IOException> { completion.onRestoreFinished() }
                assertContentEquals(transferred, destination.readBytes())
                assertTrue(snapshot.readText().contains("\"state\":\"prepared\""))
                assertTrue(!detector.hasCompletedNativeRestoreDelivery())
                completion.onDestroy()
                assertTrue(retainedObstruction.delete())
                assertTrue(obstruction.delete())
                completion = agent()
            }
            completion.onRestoreFinished()

            assertEquals(generation, detector.retainedPreferenceSessionId())
            assertTrue(detector.hasCompletedNativeRestoreDelivery())
            assertEquals(PostRestoreType.CONTENT_RESTORED, detector.detect(isOnboarded = true))
            assertContentEquals(transferred, destination.readBytes())
            assertContentEquals(transferred, NativeRestoredPreferenceFiles(context).read(names.single(), generation))
        } finally {
            completion.onDestroy()
        }
    }

    @Test
    fun failedPreferenceObservationCannotOverwriteTheDestination() {
        val agent = agent()
        try {
            deliverMetadata(agent)
            val generation = assertNotNull(detector.retainedPreferenceSessionId())
            val snapshot = File(context.noBackupFilesDir, "native-restored-preferences/$generation/logdate_widget_instances.json")
            assertTrue(snapshot.delete())
            assertTrue(snapshot.mkdir())
            val destination = File(context.applicationInfo.dataDir, "shared_prefs/logdate_widget_instances.xml")
            destination.parentFile?.mkdirs()
            val existing = "<map><string name=\"7.chosenNoteId\">existing-entry</string></map>".encodeToByteArray()
            destination.writeBytes(existing)

            assertFailsWith<IOException> { deliverFile(agent, destination, "<map/>".encodeToByteArray()) }

            assertContentEquals(existing, destination.readBytes())
            assertTrue(!detector.hasCompletedNativeRestoreDelivery())
        } finally {
            agent.onDestroy()
        }
    }

    @Test
    fun duplicatePreferenceDeliveryCannotReplaceAcceptedBytes() {
        val agent = agent()
        try {
            val destination = File(context.applicationInfo.dataDir, "shared_prefs/logdate_widget_instances.xml")
            val transferred = "<map><string name=\"7.chosenNoteId\">first-entry</string></map>".encodeToByteArray()
            deliverFile(agent, destination, transferred)
            val generation = assertNotNull(detector.retainedPreferenceSessionId())

            deliverFile(agent, destination, "<map/>".encodeToByteArray())
            agent.onRestoreFinished()

            assertEquals(generation, detector.retainedPreferenceSessionId())
            assertContentEquals(transferred, destination.readBytes())
            assertContentEquals(transferred, NativeRestoredPreferenceFiles(context).read(names.single(), generation))
        } finally {
            agent.onDestroy()
        }
    }

    @Test
    fun finalManifestWriteFailureKeepsDeliveredPreferencesOnSameAgentRetry() {
        assertManifestFailureRecovery(recreate = false)
    }

    @Test
    fun finalManifestWriteFailureKeepsDeliveredPreferencesOnRecreatedAgentRetry() {
        assertManifestFailureRecovery(recreate = true)
    }

    private fun assertManifestFailureRecovery(recreate: Boolean) {
        val original = agent()
        var completion = original
        try {
            val source = File(context.applicationInfo.dataDir, "shared_prefs/logdate_widget_instances.xml")
            val transferred = "<map><string name=\"7.chosenNoteId\">retained-entry</string></map>".encodeToByteArray()
            deliverFile(original, source, transferred)
            deliverFile(original, context.getDatabasePath("logdate"), "synthetic database payload".encodeToByteArray())
            val generation = assertNotNull(detector.retainedPreferenceSessionId())
            val obstruction = File(context.noBackupFilesDir, "native-restored-preferences/$generation/.delivery.json.part")
            assertTrue(obstruction.mkdir())

            assertFailsWith<IOException> { original.onRestoreFinished() }
            assertTrue(!detector.hasCompletedNativeRestoreDelivery())
            assertTrue(obstruction.delete())
            if (recreate) {
                original.onDestroy()
                completion = agent()
            }
            completion.onRestoreFinished()

            assertEquals(generation, detector.retainedPreferenceSessionId())
            assertEquals(PostRestoreType.CONTENT_RESTORED, detector.detect(isOnboarded = true))
            assertContentEquals(transferred, NativeRestoredPreferenceFiles(context).read(names.single(), generation))
        } finally {
            completion.onDestroy()
        }
    }

    @Test
    fun omittedPreferencesRecoverAfterSnapshotFinalizationFailureAndContentAcknowledgement() {
        val agent = agent()
        try {
            deliverMetadata(agent)
            val generation = assertNotNull(detector.retainedPreferenceSessionId())
            val snapshot = File(context.noBackupFilesDir, "native-restored-preferences/$generation/logdate_widget_instances.json")
            assertTrue(snapshot.delete())
            assertTrue(snapshot.mkdir(), "Only the isolated snapshot path is obstructed")

            agent.onRestoreFinished()
            assertTrue(detector.hasCompletedNativeRestoreDelivery())
            assertTrue(snapshot.delete())

            assertNull(NativeRestoredPreferenceFiles(context).read(names.single(), generation))
            assertTrue(detector.acknowledgeContentRecovery(assertNotNull(detector.pendingContentRecoveryId())))
            assertNull(NativeRestoredPreferenceFiles(context).read(names.single(), generation))
            assertEquals(generation, detector.retainedPreferenceSessionId())
        } finally {
            agent.onDestroy()
        }
    }

    @Test
    fun recreatedCompletionResumesTheFinalDeliveryAndNeverRecapturesLaterWidgetEdits() {
        detector.beginNativeRestoreSession(retainPreferences = true)
        retained.begin(names)
        val generation = assertNotNull(detector.retainedPreferenceSessionId())
        val source = File(context.applicationInfo.dataDir, "shared_prefs/logdate_widget_instances.xml")
        val transferred = "<map><string name=\"7.chosenNoteId\">transferred-entry</string></map>".encodeToByteArray()
        source.parentFile?.mkdirs()
        source.writeBytes(transferred)
        File(context.noBackupFilesDir, "native-restored-preferences/$generation/.delivery.json").writeText(
            """{"generation":"$generation","names":["logdate_widget_instances"],"delivered":["logdate_widget_instances"]}""",
        )
        val recreated = agent()
        try {
            recreated.onRestoreFinished()

            assertEquals(generation, detector.retainedPreferenceSessionId())
            assertContentEquals(transferred, NativeRestoredPreferenceFiles(context).read(names.single(), generation))
            assertTrue(detector.acknowledgeContentRecovery(assertNotNull(detector.pendingContentRecoveryId())))
            source.writeText("<map/>")
            assertContentEquals(transferred, NativeRestoredPreferenceFiles(context).read(names.single(), generation))

            detector.beginNativeRestoreSession(retainPreferences = true)
            assertContentEquals(transferred, NativeRestoredPreferenceFiles(context).read(names.single(), generation))
        } finally {
            recreated.onDestroy()
        }
    }

    @Test
    fun unfinishedHistoricalSnapshotCannotCapturePreferencesFromAnotherDelivery() {
        detector.beginNativeRestoreSession(retainPreferences = true)
        retained.begin(names)
        val previous = assertNotNull(detector.retainedPreferenceSessionId())
        val source = File(context.applicationInfo.dataDir, "shared_prefs/logdate_widget_instances.xml")
        source.parentFile?.mkdirs()
        source.writeText("<map/>")
        assertEquals(names.single(), retained.observe(source, names))
        detector.recordRestoreCompleted()

        detector.beginNativeRestoreSession(retainPreferences = true)
        val current = "<map><string name=\"7.chosenNoteId\">current-delivery</string></map>".encodeToByteArray()
        source.writeBytes(current)
        detector.recordRestoreCompleted()

        assertFailsWith<IllegalStateException> { NativeRestoredPreferenceFiles(context).read(names.single(), previous) }
        assertContentEquals(current, source.readBytes())
    }

    private fun agent(): NativeBackupAgent =
        object : NativeBackupAgent() {
            override val retainedPreferenceNames: Set<String> = names
        }.also {
            ContextWrapper::class.java
                .getDeclaredMethod("attachBaseContext", Context::class.java)
                .apply { isAccessible = true }
                .invoke(it, context)
        }

    private fun deliverMetadata(agent: NativeBackupAgent) {
        deliverFile(agent, File(context.filesDir, "metadata.bin"), "synthetic native delivery".encodeToByteArray())
    }

    private fun deliverFile(
        agent: NativeBackupAgent,
        destination: File,
        bytes: ByteArray,
        declaredSize: Long = bytes.size.toLong(),
    ) {
        val source = File(folder, "incoming-${Uuid.random()}").apply { writeBytes(bytes) }
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use {
            agent.onRestoreFile(it, declaredSize, destination, BackupAgent.TYPE_FILE, 0x180L, 1L)
        }
    }
}
