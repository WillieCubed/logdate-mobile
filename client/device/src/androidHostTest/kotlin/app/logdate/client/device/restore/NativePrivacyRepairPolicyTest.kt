package app.logdate.client.device.restore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativePrivacyRepairPolicyTest {
    private val owner = stringPreferencesKey("identity.canonical_owner_id")
    private val security = stringPreferencesKey("security_level")
    private val onboarded = booleanPreferencesKey("is_onboarded")
    private val device = stringPreferencesKey("device.id")
    private val draft = stringPreferencesKey("draft.v1")

    @Test
    fun `same source restores only its known policy while preserving installation preferences`() {
        val before = mutablePreferencesOf(owner to "owner", device to "destination", draft to "draft")
        val incoming =
            mutablePreferencesOf(
                owner to "owner",
                device to "source",
                draft to "draft",
                security to "BIOMETRIC",
                onboarded to true,
            )
        val result = assertNotNull(nativePrivacyRepairPolicy(before, incoming))
        assertEquals("BIOMETRIC", result[security])
        assertEquals(true, result[onboarded])
        assertEquals("destination", result[device])
        assertEquals("draft", result[draft])
        assertNull(before[security])
    }

    @Test
    fun `a known local policy cannot be replaced even by an otherwise matching source`() {
        val before = mutablePreferencesOf(owner to "owner", security to "BIOMETRIC", onboarded to true)
        val incoming = mutablePreferencesOf(owner to "owner", security to "NONE", onboarded to true)
        assertNull(nativePrivacyRepairPolicy(before, incoming))
    }

    @Test
    fun `typed flags without their native receipt are not an accepted local policy`() {
        val before = mutablePreferencesOf(owner to "owner", security to "NONE")
        val incoming = mutablePreferencesOf(owner to "owner", security to "BIOMETRIC")
        assertNull(nativePrivacyRepairPolicy(before, incoming, currentPolicyReceived = true))
        assertEquals("BIOMETRIC", assertNotNull(nativePrivacyRepairPolicy(before, incoming, currentPolicyReceived = false))[security])
    }

    @Test
    fun `different owner draft or backend rejects a policy-only repair`() {
        val before = mutablePreferencesOf(owner to "owner", draft to "draft")
        val source = mutablePreferencesOf(owner to "owner", draft to "draft", security to "BIOMETRIC")
        for (changed in listOf(
            source.toMutablePreferences().apply { this[owner] = "other" },
            source.toMutablePreferences().apply { this[draft] = "new draft" },
            source.toMutablePreferences().apply { this[stringPreferencesKey("backend_url")] = "https://other.example" },
        )) {
            assertNull(nativePrivacyRepairPolicy(before, changed))
        }
    }

    @Test
    fun `unknown and mistyped incoming policies remain unavailable`() {
        val before = mutablePreferencesOf(owner to "owner")
        for (policy in listOf(
            mutablePreferencesOf(owner to "owner"),
            mutablePreferencesOf(owner to "owner", security to "corrupt"),
            mutablePreferencesOf(owner to "owner", stringPreferencesKey("is_onboarded") to "true", security to "BIOMETRIC"),
        )) {
            assertNull(nativePrivacyRepairPolicy(before, policy))
        }
    }

    @Test
    fun `only existing credential migration and installation id normalization are ignored`() {
        val before = mutablePreferencesOf(owner to "owner", device to "destination")
        val incoming =
            mutablePreferencesOf(
                owner to "owner",
                device to "source",
                security to "BIOMETRIC",
                stringPreferencesKey("cloud_access_token") to "synthetic",
            )
        val repaired = assertNotNull(nativePrivacyRepairPolicy(before, incoming))
        assertNull(repaired[stringPreferencesKey("cloud_access_token")])
        val unrelated = incoming.toMutablePreferences().apply { this[stringPreferencesKey("identity.extra")] = "different" }
        assertNull(nativePrivacyRepairPolicy(before, unrelated))
    }

    @Test
    fun `byte preference equality compares contents and rejects a changed payload`() {
        val key = byteArrayPreferencesKey("custom_bytes")
        val before = mutablePreferencesOf(owner to "owner", key to byteArrayOf(1, 2))
        val incoming = mutablePreferencesOf(owner to "owner", key to byteArrayOf(1, 2), security to "BIOMETRIC")
        assertNotNull(nativePrivacyRepairPolicy(before, incoming))
        assertNull(nativePrivacyRepairPolicy(before, incoming.toMutablePreferences().apply { this[key] = byteArrayOf(1, 3) }))
    }

    @Test
    fun `accepted legacy policy combinations are not replaced with invented defaults`() {
        for (source in listOf(
            mutablePreferencesOf(owner to "owner", onboarded to false),
            mutablePreferencesOf(owner to "owner", security to "BIOMETRIC"),
        )) {
            val repaired = assertNotNull(nativePrivacyRepairPolicy(mutablePreferencesOf(owner to "owner"), source))
            assertEquals(source[security], repaired[security])
            assertEquals(source[onboarded], repaired[onboarded])
        }
    }

    @Test
    fun `resume fingerprint compares typed preference values with stable order`() {
        val set = stringSetPreferencesKey("set")
        val first = mutablePreferencesOf(owner to "owner", set to linkedSetOf("a", "b"))
        val reordered = mutablePreferencesOf(set to linkedSetOf("b", "a"), owner to "owner")
        assertEquals(nativePrivacyPreferenceFingerprint(first), nativePrivacyPreferenceFingerprint(reordered))
        assertTrue(nativePrivacyPreferenceFingerprint(first).matches(Regex("[a-f0-9]{64}")))
        assertNotEquals(
            nativePrivacyPreferenceFingerprint(mutablePreferencesOf(stringPreferencesKey("value") to "1")),
            nativePrivacyPreferenceFingerprint(mutablePreferencesOf(intPreferencesKey("value") to 1)),
        )
    }

    @Test
    fun `resume fingerprint includes installation and privacy fields as well as raw bytes`() {
        val bytes = byteArrayPreferencesKey("bytes")
        val first = mutablePreferencesOf(owner to "owner", device to "destination", bytes to byteArrayOf(1, 2))
        assertEquals(nativePrivacyPreferenceFingerprint(first), nativePrivacyPreferenceFingerprint(first.toMutablePreferences()))
        for (changed in listOf(
            first.toMutablePreferences().apply { this[device] = "other" },
            first.toMutablePreferences().apply { this[security] = "BIOMETRIC" },
            first.toMutablePreferences().apply { this[bytes] = byteArrayOf(1, 3) },
        )) {
            assertNotEquals(nativePrivacyPreferenceFingerprint(first), nativePrivacyPreferenceFingerprint(changed))
        }
    }
}
