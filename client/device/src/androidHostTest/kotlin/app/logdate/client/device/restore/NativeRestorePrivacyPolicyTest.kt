package app.logdate.client.device.restore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeRestorePrivacyPolicyTest {
    private val onboarded = booleanPreferencesKey("is_onboarded")
    private val security = stringPreferencesKey("security_level")

    @Test
    fun `retained content without privacy metadata cannot enter onboarding`() =
        runTest {
            assertFailsWith<NativeRestorePrivacyMetadataRequiredException> {
                NativeRestorePrivacyPolicy.verifyBeforeContent(emptyPreferences()) { true }
            }
        }

    @Test
    fun `owner metadata alone does not establish the source privacy policy`() =
        runTest {
            val preferences = preferencesOf(stringPreferencesKey("identity.canonical_owner_id") to "synthetic-owner")
            assertFailsWith<NativeRestorePrivacyMetadataRequiredException> {
                NativeRestorePrivacyPolicy.verifyBeforeContent(preferences) { true }
            }
        }

    @Test
    fun `empty first launch and credentials-only delivery can begin setup`() =
        runTest {
            NativeRestorePrivacyPolicy.verifyBeforeContent(emptyPreferences()) { false }
        }

    @Test
    fun `explicit unfinished onboarding is preserved`() =
        runTest {
            NativeRestorePrivacyPolicy.verifyBeforeContent(preferencesOf(onboarded to false)) {
                error("Known onboarding metadata must not query retained content")
            }
        }

    @Test
    fun `legacy onboarded preferences retain the documented default lock policy`() =
        runTest {
            NativeRestorePrivacyPolicy.verifyBeforeContent(preferencesOf(onboarded to true)) { error("Unexpected content read") }
        }

    @Test
    fun `a restored biometric policy is never replaced by an unlocked default`() =
        runTest {
            val store = MemoryStore(preferencesOf(security to "BIOMETRIC", onboarded to true))
            NativeRestorePrivacyPolicy.prepareBackup(store)
            assertEquals("BIOMETRIC", store.data.value[security])
            assertEquals(true, store.data.value[onboarded])
        }

    @Test
    fun `backup persists explicit setup and privacy defaults for unused installations`() =
        runTest {
            val store = MemoryStore(emptyPreferences())
            NativeRestorePrivacyPolicy.prepareBackup(store) { true }
            assertEquals(false, store.data.value[onboarded])
            assertEquals("NONE", store.data.value[security])
        }

    @Test
    fun `backup cannot manufacture privacy metadata for an unverified retained journal`() =
        runTest {
            val store = MemoryStore(emptyPreferences())
            assertFailsWith<NativeRestorePrivacyMetadataRequiredException> { NativeRestorePrivacyPolicy.prepareBackup(store) }
            assertEquals(emptyPreferences(), store.data.value)
            assertFailsWith<NativeRestorePrivacyMetadataRequiredException> {
                NativeRestorePrivacyPolicy.verifyBeforeContent(store.data.value) { true }
            }
        }

    @Test
    fun `invalid privacy values do not become an unlocked restored journal`() =
        runTest {
            assertFailsWith<NativeRestorePrivacyMetadataRequiredException> {
                NativeRestorePrivacyPolicy.verifyBeforeContent(preferencesOf(onboarded to true, security to "unknown")) { true }
            }
        }

    @Test
    fun `a failed content query does not establish an empty journal`() =
        runTest {
            assertFailsWith<IllegalStateException> {
                NativeRestorePrivacyPolicy.verifyBeforeContent(emptyPreferences()) { error("storage unavailable") }
            }
        }

    private class MemoryStore(
        initial: Preferences,
    ) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
}
