package app.logdate.client.datastore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeRestorePolicyTest {
    @Test
    fun `native authorization travels as non-secret preferences and preserves server scope`() =
        runTest {
            val preferences = mutableMapOf<String, String>()
            val policy = NativeRestorePolicy({ preferences[it] }, { key, value -> preferences[key] = value })
            val origin = "https://cloud.example"
            assertTrue(policy.canRestore(origin))
            assertNull(policy.decision(origin))
            policy.setAllowed(origin, false)
            val first = policy.decision(origin)
            val copy = preferences.toMap()
            val transferred = NativeRestorePolicy({ copy[it] }, { _, _ -> error("read only") })
            assertFalse(transferred.canRestore("$origin/"))
            assertTrue(transferred.canRestore("https://other.example"))
            policy.setAllowed(origin, false)
            assertNotEquals(first, policy.decision(origin), "Repeated sign-out invalidates an older explicit authentication")
            policy.setAllowed(origin, true)
            assertTrue(policy.canRestore(origin))
        }

    @Test
    fun `malformed authorization never permits automatic restore`() {
        for (value in listOf("allowed", "allowed:broken", "disabled:broken", "", "true")) {
            assertFalse(NativeRestorePolicy.allows(value))
        }
    }
}
