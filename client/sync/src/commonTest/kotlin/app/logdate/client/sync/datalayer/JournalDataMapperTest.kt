package app.logdate.client.sync.datalayer

import app.logdate.shared.model.Journal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Unit tests for [JournalDataMapper].
 *
 * Verifies the serialization of journal metadata for Wear OS Data Layer sync,
 * including support for favorited status, Unicode content, and sync versions.
 */
class JournalDataMapperTest {
    private val mapper = JournalDataMapper()

    private val fixedTime = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val fixedUuid = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")

    // =======================================================================
    // Round-trip serialization
    // =======================================================================

    @Test
    fun `journal round trip`() {
        val journal =
            Journal(
                id = fixedUuid,
                title = "My Journal",
                description = "A test journal",
                isFavorited = false,
                created = fixedTime,
                lastUpdated = fixedTime,
                syncVersion = 1,
            )

        val map = mapper.toDataMap(journal)
        val restored = mapper.fromDataMap(map)

        assertEquals(journal, restored)
    }

    @Test
    fun `journal with favorited flag round trips`() {
        val journal =
            Journal(
                id = fixedUuid,
                title = "Favorites",
                description = "",
                isFavorited = true,
                created = fixedTime,
                lastUpdated = fixedTime,
                syncVersion = 5,
            )

        val map = mapper.toDataMap(journal)
        val restored = mapper.fromDataMap(map)

        assertEquals(journal, restored)
        assertTrue(restored.isFavorited)
    }

    @Test
    fun `journal with empty fields round trips`() {
        val journal =
            Journal(
                id = fixedUuid,
                title = "",
                description = "",
                created = fixedTime,
                lastUpdated = fixedTime,
            )

        val map = mapper.toDataMap(journal)
        val restored = mapper.fromDataMap(map)

        assertEquals("", restored.title)
        assertEquals("", restored.description)
    }

    @Test
    fun `journal with unicode content round trips`() {
        val journal =
            Journal(
                id = fixedUuid,
                title = "日記 \uD83D\uDCD3",
                description = "My journal with émojis \u2764\uFE0F",
                created = fixedTime,
                lastUpdated = fixedTime,
            )

        val map = mapper.toDataMap(journal)
        val restored = mapper.fromDataMap(map)

        assertEquals(journal.title, restored.title)
        assertEquals(journal.description, restored.description)
    }

    @Test
    fun `sync version preserved through round trip`() {
        val journal =
            Journal(
                id = fixedUuid,
                title = "Test",
                created = fixedTime,
                lastUpdated = fixedTime,
                syncVersion = 42,
            )

        val restored = mapper.fromDataMap(mapper.toDataMap(journal))

        assertEquals(42, restored.syncVersion)
    }

    // =======================================================================
    // Data map key validation
    // =======================================================================

    @Test
    fun `data map contains required keys`() {
        val journal =
            Journal(
                id = fixedUuid,
                title = "Test",
                created = fixedTime,
                lastUpdated = fixedTime,
            )

        val map = mapper.toDataMap(journal)

        assertNotNull(map[JournalDataMapper.KEY_UID])
        assertNotNull(map[JournalDataMapper.KEY_JSON_PAYLOAD])
        assertEquals(fixedUuid.toString(), map[JournalDataMapper.KEY_UID])
    }

    // =======================================================================
    // Error handling
    // =======================================================================

    @Test
    fun `from data map throws on missing payload`() {
        val map = mapOf(JournalDataMapper.KEY_UID to fixedUuid.toString())

        assertFailsWith<IllegalArgumentException> {
            mapper.fromDataMap(map)
        }
    }

    @Test
    fun `from data map throws on empty map`() {
        assertFailsWith<IllegalArgumentException> {
            mapper.fromDataMap(emptyMap())
        }
    }

    @Test
    fun `from data map throws on invalid json`() {
        val map =
            mapOf(
                JournalDataMapper.KEY_UID to fixedUuid.toString(),
                JournalDataMapper.KEY_JSON_PAYLOAD to "not valid json",
            )

        assertFailsWith<Exception> {
            mapper.fromDataMap(map)
        }
    }

    // =======================================================================
    // Path generation and parsing
    // =======================================================================

    @Test
    fun `journal path uses id`() {
        val path = JournalDataMapper.journalPath(fixedUuid)
        assertEquals("/logdate/journals/550e8400-e29b-41d4-a716-446655440000", path)
    }

    @Test
    fun `journal delete path uses id`() {
        val path = JournalDataMapper.journalDeletePath(fixedUuid)
        assertEquals("/logdate/journals/550e8400-e29b-41d4-a716-446655440000/delete", path)
    }

    @Test
    fun `is journal path returns true for journal data paths`() {
        assertTrue(JournalDataMapper.isJournalPath("/logdate/journals/550e8400-e29b-41d4-a716-446655440000"))
    }

    @Test
    fun `is journal path returns false for delete paths`() {
        assertFalse(JournalDataMapper.isJournalPath("/logdate/journals/550e8400-e29b-41d4-a716-446655440000/delete"))
    }

    @Test
    fun `is journal path returns false for unrelated paths`() {
        assertFalse(JournalDataMapper.isJournalPath("/logdate/notes/some-id"))
        assertFalse(JournalDataMapper.isJournalPath("/other/path"))
    }

    @Test
    fun `is delete path returns true for delete paths`() {
        assertTrue(JournalDataMapper.isDeletePath("/logdate/journals/550e8400-e29b-41d4-a716-446655440000/delete"))
    }

    @Test
    fun `is delete path returns false for non delete paths`() {
        assertFalse(JournalDataMapper.isDeletePath("/logdate/journals/550e8400-e29b-41d4-a716-446655440000"))
    }

    @Test
    fun `journal id from path extracts correct uuid`() {
        val path = "/logdate/journals/550e8400-e29b-41d4-a716-446655440000"
        val extracted = JournalDataMapper.journalIdFromPath(path)
        assertEquals(fixedUuid, extracted)
    }

    @Test
    fun `journal id from delete path extracts correct uuid`() {
        val path = "/logdate/journals/550e8400-e29b-41d4-a716-446655440000/delete"
        val extracted = JournalDataMapper.journalIdFromPath(path)
        assertEquals(fixedUuid, extracted)
    }

    // =======================================================================
    // Batch serialization
    // =======================================================================

    @Test
    fun `multiple journals serialize independently`() {
        val journals =
            listOf(
                Journal(id = Uuid.random(), title = "Journal 1", created = fixedTime, lastUpdated = fixedTime),
                Journal(id = Uuid.random(), title = "Journal 2", created = fixedTime, lastUpdated = fixedTime),
                Journal(id = Uuid.random(), title = "Journal 3", created = fixedTime, lastUpdated = fixedTime),
            )

        val maps = journals.map { mapper.toDataMap(it) }
        val restored = maps.map { mapper.fromDataMap(it) }

        assertEquals(journals.size, restored.size)
        for (i in journals.indices) {
            assertEquals(journals[i], restored[i])
        }
    }
}
