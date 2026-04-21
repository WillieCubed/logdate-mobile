package app.logdate.client.data.people

import app.logdate.shared.model.PersonOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests the data normalization and merging logic used when importing contacts into the people system.
 *
 * This suite ensures that external contact data is sanitized and reconciled correctly by:
 * - Trimming and deduplicating person aliases in a case-insensitive manner
 * - Removing redundant aliases that match the primary display name
 * - Cleaning optional text fields by collapsing blank strings to nulls
 * - Resolving conflicts between existing and imported contact origins based on source "strength"
 */
class PeopleImportNormalizationTest {
    @Test
    fun `normalizePersonAliases trims blanks and removes case-insensitive duplicates`() {
        val aliases =
            normalizePersonAliases(
                aliases = listOf("  Alex  ", "", "alex", "Lex", "  LEX  "),
                primaryName = "Alexa",
            )

        assertEquals(listOf("Alex", "Lex"), aliases)
    }

    @Test
    fun `normalizePersonAliases removes aliases that match the primary name`() {
        val aliases =
            normalizePersonAliases(
                aliases = listOf("Sam", " Sammy ", "sam"),
                primaryName = "  sam ",
            )

        assertEquals(listOf("Sammy"), aliases)
    }

    @Test
    fun `normalizeOptionalText trims nonblank values and clears blanks`() {
        assertEquals("photo://uri", normalizeOptionalText("  photo://uri  "))
        assertNull(normalizeOptionalText("   "))
        assertNull(normalizeOptionalText(null))
    }

    @Test
    fun `mergeImportedPersonOrigin keeps the strongest contact source`() {
        assertEquals(
            PersonOrigin.CONTACT_FULL,
            mergeImportedPersonOrigin(
                existingOriginName = PersonOrigin.CONTACT_FULL.name,
                importedOrigin = PersonOrigin.CONTACT_SELECTED,
            ),
        )
        assertEquals(
            PersonOrigin.CONTACT_SELECTED,
            mergeImportedPersonOrigin(
                existingOriginName = PersonOrigin.MANUAL.name,
                importedOrigin = PersonOrigin.CONTACT_SELECTED,
            ),
        )
        assertEquals(
            PersonOrigin.CONTACT_FULL,
            mergeImportedPersonOrigin(
                existingOriginName = "UNKNOWN",
                importedOrigin = PersonOrigin.CONTACT_FULL,
            ),
        )
    }
}
