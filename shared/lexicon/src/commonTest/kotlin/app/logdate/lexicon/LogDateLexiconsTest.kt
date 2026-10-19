package app.logdate.lexicon

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.syntax.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The schemas built into the app check records on every platform, with no schema files to read. */
class LogDateLexiconsTest {
    @Test
    fun `every record type the models know has its schema built in`() {
        LogDateRecords.records.keys.forEach { collection ->
            assertNotNull(LogDateLexicons.registry.document(Nsid.require(collection)), collection)
        }
    }

    @Test
    fun `a valid journal passes and one without its title is reported`() {
        val journal = Nsid.require("app.logdate.journal.journal")
        val valid =
            buildJsonObject {
                put("\$type", journal.toString())
                put("title", "Weekend walks")
                put("createdAt", "2026-10-16T18:00:00.000Z")
            }
        val missingTitle =
            buildJsonObject {
                put("\$type", journal.toString())
                put("createdAt", "2026-10-16T18:00:00.000Z")
            }

        assertEquals(emptyList(), LogDateLexicons.validator.validateRecord(journal, valid).issues)
        val issues = LogDateLexicons.validator.validateRecord(journal, missingTitle).issues
        assertTrue(issues.any { it.path == "title" }, issues.toString())
    }
}
