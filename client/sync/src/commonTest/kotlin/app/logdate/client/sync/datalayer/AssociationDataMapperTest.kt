package app.logdate.client.sync.datalayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unit tests for [AssociationDataMapper].
 *
 * Validates the mapping of associations between journals and content for sync
 * over the Wear OS Data Layer, including composite key generation and deletion
 * path handling.
 */
class AssociationDataMapperTest {
    private val mapper = AssociationDataMapper()

    private val journalId = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")
    private val contentId = Uuid.parse("660e8400-e29b-41d4-a716-446655440000")

    // =======================================================================
    // Round-trip serialization
    // =======================================================================

    @Test
    fun associationRoundTrip() {
        val map = mapper.toDataMap(journalId, contentId)
        val (restoredJournal, restoredContent) = mapper.fromDataMap(map)

        assertEquals(journalId, restoredJournal)
        assertEquals(contentId, restoredContent)
    }

    @Test
    fun dataMapContainsRequiredKeys() {
        val map = mapper.toDataMap(journalId, contentId)

        assertEquals(journalId.toString(), map[AssociationDataMapper.KEY_JOURNAL_ID])
        assertEquals(contentId.toString(), map[AssociationDataMapper.KEY_CONTENT_ID])
    }

    // =======================================================================
    // Error handling
    // =======================================================================

    @Test
    fun fromDataMapThrowsOnMissingJournalId() {
        val map = mapOf(AssociationDataMapper.KEY_CONTENT_ID to contentId.toString())

        assertFailsWith<IllegalArgumentException> {
            mapper.fromDataMap(map)
        }
    }

    @Test
    fun fromDataMapThrowsOnMissingContentId() {
        val map = mapOf(AssociationDataMapper.KEY_JOURNAL_ID to journalId.toString())

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

    // =======================================================================
    // Path generation and parsing
    // =======================================================================

    @Test
    fun associationPathUsesCompositKey() {
        val path = AssociationDataMapper.associationPath(journalId, contentId)
        assertEquals(
            "/logdate/associations/550e8400-e29b-41d4-a716-446655440000::660e8400-e29b-41d4-a716-446655440000",
            path,
        )
    }

    @Test
    fun associationDeletePathUsesCompositeKey() {
        val path = AssociationDataMapper.associationDeletePath(journalId, contentId)
        assertEquals(
            "/logdate/associations/550e8400-e29b-41d4-a716-446655440000::660e8400-e29b-41d4-a716-446655440000/delete",
            path,
        )
    }

    @Test
    fun isAssociationPathReturnsTrueForDataPaths() {
        assertTrue(
            AssociationDataMapper.isAssociationPath(
                "/logdate/associations/550e8400-e29b-41d4-a716-446655440000::660e8400-e29b-41d4-a716-446655440000",
            ),
        )
    }

    @Test
    fun isAssociationPathReturnsFalseForDeletePaths() {
        assertFalse(
            AssociationDataMapper.isAssociationPath(
                "/logdate/associations/550e8400-e29b-41d4-a716-446655440000::660e8400-e29b-41d4-a716-446655440000/delete",
            ),
        )
    }

    @Test
    fun isAssociationPathReturnsFalseForUnrelatedPaths() {
        assertFalse(AssociationDataMapper.isAssociationPath("/logdate/notes/some-id"))
        assertFalse(AssociationDataMapper.isAssociationPath("/logdate/journals/some-id"))
    }

    @Test
    fun isDeletePathReturnsTrueForDeletePaths() {
        assertTrue(
            AssociationDataMapper.isDeletePath(
                "/logdate/associations/550e8400-e29b-41d4-a716-446655440000::660e8400-e29b-41d4-a716-446655440000/delete",
            ),
        )
    }

    @Test
    fun idsFromPathExtractsCorrectUuids() {
        val path =
            "/logdate/associations/550e8400-e29b-41d4-a716-446655440000::660e8400-e29b-41d4-a716-446655440000"
        val (extractedJournal, extractedContent) = AssociationDataMapper.idsFromPath(path)

        assertEquals(journalId, extractedJournal)
        assertEquals(contentId, extractedContent)
    }

    @Test
    fun idsFromDeletePathExtractsCorrectUuids() {
        val path =
            "/logdate/associations/550e8400-e29b-41d4-a716-446655440000::660e8400-e29b-41d4-a716-446655440000/delete"
        val (extractedJournal, extractedContent) = AssociationDataMapper.idsFromPath(path)

        assertEquals(journalId, extractedJournal)
        assertEquals(contentId, extractedContent)
    }

    // =======================================================================
    // Batch serialization
    // =======================================================================

    @Test
    fun multipleAssociationsSerializeIndependently() {
        val pairs =
            listOf(
                Uuid.random() to Uuid.random(),
                Uuid.random() to Uuid.random(),
                Uuid.random() to Uuid.random(),
            )

        val maps = pairs.map { (j, c) -> mapper.toDataMap(j, c) }
        val restored = maps.map { mapper.fromDataMap(it) }

        assertEquals(pairs.size, restored.size)
        for (i in pairs.indices) {
            assertEquals(pairs[i].first, restored[i].first)
            assertEquals(pairs[i].second, restored[i].second)
        }
    }
}
