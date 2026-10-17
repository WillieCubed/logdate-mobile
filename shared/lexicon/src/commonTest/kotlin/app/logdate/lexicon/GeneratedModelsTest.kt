package app.logdate.lexicon

import app.logdate.lexicon.app.logdate.DefsLocation
import app.logdate.lexicon.app.logdate.journal.Entry
import app.logdate.lexicon.app.logdate.journal.EntryBlocksItem
import app.logdate.lexicon.app.logdate.journal.EntryTextBlock
import app.logdate.lexicon.community.lexicon.location.Geo
import studio.hypertext.atproto.lexicon.LexiconRecordKey
import studio.hypertext.atproto.lexicon.data.LexiconData
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The generated models are ordinary multiplatform code: they build and run on every target the
 * apps ship on.
 */
class GeneratedModelsTest {
    @Test
    fun `a journal entry round-trips on every platform`() {
        val entry =
            Entry(
                blocks = listOf(EntryBlocksItem.TextBlock(EntryTextBlock(id = "b1", text = "A walk by the river"))),
                createdAt = "2026-10-16T18:00:00.000Z",
                location = DefsLocation(geo = Geo(latitude = "51.5072", longitude = "-0.1276")),
            )

        val json = LexiconData.json.encodeToString(Entry.serializer(), entry)
        val cbor = LexiconData.encodeToDagCbor(Entry.serializer(), entry)

        assertEquals(entry, LexiconData.json.decodeFromString(Entry.serializer(), json))
        assertEquals(entry, LexiconData.decodeFromDagCbor(Entry.serializer(), cbor))
    }

    @Test
    fun `record types expose their collection and key rule`() {
        assertEquals("app.logdate.journal.entry", Entry.TYPE)
        assertEquals(LexiconRecordKey.Any, Entry.KEY)
        assertEquals(Entry.serializer(), LogDateRecords.records.getValue(Entry.TYPE))
    }
}
