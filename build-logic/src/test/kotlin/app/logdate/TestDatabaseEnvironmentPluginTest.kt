package app.logdate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class TestDatabaseEnvironmentPluginTest {
    @Test
    fun `no database is its own value`() {
        assertEquals("none", databaseFingerprint("", "", hasPassword = false))
        assertEquals("none", databaseFingerprint("", "someone", hasPassword = true))
    }

    @Test
    fun `a different url or user changes the fingerprint`() {
        val base = databaseFingerprint("jdbc:postgresql://localhost:5432/a", "test", hasPassword = true)

        assertNotEquals(base, databaseFingerprint("jdbc:postgresql://localhost:5432/b", "test", hasPassword = true))
        assertNotEquals(base, databaseFingerprint("jdbc:postgresql://localhost:5432/a", "other", hasPassword = true))
        assertNotEquals(base, "none")
        assertEquals(base, databaseFingerprint("jdbc:postgresql://localhost:5432/a", "test", hasPassword = true))
    }

    @Test
    fun `the fingerprint never contains the url, the user or any password`() {
        val url = "jdbc:postgresql://localhost:5432/logdate_test"
        val fingerprint = databaseFingerprint(url, "test-user", hasPassword = true)

        assertFalse("logdate_test" in fingerprint)
        assertFalse("test-user" in fingerprint)
        assertFalse("localhost" in fingerprint)
    }
}
