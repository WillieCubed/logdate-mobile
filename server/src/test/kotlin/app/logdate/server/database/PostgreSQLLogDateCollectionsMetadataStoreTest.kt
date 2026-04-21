package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import app.logdate.server.logdate.LogDateCollectionKind
import kotlinx.coroutines.runBlocking
import studio.hypertext.atproto.identity.AtprotoDid
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [PostgreSQLLogDateCollectionsMetadataStore], the component
 * responsible for tracking the synchronization state of user collections.
 *
 * This suite verifies the metadata tracking required for efficient
 * synchronization of AT Protocol records:
 * - Accurate version tracking for individual records within a collection (Entries,
 *   Journals, etc.).
 * - Correct generation of delta "change sets" for incremental syncing, including
 *   pagination and tombstone (deletion) support.
 * - Aggregation of collection status metrics (e.g., total record counts).
 * - Maintenance operations such as the purging of old tombstones to manage
 *   database growth.
 */
class PostgreSQLLogDateCollectionsMetadataStoreTest {
    @Test
    fun `metadata store tracks versions changes and purge counts by collection`() {
        withH2Database(
            LogDateCollectionStatesTable,
            LogDateCollectionRecordsTable,
        ) {
            val store = PostgreSQLLogDateCollectionsMetadataStore()
            val userId = UUID.randomUUID()
            val repoDid = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")

            runBlocking {
                val firstEntry = store.upsert(userId, repoDid, LogDateCollectionKind.ENTRY, "entry-1")
                val firstJournal = store.upsert(userId, repoDid, LogDateCollectionKind.JOURNAL, "journal-1")
                val secondEntry = store.upsert(userId, repoDid, LogDateCollectionKind.ENTRY, "entry-2")
                store.upsert(userId, repoDid, LogDateCollectionKind.ENTRY, "entry-3")
                val status = store.status(userId)
                val changes = store.changes(userId, LogDateCollectionKind.ENTRY, since = 0L, limit = 2)

                assertEquals(repoDid, store.state(userId)?.repoDid)
                assertTrue(firstJournal.version > firstEntry.version)
                assertTrue(secondEntry.version > firstJournal.version)
                assertEquals(3, status.entryCount)
                assertEquals(1, status.journalCount)
                assertEquals(0, status.associationCount)
                assertEquals(2, changes.changes.size)
                assertTrue(changes.hasMore)

                val deletedAt = secondEntry.version + 10L
                val deleted = store.delete(userId, repoDid, LogDateCollectionKind.ENTRY, "entry-1", deletedAt)
                val liveEntry = store.metadata(userId, LogDateCollectionKind.ENTRY, "entry-1")
                val deletionChanges = store.changes(userId, LogDateCollectionKind.ENTRY, since = firstEntry.version, limit = 10)
                val purged = store.purgeTombstones(userId, olderThan = deletedAt + 1L)

                assertNotNull(deleted)
                assertNull(liveEntry)
                assertTrue(deletionChanges.deletions.any { it.recordKey == "entry-1" && it.deletedAt == deletedAt })
                assertEquals(1, purged.entryPurged)
                assertEquals(0, purged.journalPurged)
            }
        }
    }
}
