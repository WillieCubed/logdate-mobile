package app.logdate.client.e2e

import android.app.backup.BackupAgent
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.UserManager
import android.system.Os
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.data.restore.NativeAccountBackupAgent
import app.logdate.client.database.encryption.DatabaseKeyRecoveryRequiredException
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.datastore.NativeRestorePolicy
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.client.device.restore.NativeBackupAgent
import app.logdate.client.device.restore.NativeRestorePrivacyGuard
import app.logdate.client.device.restore.NativeRestorePrivacyMetadataRequiredException
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.PostRestoreType
import app.logdate.client.device.restore.nativeRestoreDeliveryCompletions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class NativeBackgroundCredentialCallbackTest {
    private val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val testFilesRoot =
        application.createDeviceProtectedStorageContext().getDir("native-restore-probe", Context.MODE_PRIVATE)
    private val folder =
        File(testFilesRoot, "background-restore-probe-${Uuid.random()}").apply {
            check(mkdirs() || isDirectory) {
                val failure = runCatching { Os.mkdir(absolutePath, 0x1C0) }.exceptionOrNull()
                "Could not create isolated restore probe directory in app-private test storage " +
                    "(uid=${Process.myUid()}, appUid=${application.applicationInfo.uid}, " +
                    "unlocked=${application.getSystemService(UserManager::class.java).isUserUnlocked}, " +
                    "parent=${parentFile?.absolutePath}, parentExists=${parentFile?.isDirectory}, " +
                    "appRootExists=${testFilesRoot.parentFile?.isDirectory}, cause=${failure?.message})"
            }
        }
    private val context =
        // The test APK has no backup include list restricting this isolated fixture to the real database path.
        object : ContextWrapper(InstrumentationRegistry.getInstrumentation().context) {
            override fun getApplicationContext(): Context = this

            override fun getCacheDir(): File = File(folder, "cache").apply { mkdirs() }

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
    fun `native backup delivery retries failed key startup and opens restored content without user retry`() =
        runBlocking {
            val keyFile = File(context.filesDir, "db_recovery/passphrase_backup")
            val source = File(folder, "late-native-key").apply { writeText("restored-key-material") }
            val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val deliveries = nativeRestoreDeliveryCompletions()
            val baseline = deliveries.value
            val testDeliveries =
                deliveries.map { it - baseline }.stateIn(startupScope, SharingStarted.Eagerly, 0L)
            var openedContent: String? = null
            val initialize: suspend () -> Unit = {
                if (!keyFile.isFile) throw DatabaseKeyRecoveryRequiredException()
                openedContent = keyFile.readText()
            }
            // The application implementation belongs to compose-main, a separate Kotlin module.
            // Reflection exercises its internal coordinator without exposing a production test API.
            val startupType = Class.forName("app.logdate.client.launch.ApplicationStartup")
            val startup =
                startupType.declaredConstructors
                    .single { it.parameterCount == 3 }
                    .newInstance(startupScope, null, initialize)
            val state = startupType.getDeclaredMethod("getState").invoke(startup) as StateFlow<*>
            val reconcile: suspend () -> Unit = {}
            val observer =
                Class
                    .forName("app.logdate.client.launch.ApplicationStartupKt")
                    .declaredMethods
                    .single { it.name == "observeNativeRestoreDeliveryCompletions" }
                    .invoke(
                        null,
                        startupScope,
                        testDeliveries,
                        startup,
                        reconcile,
                    ) as Job
            val agent = object : NativeBackupAgent() {}.also { it.initialize(context) }
            try {
                startupType.getDeclaredMethod("start").invoke(startup)
                withTimeout(5_000) { state.first { it?.javaClass?.simpleName == "Failed" } }

                keyFile.parentFile?.mkdirs()
                ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    agent.onRestoreFile(
                        descriptor,
                        source.length(),
                        keyFile,
                        BackupAgent.TYPE_FILE,
                        0x180L,
                        1L,
                    )
                }
                assertEquals(baseline, deliveries.value)
                assertEquals("Failed", state.value?.javaClass?.simpleName)
                agent.onRestoreFinished()
                assertTrue(deliveries.value > baseline, "The actual native completion must trigger the retry")

                withTimeout(10_000) { state.first { it?.javaClass?.simpleName == "Ready" } }
                assertEquals("restored-key-material", openedContent)
            } finally {
                try {
                    agent.onDestroy()
                } finally {
                    observer.cancelAndJoin()
                    startupScope.cancel()
                }
            }
        }

    @Test
    fun modernFullRestoreCallbackCopiesFileAndCompletesRecoveryWhenAvailable() {
        InstrumentationRegistry.getArguments().getString("nativeRestoreRequiredSdkFull")?.let { required ->
            assertTrue(Build.VERSION.SDK_INT >= 36, "This acceptance run requires a modern restore platform")
            assertEquals(required.toInt(), Build.VERSION.SDK_INT_FULL, "The callback must execute on the requested minor SDK")
        }
        assertTrue(context.cacheDir.isDirectory, "The callback's isolated cache directory must exist")
        assertTrue(context.cacheDir.canWrite(), "The callback's isolated cache directory must be writable")
        if (!transferredPreferences.supportsFullRestoreInput()) return
        val inputType =
            assertNotNull(
                try {
                    Class.forName("android.app.backup.FullRestoreDataInput")
                } catch (_: ClassNotFoundException) {
                    null
                },
                "API 36.1+ must provide FullRestoreDataInput for native full restore",
            )

        val content = "modern Android full restore callback".encodeToByteArray()
        val source = File(folder, "modern-restore-source").apply { writeBytes(content) }
        val destination = context.getDatabasePath("logdate")
        destination.parentFile?.mkdirs()
        val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        val input = nativeModernRestoreInput(inputType, descriptor, destination, content.size.toLong(), 0x1A4L)

        var accountRecoveryStarted = false
        val agent =
            object : NativeBackupAgent() {
                override suspend fun restoreAccount(preferences: DataStore<Preferences>) {
                    accountRecoveryStarted = true
                }
            }
        agent.initialize(context)
        try {
            NativeBackupAgent::class.java.getDeclaredMethod("onRestoreFile", inputType).invoke(agent, input)
        } finally {
            descriptor.close()
        }
        agent.onRestoreFinished()

        assertEquals(content.toList(), destination.readBytes().toList())
        assertEquals(PostRestoreType.CONTENT_RESTORED, PostRestoreDetector(context).detect(true))
        assertTrue(accountRecoveryStarted)
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
        // Normal application consumers observe the callback's committed reset.
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
        runBlocking { deliverPreferences(agent, incomingPreferences("BIOMETRIC")) }
        agent.onRestoreFinished()
        val delivery = assertNotNull(PostRestoreDetector(context).pendingContentRecoveryId())
        val account = assertNotNull(PostRestoreDetector(context).pendingAccountRecoveryId())
        val media = assertNotNull(PostRestoreDetector(context).pendingMediaQuarantineId())
        val generation = PostRestoreDetector(context).nativeRestoreSessionId()
        assertTrue(!PostRestoreDetector(context).hasMissingNativePrivacyPayload())

        agent.onRestoreFinished()

        assertEquals(listOf<String?>(account, account), observedAccountRecoveries)
        assertEquals(generation, PostRestoreDetector(context).nativeRestoreSessionId())
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
        runBlocking { deliverPreferences(firstAgent, incomingPreferences("BIOMETRIC")) }
        firstAgent.onRestoreFinished()
        val delivery = assertNotNull(PostRestoreDetector(context).pendingContentRecoveryId())
        val account = assertNotNull(PostRestoreDetector(context).pendingAccountRecoveryId())
        val media = assertNotNull(PostRestoreDetector(context).pendingMediaQuarantineId())
        val generation = PostRestoreDetector(context).nativeRestoreSessionId()
        assertTrue(!PostRestoreDetector(context).hasMissingNativePrivacyPayload())
        agent().onRestoreFinished()

        assertEquals(listOf<String?>(account, account), observedAccountRecoveries)
        assertEquals(generation, PostRestoreDetector(context).nativeRestoreSessionId())
        assertEquals(delivery, PostRestoreDetector(context).pendingContentRecoveryId())
        assertEquals(account, PostRestoreDetector(context).pendingAccountRecoveryId())
        assertEquals(media, PostRestoreDetector(context).pendingMediaQuarantineId())
    }

    @Test
    fun `duplicate completion after app launch preserves the new installation identity`() {
        withPreferences { preferences ->
            preferences.edit { values -> values[stringPreferencesKey("device.id")] = "source-device" }
        }
        val firstAgent = object : NativeBackupAgent() {}.also { it.initialize(context) }
        firstAgent.onRestoreFinished()

        withPreferences { preferences ->
            preferences.edit { values -> values[stringPreferencesKey("device.id")] = "destination-device" }
        }
        val recreatedAgent = object : NativeBackupAgent() {}.also { it.initialize(context) }
        recreatedAgent.onRestoreFinished()

        withPreferences { preferences ->
            assertEquals("destination-device", preferences.data.first()[stringPreferencesKey("device.id")])
        }
    }

    @Test
    fun `duplicate completion retries an interrupted installation identity reset`() {
        val observedDeviceIds = mutableListOf<String?>()
        val agent =
            object : NativeBackupAgent() {
                override suspend fun retainAccountRecovery(
                    preferences: DataStore<Preferences>,
                    freshDelivery: Boolean,
                ) {
                    if (freshDelivery) error("Simulated interruption before installation reset")
                }

                override suspend fun restoreAccount(preferences: DataStore<Preferences>) {
                    observedDeviceIds += preferences.data.first()[stringPreferencesKey("device.id")]
                }
            }
        agent.initialize(context)
        runBlocking { deliverPreferences(agent, incomingPreferences("BIOMETRIC", "source-device")) }
        agent.onRestoreFinished()
        assertTrue(observedDeviceIds.isEmpty())
        withPreferences { assertEquals("source-device", it.data.first()[stringPreferencesKey("device.id")]) }
        val generation = PostRestoreDetector(context).nativeRestoreSessionId()
        val content = assertNotNull(PostRestoreDetector(context).pendingContentRecoveryId())
        val account = assertNotNull(PostRestoreDetector(context).pendingAccountRecoveryId())
        val media = assertNotNull(PostRestoreDetector(context).pendingMediaQuarantineId())

        agent.onRestoreFinished()

        assertEquals(listOf<String?>(null), observedDeviceIds)
        assertEquals(generation, PostRestoreDetector(context).nativeRestoreSessionId())
        withPreferences { assertEquals("BIOMETRIC", it.data.first()[stringPreferencesKey("security_level")]) }
        assertEquals(content, PostRestoreDetector(context).pendingContentRecoveryId())
        assertEquals(account, PostRestoreDetector(context).pendingAccountRecoveryId())
        assertEquals(media, PostRestoreDetector(context).pendingMediaQuarantineId())
    }

    @Test
    fun corruptPrivacyDuplicatePreservesBytesAndAccountDelivery() {
        val preferencesFile = File(context.filesDir, "logdate.preferences_pb")
        withUncachedPreferences { preferences ->
            preferences.edit { it[stringPreferencesKey("device.id")] = "source-device" }
        }
        val corrupt = byteArrayOf(0xFF.toByte())
        preferencesFile.writeBytes(corrupt)
        var accountRetries = 0
        val agent =
            object : NativeBackupAgent() {
                override suspend fun restoreAccount(preferences: DataStore<Preferences>) {
                    accountRetries++
                }
            }.also { it.initialize(context) }
        agent.onRestoreFinished()
        val accepted = assertNotNull(PostRestoreDetector(context).capturePrivacyRepairIdentity())
        agent.onRestoreFinished()
        assertEquals(0, accountRetries)
        assertEquals(accepted, PostRestoreDetector(context).capturePrivacyRepairIdentity())
        assertEquals(corrupt.toList(), preferencesFile.readBytes().toList())
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

    @Test
    fun cachedUnlockedPolicyIsReplacedByTransferredBiometricPolicyInSameProcess() =
        runBlocking {
            val live =
                app.logdate.client.datastore
                    .createAndroidMainDataStore(context)
            val cached =
                app.logdate.client.datastore
                    .LogdatePreferencesDataSource(live)
                    .userData
            assertEquals("NONE", cached.first().securityLevel.name)
            assertTrue(!File(context.filesDir, "logdate.preferences_pb").exists(), "Default observation must not occupy the destination")
            val agent = object : NativeBackupAgent() {}.also { it.initialize(context) }
            deliverPreferences(agent, incomingPreferences("BIOMETRIC"))
            agent.onRestoreFinished()
            assertEquals("BIOMETRIC", live.data.first()[stringPreferencesKey("security_level")])
            assertEquals("BIOMETRIC", cached.first().securityLevel.name)
            assertTrue(!PostRestoreDetector(context).hasMissingNativePrivacyPayload())
        }

    @Test
    fun incompletePolicyCanBeRepairedWithoutReplacingLivePreferencesReaders() =
        runBlocking {
            val live =
                app.logdate.client.datastore
                    .createAndroidMainDataStore(context)
            val agent = object : NativeBackupAgent() {}.also { it.initialize(context) }
            deliverPreferences(agent, incomingPreferences(null))
            agent.onRestoreFinished()
            val guard =
                app.logdate.client.device.restore
                    .NativeRestorePrivacyGuard(context, live)
            assertFailsWith<NativeRestorePrivacyMetadataRequiredException> {
                guard.verifyBeforeContent { true }
            }
            val detector = PostRestoreDetector(context)
            val identity = assertNotNull(detector.capturePrivacyRepairIdentity())
            deliverPreferences(agent, incomingPreferences("BIOMETRIC"))
            assertEquals(identity, detector.capturePrivacyRepairIdentity(), "Receiving bytes is not policy completion")
            agent.onRestoreFinished()
            guard.verifyBeforeContent { true }
            assertEquals(identity.copy(privacyReceived = true), detector.capturePrivacyRepairIdentity())
            assertEquals("BIOMETRIC", live.data.first()[stringPreferencesKey("security_level")])
        }

    private val transferredPreferences by lazy { NativeBackgroundRestorePreferences(context, folder) }

    private fun deliverPreferences(
        agent: NativeBackupAgent,
        source: File,
    ) = transferredPreferences.deliver(agent, source)

    private suspend fun incomingPreferences(
        policy: String?,
        deviceId: String? = null,
    ): File = transferredPreferences.incoming(policy, deviceId)

    private fun withPreferences(block: suspend (DataStore<Preferences>) -> Unit) =
        runBlocking { block(createAndroidMainDataStore(context)) }

    private fun withUncachedPreferences(block: suspend (DataStore<Preferences>) -> Unit) =
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
