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
    fun journalRoundTrip() {
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
    fun journalWithFavoritedFlagRoundTrips() {
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
    fun journalWithEmptyFieldsRoundTrips() {
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
    fun journalWithUnicodeContentRoundTrips() {
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
    fun syncVersionPreservedThroughRoundTrip() {
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
    fun dataMapContainsRequiredKeys() {
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
    fun fromDataMapThrowsOnMissingPayload() {
        val map = mapOf(JournalDataMapper.KEY_UID to fixedUuid.toString())

        assertFailsWith<IllegalArgumentException> {
            mapper.fromDataMap(map)
        }
    }

    @Test
    fun fromDataMapThrowsOnEmptyMap() {
        assertFailsWith<IllegalArgumentException> {
            mapper.fromDataMap(emptyMap())
        }
    }

    @Test
    fun fromDataMapThrowsOnInvalidJson() {
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
    fun journalPathUsesId() {
        val path = JournalDataMapper.journalPath(fixedUuid)
        assertEquals("/logdate/journals/550e8400-e29b-41d4-a716-446655440000", path)
    }

    @Test
    fun journalDeletePathUsesId() {
        val path = JournalDataMapper.journalDeletePath(fixedUuid)
        assertEquals("/logdate/journals/550e8400-e29b-41d4-a716-446655440000/delete", path)
    }

    @Test
    fun isJournalPathReturnsTrueForJournalDataPaths() {
        assertTrue(JournalDataMapper.isJournalPath("/logdate/journals/550e8400-e29b-41d4-a716-446655440000"))
    }

    @Test
    fun isJournalPathReturnsFalseForDeletePaths() {
        assertFalse(JournalDataMapper.isJournalPath("/logdate/journals/550e8400-e29b-41d4-a716-446655440000/delete"))
    }

    @Test
    fun isJournalPathReturnsFalseForUnrelatedPaths() {
        assertFalse(JournalDataMapper.isJournalPath("/logdate/notes/some-id"))
        assertFalse(JournalDataMapper.isJournalPath("/other/path"))
    }

    @Test
    fun isDeletePathReturnsTrueForDeletePaths() {
        assertTrue(JournalDataMapper.isDeletePath("/logdate/journals/550e8400-e29b-41d4-a716-446655440000/delete"))
    }

    @Test
    fun isDeletePathReturnsFalseForNonDeletePaths() {
        assertFalse(JournalDataMapper.isDeletePath("/logdate/journals/550e8400-e29b-41d4-a716-446655440000"))
    }

    @Test
    fun journalIdFromPathExtractsCorrectUuid() {
        val path = "/logdate/journals/550e8400-e29b-41d4-a716-446655440000"
        val extracted = JournalDataMapper.journalIdFromPath(path)
        assertEquals(fixedUuid, extracted)
    }

    @Test
    fun journalIdFromDeletePathExtractsCorrectUuid() {
        val path = "/logdate/journals/550e8400-e29b-41d4-a716-446655440000/delete"
        val extracted = JournalDataMapper.journalIdFromPath(path)
        assertEquals(fixedUuid, extracted)
    }

    // =======================================================================
    // Batch serialization
    // =======================================================================

    @Test
    fun multipleJournalsSerializeIndependently() {
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
