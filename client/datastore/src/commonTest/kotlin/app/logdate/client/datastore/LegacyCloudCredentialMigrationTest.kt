package app.logdate.client.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegacyCloudCredentialMigrationTest {
    @Test
    fun `all historic credential families are purged without losing account metadata`() =
        runTest {
            val keys =
                listOf(
                    "cloud_access_token",
                    "cloud_refresh_token",
                    "cloud_access_token_first",
                    "cloud_refresh_token_second",
                    "access_token",
                    "refresh_token",
                    "access_token_first",
                    "refresh_token_second",
                )
            val preferences = mutablePreferencesOf(stringPreferencesKey("cloud_account_did_first") to "did:web:synthetic.invalid")
            keys.forEach { preferences[stringPreferencesKey(it)] = "synthetic-token" }
            val migration = LegacyCloudCredentialMigration()
            assertTrue(migration.shouldMigrate(preferences))
            val clean = migration.migrate(preferences)
            keys.forEach { assertNull(clean[stringPreferencesKey(it)], it) }
            assertEquals("did:web:synthetic.invalid", clean[stringPreferencesKey("cloud_account_did_first")])
            assertFalse(migration.shouldMigrate(clean))
            assertEquals(clean, migration.migrate(clean))
        }
}
