package app.logdate.client.data.restore

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.client.device.restore.NativeBackupAgent
import app.logdate.client.device.restore.PostRestoreDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NativeAccountRecoveryCallbackTest {
    @Test
    fun `callback retains the request before installation reset and distinguishes duplicate completion`() {
        val directory = Files.createTempDirectory("native-account-callback").toFile()
        try {
            val context =
                object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
                    override fun getFilesDir() = File(directory, "files").apply { mkdirs() }

                    override fun getNoBackupFilesDir() = File(directory, "private").apply { mkdirs() }

                    override fun getDatabasePath(name: String) = File(directory, "databases/$name")
                }
            val device = stringPreferencesKey("device.id")
            val requests = mutableListOf<Boolean>()
            val agent =
                object : NativeBackupAgent() {
                    override suspend fun retainAccountRecovery(
                        preferences: DataStore<Preferences>,
                        freshDelivery: Boolean,
                    ) {
                        if (freshDelivery) assertEquals("source-device", preferences.data.first()[device])
                        requests += freshDelivery
                    }

                    override suspend fun restoreAccount(preferences: DataStore<Preferences>) {
                        assertTrue(requests.isNotEmpty())
                        assertEquals(null, preferences.data.first()[device])
                    }
                }
            ContextWrapper::class.java
                .getDeclaredMethod(
                    "attachBaseContext",
                    Context::class.java,
                ).apply { isAccessible = true }
                .invoke(agent, context)
            runBlocking { createAndroidMainDataStore(context).edit { it[device] = "source-device" } }
            agent.onRestoreFinished()
            agent.onRestoreFinished()
            assertEquals(listOf(true, false), requests)
        } finally {
            directory.deleteRecursively()
        }
    }

    private class SimulatedProcessDeath : Error()

    @Test
    fun `account work is retained before completion and reused after a callback process death`() {
        val directory = Files.createTempDirectory("native-account-admission").toFile()
        try {
            val context =
                object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
                    override fun getFilesDir() = File(directory, "files").apply { mkdirs() }

                    override fun getNoBackupFilesDir() = File(directory, "private").apply { mkdirs() }

                    override fun getDatabasePath(name: String) = File(directory, "databases/$name")
                }

            fun agent(interrupt: Boolean): NativeBackupAgent {
                val agent =
                    object : NativeBackupAgent() {
                        override val retainedPreferenceNames = setOf("logdate_widget_instances")

                        override suspend fun retainAccountRecoveryBeforeCompletion(
                            preferences: DataStore<Preferences>,
                            deliveryId: String,
                        ) {
                            val generation = assertNotNull(PostRestoreDetector(context).nativeRestoreSessionId())
                            val request = assertNotNull(nativeAccountRecoveryRequest(deliveryId, generation, preferences.data.first()))
                            assertTrue(NativeAccountRecoveryInbox(context.noBackupFilesDir).retainFreshDelivery(request))
                            assertTrue(scheduleNativeAccountRecovery(context, request))
                            if (interrupt) throw SimulatedProcessDeath()
                        }
                    }
                ContextWrapper::class.java
                    .getDeclaredMethod(
                        "attachBaseContext",
                        Context::class.java,
                    ).apply { isAccessible = true }
                    .invoke(agent, context)
                return agent
            }
            assertFailsWith<SimulatedProcessDeath> { agent(true).onRestoreFinished() }
            val retained = assertNotNull(NativeAccountRecoveryInbox(context.noBackupFilesDir).pending())
            assertFalse(PostRestoreDetector(context).hasCompletedNativeRestoreDelivery())
            val scheduler = context.getSystemService(android.app.job.JobScheduler::class.java)
            assertTrue(scheduler.allPendingJobs.single().isPersisted)
            assertTrue(
                runBlocking {
                    app.logdate.client.device.restore
                        .completeNativeAccountRecoveryDelivery(context, retained.delivery, retained.generation)
                },
            )
            agent(false).onRestoreFinished()
            assertTrue(PostRestoreDetector(context).hasCompletedNativeRestoreDelivery())
            assertEquals(retained, NativeAccountRecoveryInbox(context.noBackupFilesDir).pending())
            assertEquals(retained.delivery, PostRestoreDetector(context).pendingAccountRecoveryId())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `startup reschedules retained work after scheduling failed before completion`() {
        val directory = Files.createTempDirectory("native-account-schedule-retry").toFile()
        try {
            val base = ApplicationProvider.getApplicationContext<Application>()
            val scheduler = base.getSystemService(android.app.job.JobScheduler::class.java)
            var fail = true
            val failing =
                org.robolectric.shadow.api.Shadow
                    .newInstanceOf(scheduler.javaClass)
            org.robolectric.Shadows
                .shadowOf(failing)
                .failOnJob(0x4e415252)
            val context =
                object : ContextWrapper(base) {
                    override fun getFilesDir() = File(directory, "files").apply { mkdirs() }

                    override fun getNoBackupFilesDir() = File(directory, "private").apply { mkdirs() }

                    override fun getDatabasePath(name: String) = File(directory, "databases/$name")

                    override fun getSystemService(name: String): Any? =
                        if (name ==
                            Context.JOB_SCHEDULER_SERVICE
                        ) {
                            (if (fail) failing else scheduler)
                        } else {
                            super.getSystemService(name)
                        }
                }
            val agent =
                object : NativeBackupAgent() {
                    override suspend fun retainAccountRecoveryBeforeCompletion(
                        preferences: DataStore<Preferences>,
                        deliveryId: String,
                    ) {
                        PostRestoreDetector(context).markNativePrivacyPayloadReceived()
                        val generation = assertNotNull(PostRestoreDetector(context).nativeRestoreSessionId())
                        val request = assertNotNull(nativeAccountRecoveryRequest(deliveryId, generation, preferences.data.first()))
                        assertTrue(NativeAccountRecoveryInbox(context.noBackupFilesDir).retainFreshDelivery(request))
                        check(scheduleNativeAccountRecovery(context, request))
                    }
                }
            ContextWrapper::class.java
                .getDeclaredMethod(
                    "attachBaseContext",
                    Context::class.java,
                ).apply { isAccessible = true }
                .invoke(agent, context)
            assertFailsWith<IllegalStateException> { agent.onRestoreFinished() }
            val retained = assertNotNull(NativeAccountRecoveryInbox(context.noBackupFilesDir).pending())
            assertFalse(PostRestoreDetector(context).hasCompletedNativeRestoreDelivery())
            assertTrue(scheduler.allPendingJobs.isEmpty())
            fail = false
            org.robolectric.Shadows
                .shadowOf(base.getSystemService(android.os.UserManager::class.java))
                .setUserUnlocked(true)
            assertFalse(PostRestoreDetector(context).hasMissingNativePrivacyPayload())
            resumeNativeAccountRecovery(context)
            val job = scheduler.allPendingJobs.single()
            assertTrue(job.isPersisted)
            assertEquals(
                retained,
                kotlinx.serialization.json.Json
                    .decodeFromString<NativeAccountRecoveryRequest>(job.extras.getString("request")!!),
            )
            assertTrue(
                runBlocking {
                    app.logdate.client.device.restore
                        .completeNativeAccountRecoveryDelivery(context, retained.delivery, retained.generation)
                },
            )
            assertEquals(retained.delivery, PostRestoreDetector(context).pendingAccountRecoveryId())
            assertEquals(retained.generation, PostRestoreDetector(context).nativeRestoreSessionId())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `duplicate completion after a partial owner commit preserves original system custody`() {
        val directory = Files.createTempDirectory("native-account-duplicate-commit").toFile()
        try {
            val context =
                object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
                    override fun getFilesDir() = File(directory, "files").apply { mkdirs() }

                    override fun getNoBackupFilesDir() = File(directory, "private").apply { mkdirs() }

                    override fun getDatabasePath(name: String) = File(directory, "databases/$name")
                }

            fun attach(agent: NativeBackupAgent) {
                ContextWrapper::class.java
                    .getDeclaredMethod(
                        "attachBaseContext",
                        Context::class.java,
                    ).apply { isAccessible = true }
                    .invoke(agent, context)
            }
            val initial =
                object : NativeBackupAgent() {
                    override suspend fun retainAccountRecoveryBeforeCompletion(
                        preferences: DataStore<Preferences>,
                        deliveryId: String,
                    ) {
                        val detector = PostRestoreDetector(context)
                        detector.markNativePrivacyPayloadReceived()
                        val request =
                            assertNotNull(
                                nativeAccountRecoveryRequest(
                                    deliveryId,
                                    assertNotNull(detector.nativeRestoreSessionId()),
                                    preferences.data.first(),
                                ),
                            )
                        assertTrue(NativeAccountRecoveryInbox(context.noBackupFilesDir).retainFreshDelivery(request))
                        assertTrue(scheduleNativeAccountRecovery(context, request))
                    }
                }
            attach(initial)
            initial.onRestoreFinished()
            val inbox = NativeAccountRecoveryInbox(context.noBackupFilesDir)
            val original = assertNotNull(inbox.pending())
            val owner = "347c6231-19f4-49fa-bd1d-58ed34b9be03"
            assertTrue(inbox.acceptTarget(original, owner))
            runBlocking {
                createAndroidMainDataStore(context).edit {
                    it[stringPreferencesKey("identity.canonical_owner_id")] = owner
                    it[stringPreferencesKey("identity.canonical_owner_bound")] = "true"
                    it[stringPreferencesKey("identity.native_restore_commit")] = nativeAccountRecoveryCommitStamp(original, owner)
                }
            }
            File(context.noBackupFilesDir, "native-account-recovery.json").writeText("truncated")
            val duplicate = NativeAccountBackupAgent()
            attach(duplicate)
            duplicate.onRestoreFinished()
            val job = context.getSystemService(android.app.job.JobScheduler::class.java).allPendingJobs.single()
            assertEquals(
                original,
                kotlinx.serialization.json.Json
                    .decodeFromString<NativeAccountRecoveryRequest>(job.extras.getString("request")!!),
            )
            assertTrue(
                runBlocking {
                    recoverNativeAccountRecoveryCustody(
                        original,
                        createAndroidMainDataStore(context).data.first(),
                        inbox,
                        false,
                    )
                },
            )
            assertEquals(original, inbox.pending())
            assertEquals(owner, inbox.acceptedTarget(original))
        } finally {
            directory.deleteRecursively()
        }
    }
}
