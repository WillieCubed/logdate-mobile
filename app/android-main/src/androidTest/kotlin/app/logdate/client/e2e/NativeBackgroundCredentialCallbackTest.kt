package app.logdate.client.e2e

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.data.restore.NativeAccountBackupAgent
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.datastore.NativeRestorePolicy
import app.logdate.client.device.restore.NativeBackupAgent
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.PostRestoreType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class NativeBackgroundCredentialCallbackTest {
    private val application = ApplicationProvider.getApplicationContext<Context>()
    private val folder = File(application.cacheDir, "background-restore-probe-${Uuid.random()}").apply { mkdirs() }
    private val context =
        object : ContextWrapper(application) {
            override fun getFilesDir(): File = File(folder, "files").apply { mkdirs() }

            override fun getNoBackupFilesDir(): File = File(folder, "private").apply { mkdirs() }

            override fun getDatabasePath(name: String): File = File(folder, "databases/$name")
        }

    @AfterTest
    fun cleanup() {
        folder.deleteRecursively()
    }

    @Test
    fun `manifest selects the account-capable native backup agent`() {
        val info = application.packageManager.getApplicationInfo(application.packageName, 0)
        assertEquals(NativeAccountBackupAgent::class.java.name, info.backupAgentName)
    }

    @Test
    fun `callback resets installation identity before bounded account recovery`() {
        var called = false
        val agent =
            object : NativeBackupAgent() {
                override suspend fun restoreAccount(preferences: DataStore<Preferences>) {
                    assertEquals(null, preferences.data.first()[stringPreferencesKey("device.id")])
                    assertNotNull(PostRestoreDetector(context).pendingAccountRecoveryId())
                    called = true
                }
            }
        withPreferences { it.edit { values -> values[stringPreferencesKey("device.id")] = "source-device" } }
        agent.initialize(context)
        agent.onRestoreFinished()
        assertTrue(called)
        assertEquals(PostRestoreType.CONTENT_MISSING, PostRestoreDetector(context).detect(true))
        assertTrue(PostRestoreDetector(context).isAccountRecoveryPending())
        // A second store after callback return proves its DataStore scope was closed.
        withPreferences { assertEquals(null, it.data.first()[stringPreferencesKey("device.id")]) }
    }

    @Test
    fun `duplicate framework restore callbacks retain one delivery ticket`() {
        val observedAccountRecoveries = mutableListOf<String?>()
        val agent =
            object : NativeBackupAgent() {
                override suspend fun restoreAccount(preferences: DataStore<Preferences>) {
                    observedAccountRecoveries += PostRestoreDetector(context).pendingAccountRecoveryId()
                }
            }
        agent.initialize(context)

        agent.onRestoreFinished()
        val delivery = assertNotNull(PostRestoreDetector(context).pendingContentRecoveryId())
        val account = assertNotNull(PostRestoreDetector(context).pendingAccountRecoveryId())
        val media = assertNotNull(PostRestoreDetector(context).pendingMediaQuarantineId())

        agent.onRestoreFinished()

        assertEquals(listOf<String?>(account, account), observedAccountRecoveries)
        assertEquals(delivery, PostRestoreDetector(context).pendingContentRecoveryId())
        assertEquals(account, PostRestoreDetector(context).pendingAccountRecoveryId())
        assertEquals(media, PostRestoreDetector(context).pendingMediaQuarantineId())
    }

    @Test
    fun `recreated backup agent retains tickets after a duplicate completion callback`() {
        val observedAccountRecoveries = mutableListOf<String?>()

        fun agent() =
            object : NativeBackupAgent() {
                override suspend fun restoreAccount(preferences: DataStore<Preferences>) {
                    observedAccountRecoveries += PostRestoreDetector(context).pendingAccountRecoveryId()
                }
            }.also { it.initialize(context) }

        val firstAgent = agent()

        firstAgent.onRestoreFinished()
        val delivery = assertNotNull(PostRestoreDetector(context).pendingContentRecoveryId())
        val account = assertNotNull(PostRestoreDetector(context).pendingAccountRecoveryId())
        val media = assertNotNull(PostRestoreDetector(context).pendingMediaQuarantineId())
        agent().onRestoreFinished()

        assertEquals(listOf<String?>(account, account), observedAccountRecoveries)
        assertEquals(delivery, PostRestoreDetector(context).pendingContentRecoveryId())
        assertEquals(account, PostRestoreDetector(context).pendingAccountRecoveryId())
        assertEquals(media, PostRestoreDetector(context).pendingMediaQuarantineId())
    }

    @Test
    fun `duplicate completion retries an interrupted installation identity reset`() {
        val preferencesFile = File(context.filesDir, "logdate.preferences_pb")
        withPreferences { preferences ->
            preferences.edit { values -> values[stringPreferencesKey("device.id")] = "source-device" }
        }
        preferencesFile.writeBytes(byteArrayOf(0xFF.toByte()))
        val observedDeviceIds = mutableListOf<String?>()
        val agent =
            object : NativeBackupAgent() {
                override suspend fun restoreAccount(preferences: DataStore<Preferences>) {
                    observedDeviceIds += preferences.data.first()[stringPreferencesKey("device.id")]
                }
            }
        agent.initialize(context)

        agent.onRestoreFinished()
        val content = assertNotNull(PostRestoreDetector(context).pendingContentRecoveryId())
        val account = assertNotNull(PostRestoreDetector(context).pendingAccountRecoveryId())
        val media = assertNotNull(PostRestoreDetector(context).pendingMediaQuarantineId())

        preferencesFile.delete()
        withPreferences { preferences ->
            preferences.edit { values -> values[stringPreferencesKey("device.id")] = "source-device" }
        }
        agent.onRestoreFinished()

        assertEquals(listOf<String?>(null), observedDeviceIds)
        assertEquals(content, PostRestoreDetector(context).pendingContentRecoveryId())
        assertEquals(account, PostRestoreDetector(context).pendingAccountRecoveryId())
        assertEquals(media, PostRestoreDetector(context).pendingMediaQuarantineId())
    }

    @Test
    fun `corrupt restored server leaves local content and account recovery pending`() {
        val owner = Uuid.random().toString()
        withPreferences {
            it.edit { values ->
                values[stringPreferencesKey("backend_url")] = "invalid-server"
                values[stringPreferencesKey("identity.canonical_owner_id")] = owner
                values[stringPreferencesKey("identity.canonical_owner_bound")] = "true"
            }
        }
        context.getDatabasePath("logdate").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        val agent = NativeAccountBackupAgent()
        agent.initialize(context)
        agent.onRestoreFinished()
        assertTrue(PostRestoreDetector(context).isAccountRecoveryPending())
        assertEquals(PostRestoreType.CONTENT_MISSING, PostRestoreDetector(context).detect(true))
        assertEquals(listOf<Byte>(1), context.getDatabasePath("logdate").readBytes().toList())
        withPreferences { assertEquals(owner, it.data.first()[stringPreferencesKey("identity.canonical_owner_id")]) }
    }

    @Test
    fun `transferred sign out preference prevents background account recovery without changing content`() {
        val owner = Uuid.random().toString()
        withPreferences { preferences ->
            preferences.edit { values ->
                values[stringPreferencesKey("backend_url")] = "https://restore.example"
                values[stringPreferencesKey("identity.canonical_owner_id")] = owner
                values[stringPreferencesKey("identity.canonical_owner_bound")] = "true"
            }
            NativeRestorePolicy(DataStoreKeyValueStorage(preferences)).setAllowed("https://restore.example", false)
        }
        context.getDatabasePath("logdate").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(2, 3))
        }
        val agent = NativeAccountBackupAgent()
        agent.initialize(context)
        agent.onRestoreFinished()
        assertTrue(PostRestoreDetector(context).isAccountRecoveryPending())
        assertEquals(listOf<Byte>(2, 3), context.getDatabasePath("logdate").readBytes().toList())
        withPreferences { preferences ->
            assertEquals(owner, preferences.data.first()[stringPreferencesKey("identity.canonical_owner_id")])
            assertEquals(false, NativeRestorePolicy(DataStoreKeyValueStorage(preferences)).canRestore("https://restore.example"))
        }
    }

    private fun NativeBackupAgent.initialize(context: Context) {
        ContextWrapper::class.java
            .getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply {
                isAccessible = true
            }.invoke(this, context)
    }

    private fun withPreferences(block: suspend (DataStore<Preferences>) -> Unit) =
        runBlocking {
            val job = SupervisorJob()
            try {
                block(
                    PreferenceDataStoreFactory.create(
                        scope = CoroutineScope(job + Dispatchers.IO),
                    ) { File(context.filesDir, "logdate.preferences_pb") },
                )
            } finally {
                job.cancelAndJoin()
            }
        }
}
