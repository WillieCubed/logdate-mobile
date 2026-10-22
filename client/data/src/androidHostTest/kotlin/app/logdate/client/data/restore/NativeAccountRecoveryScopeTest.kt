package app.logdate.client.data.restore

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.datastore.NativeRestorePolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NativeAccountRecoveryScopeTest {
    private val delivery = "2f18404e-e9f5-434d-8b03-2f04f09ac523"
    private val generation = "19b6ef01-19ec-4b65-8c82-c49b1ea25862"

    @Test
    fun `unrelated settings edits do not strand a retained account recovery`() =
        runTest {
            val preferences = mutablePreferencesOf()
            val original = assertNotNull(nativeAccountRecoveryRequest(delivery, generation, preferences))
            preferences[stringPreferencesKey("theme")] = "dark"
            preferences[stringPreferencesKey("device.id")] = "fresh-installation"
            assertEquals(original, nativeAccountRecoveryRequest(delivery, generation, preferences))
        }

    @Test
    fun `server owner and authorization changes invalidate the original attempt`() =
        runTest {
            val preferences = mutablePreferencesOf()
            val original = assertNotNull(nativeAccountRecoveryRequest(delivery, generation, preferences))
            preferences[stringPreferencesKey("identity.canonical_owner_id")] = "347c6231-19f4-49fa-bd1d-58ed34b9be03"
            preferences[stringPreferencesKey("identity.canonical_owner_bound")] = "true"
            assertNotEquals(original, nativeAccountRecoveryRequest(delivery, generation, preferences))
            preferences.clear()
            preferences[stringPreferencesKey("backend_url")] = "http://unsafe.example"
            assertNull(nativeAccountRecoveryRequest(delivery, generation, preferences))
        }

    @Test
    fun `disable and reenable cannot revive a retained authorization`() =
        runTest {
            val preferences = mutablePreferencesOf()
            val policy =
                NativeRestorePolicy(
                    { preferences[stringPreferencesKey(it)] },
                    { key, value -> preferences[stringPreferencesKey(key)] = value },
                )
            // Use the same origin selected by a fresh installation's validated configuration.
            val configuredOrigin = assertNotNull(nativeRestoreConfiguration(preferences)).getCurrentBackendUrl()
            policy.setAllowed(configuredOrigin, true)
            val original = assertNotNull(nativeAccountRecoveryRequest(delivery, generation, preferences))
            policy.setAllowed(configuredOrigin, false)
            assertNull(nativeAccountRecoveryRequest(delivery, generation, preferences))
            policy.setAllowed(configuredOrigin, true)
            assertNotEquals(original, nativeAccountRecoveryRequest(delivery, generation, preferences))
        }
}
