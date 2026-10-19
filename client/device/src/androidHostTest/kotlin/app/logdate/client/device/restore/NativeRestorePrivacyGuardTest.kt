package app.logdate.client.device.restore

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeRestorePrivacyGuardTest {
    private val root = createTempDirectory("native-privacy").toFile()
    private val files = File(root, "files").apply { mkdirs() }
    private val noBackup = File(root, "no_backup").apply { mkdirs() }
    private val context =
        mockk<Context>().also {
            every { it.filesDir } returns files
            every { it.noBackupFilesDir } returns noBackup
            every { it.applicationInfo } returns ApplicationInfo().apply { dataDir = root.path }
            every { it.getDatabasePath(any()) } answers { File(root, firstArg<String>()) }
        }
    private val detector = PostRestoreDetector(context)
    private val store = MemoryStore()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `only proven empty fresh storage may establish backup defaults`() {
        assertTrue(canEstablishNativePrivacyDefaults(context, emptyPreferences()))
        File(root, "logdate").writeText("retained encrypted journal")
        assertFalse(canEstablishNativePrivacyDefaults(context, emptyPreferences()))
    }

    @Test
    fun `partial drafts without a database do not authorize backup defaults`() {
        val preferences = preferencesOf(stringPreferencesKey("editor_drafts") to "retained draft")
        assertFalse(canEstablishNativePrivacyDefaults(context, preferences))
    }

    @Test
    fun `acknowledging native delivery never establishes its missing privacy metadata`() {
        detector.markDeviceInitialized()
        assertFalse(detector.shouldValidatePrivacyMetadata())
        File(root, "logdate").writeText("retained encrypted journal")
        detector.recordRestoreCompleted()
        assertTrue(detector.shouldValidatePrivacyMetadata())
        detector.markDeviceInitialized()
        assertTrue(PostRestoreDetector(context).shouldValidatePrivacyMetadata())
        assertFalse(canEstablishNativePrivacyDefaults(context, emptyPreferences()))
    }

    @Test
    fun `attempted backup and a new guard instance cannot unlock unknown retained content`() =
        runTest {
            File(root, "logdate").writeText("retained encrypted journal")
            detector.recordRestoreCompleted()
            assertFailsWith<NativeRestorePrivacyMetadataRequiredException> {
                NativeRestorePrivacyPolicy.prepareBackup(store) { canEstablishNativePrivacyDefaults(context, it) }
            }
            assertEquals(emptyPreferences(), store.data.value)
            repeat(2) {
                assertFailsWith<NativeRestorePrivacyMetadataRequiredException> {
                    NativeRestorePrivacyGuard(context, store).verifyBeforeContent { true }
                }
            }
        }

    @Test
    fun `restored draft storage requires privacy even before a note exists`() =
        runTest {
            val draft = File(files, "datastore/entry_drafts.preferences_pb")
            draft.parentFile.mkdirs()
            draft.writeText("retained draft")
            assertFailsWith<NativeRestorePrivacyMetadataRequiredException> {
                NativeRestorePrivacyGuard(context, store).verifyBeforeContent { false }
            }
        }

    @Test
    fun `repaired preferences unblock retry while retaining the biometric policy`() =
        runTest {
            val guard = NativeRestorePrivacyGuard(context, store)
            assertFailsWith<NativeRestorePrivacyMetadataRequiredException> { guard.verifyBeforeContent { true } }
            store.data.value =
                preferencesOf(booleanPreferencesKey("is_onboarded") to true, stringPreferencesKey("security_level") to "BIOMETRIC")
            guard.verifyBeforeContent { error("Known privacy must not query protected content") }
            assertEquals("BIOMETRIC", store.data.value[stringPreferencesKey("security_level")])
        }

    @Test
    fun `cancellation cannot establish that protected storage is empty`() =
        runTest {
            assertFailsWith<CancellationException> {
                NativeRestorePrivacyGuard(context, store).verifyBeforeContent { throw CancellationException() }
            }
        }

    @Test
    fun `old unlocked preferences cannot authorize a native delivery that omitted preferences`() =
        runTest {
            store.data.value =
                preferencesOf(booleanPreferencesKey("is_onboarded") to true, stringPreferencesKey("security_level") to "NONE")
            detector.beginNativeRestoreSession()
            assertFailsWith<NativeRestorePrivacyMetadataRequiredException> {
                NativeRestorePrivacyGuard(context, store).verifyBeforeContent { true }
            }
            detector.markNativePrivacyPayloadReceived()
            NativeRestorePrivacyGuard(context, store).verifyBeforeContent { true }
        }

    @Test
    fun `verified empty native delivery may establish a new local privacy policy`() =
        runTest {
            detector.beginNativeRestoreSession()
            NativeRestorePrivacyGuard(context, store).verifyBeforeContent { false }
            assertFalse(detector.hasMissingNativePrivacyPayload())
        }

    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
}
