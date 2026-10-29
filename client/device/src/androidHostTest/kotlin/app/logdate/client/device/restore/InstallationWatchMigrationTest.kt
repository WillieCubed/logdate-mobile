package app.logdate.client.device.restore

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstallationWatchMigrationTest {
    private val keys = mutableMapOf<String, ByteArray>()
    private val linkKey = stringPreferencesKey("phone.watch_links.v1")
    private val revisionKey = stringPreferencesKey("phone.watch_window.published.v1.$peer.revision")

    @Test
    fun `an upgrade retains local trust and counters but never copies user preferences`() =
        runTest {
            seed()
            val incoming = legacy()
            val migration = InstallationWatchMigration({ incoming }, { keys[it] })
            val local = migration.migrate(emptyPreferences())
            assertEquals(incoming[linkKey], local[linkKey])
            assertEquals("3", local[revisionKey])
            assertNull(local[stringPreferencesKey("journal.setting")])
            assertFalse(migration.shouldMigrate(local))
        }

    @Test
    fun `a replacement cannot migrate copied trust or counters without the excluded key`() =
        runTest {
            seed()
            val incoming = legacy()
            keys.remove("watch")
            val migration = InstallationWatchMigration({ incoming }, { keys[it] })
            val local = migration.migrate(emptyPreferences())
            assertEquals("{}", local[linkKey])
            assertNull(local[revisionKey])
            assertFalse(migration.shouldMigrate(local))
        }

    @Test
    fun `native restore history excludes copied trust and counters even with a matching key`() =
        runTest {
            seed()
            val migration = InstallationWatchMigration({ legacy() }, { keys[it] }, canAdoptLegacy = { false })
            val migrated = migration.migrate(emptyPreferences())
            assertEquals("{}", migrated[linkKey])
            assertNull(migrated[revisionKey])
            assertNull(migrated[stringPreferencesKey("journal.setting")])
            assertFalse(migration.shouldMigrate(migrated))
        }

    @Test
    fun `replay cannot remigrate portable metadata over a destination exchange`() =
        runTest {
            seed()
            val migration = InstallationWatchMigration({ legacy() }, { keys[it] })
            val local = migration.migrate(emptyPreferences()).toMutablePreferences()
            local[linkKey] = "destination metadata"
            local[revisionKey] = "7"
            keys["watch"] = ByteArray(32) { 2 }
            assertFalse(migration.shouldMigrate(local))
            assertEquals("destination metadata", local[linkKey])
            assertEquals("7", local[revisionKey])
            assertEquals(local, migration.migrate(local))
        }

    @Test
    fun `missing and damaged local keys never authorize counter migration`() =
        runTest {
            seed()
            keys["watch"] = ByteArray(31)
            val local = InstallationWatchMigration({ legacy() }, { keys[it] }).migrate(emptyPreferences())
            assertEquals("{}", local[linkKey])
            assertNull(local[revisionKey])
        }

    @Test
    fun `damaged optional entries do not block sound trust or journal restoration`() =
        runTest {
            seed()
            val incoming =
                legacy().apply {
                    this[linkKey] = this[linkKey]!!.dropLast(1) + ",\"damaged\":[]}"
                    this[stringPreferencesKey("phone.watch_window.published.v1.{}.revision")] = "4"
                    this[stringPreferencesKey("phone.watch_window.published.v1.{damaged}.revision")] = "4"
                }
            val migrated = InstallationWatchMigration({ incoming }, { keys[it] }).migrate(emptyPreferences())
            assertEquals(legacy()[linkKey], migrated[linkKey])
            assertEquals("3", migrated[revisionKey])
            assertEquals(3, migrated.asMap().size)
        }

    @Test
    fun `a failed secure key read leaves migration retryable`() =
        runTest {
            seed()
            var obstructed = true
            val migration =
                InstallationWatchMigration({ legacy() }, {
                    if (obstructed) throw IOException("Secure storage not available")
                    keys[it]
                })
            val local = emptyPreferences()
            assertFailsWith<IOException> { migration.migrate(local) }
            assertTrue(migration.shouldMigrate(local))
            obstructed = false
            assertFalse(migration.shouldMigrate(migration.migrate(local)))
        }

    @Test
    fun `untrusted and mismatched exchanges cannot bring counters into the installation`() =
        runTest {
            seed()
            val incoming =
                legacy().apply {
                    this[stringPreferencesKey(revisionKey.name.replace("watchNode\":\"watch", "watchNode\":\"another"))] = "19"
                }
            val migrated = InstallationWatchMigration({ incoming }, { keys[it] }).migrate(emptyPreferences())
            assertEquals(3, migrated.asMap().size)
            assertEquals("3", migrated[revisionKey])
        }

    @Test
    fun `unavailable restore history never commits a completed migration`() =
        runTest {
            seed()
            var available = false
            val migration =
                InstallationWatchMigration({ legacy() }, { keys[it] }, canAdoptLegacy = {
                    if (!available) throw IOException("Restore history unavailable")
                    false
                })
            val local = emptyPreferences()
            assertFailsWith<IOException> { migration.migrate(local) }
            assertTrue(migration.shouldMigrate(local))
            available = true
            val migrated = migration.migrate(local)
            assertEquals("{}", migrated[linkKey])
            assertFalse(migration.shouldMigrate(migrated))
            available = false
            assertEquals(migrated, migration.migrate(migrated))
        }

    private fun seed() {
        keys["watch"] = ByteArray(32) { 1 }
    }

    private fun legacy() =
        emptyPreferences().toMutablePreferences().apply {
            this[linkKey] =
                """{"watch":{"nodeId":"watch","displayName":"Watch","mode":"PAIR","linkId":"$LINK_ID",""" +
                """"linkedAt":100,"confirmedAt":101,"sessionId":"legacy-session"}}"""
            this[revisionKey] = "3"
            this[stringPreferencesKey("journal.setting")] = "portable"
        }

    companion object {
        private const val LINK_ID = "CQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQk="
        private val peer = """{"linkId":"$LINK_ID","phoneNode":"phone","watchNode":"watch","mode":"PAIR"}"""
    }
}
