package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.client.sync.space.SpaceRecordMappingException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class NoteEntryMapperTest : NoteEntryMapperFixture() {
    @Test
    fun `every field of every kind of note round-trips`() {
        fullNotes.forEach { note -> assertEquals(note, roundTrip(note)) }
    }

    @Test
    fun `notes with only their required fields round-trip`() {
        val bare =
            listOf(
                JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = ""),
                JournalNote.Image(uid = imageId, creationTimestamp = created, lastUpdated = created, mediaRef = photoRef),
                JournalNote.Video(uid = videoId, creationTimestamp = created, lastUpdated = created, mediaRef = clipRef),
                JournalNote.Audio(uid = audioId, creationTimestamp = created, lastUpdated = created, mediaRef = voiceRef),
            )

        bare.forEach { note -> assertEquals(note, roundTrip(note)) }
    }

    @Test
    fun `one note is one entry with one block and both are keyed by the note's id`() {
        fullNotes.forEach { note ->
            val record = mapper.toRecord(note)
            val blocks = record.getValue("blocks").jsonArray

            assertEquals(note.uid.toString(), mapper.rkeyOf(note))
            assertEquals("app.logdate.journal.entry", record["\$type"]?.jsonPrimitive?.content)
            assertEquals(1, blocks.size)
            assertEquals(
                note.uid.toString(),
                blocks
                    .single()
                    .jsonObject["id"]
                    ?.jsonPrimitive
                    ?.content,
            )
        }
    }

    @Test
    fun `capture details use the lexicon's names`() {
        val record = mapper.toRecord(fullNotes[1])

        assertEquals("watch", record["capturedOn"]?.jsonPrimitive?.content)
        assertEquals("inVehicle", record["capturedDuring"]?.jsonPrimitive?.content)
        assertEquals(
            "framed",
            record
                .getValue("blocks")
                .jsonArray[0]
                .jsonObject["presentation"]
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun `coordinates are decimal strings and accuracy is in centimeters`() {
        val location = mapper.toRecord(fullNotes[0]).getValue("location").jsonObject
        val geo = location.getValue("geo").jsonObject

        assertEquals("-33.86882", geo["latitude"]?.jsonPrimitive?.content)
        assertEquals("151.209295", geo["longitude"]?.jsonPrimitive?.content)
        assertEquals("-12.5", geo["altitude"]?.jsonPrimitive?.content)
        assertEquals("475", location["accuracyCentimeters"]?.jsonPrimitive?.content)
        assertEquals(ref("app.logdate.place.place", home.id.toString()), location["place"])
        assertEquals(
            buildJsonObject {
                put("name", JsonPrimitive("Casa da Avó 🏡"))
                put(
                    "geo",
                    buildJsonObject {
                        put("latitude", JsonPrimitive("38.7223"))
                        put("longitude", JsonPrimitive("-9.1393"))
                    },
                )
            },
            location["placeDetails"],
        )
    }

    @Test
    fun `a place reads back from its details without the place record`() {
        val farAway = NotePlace(Uuid.random(), "", -54.8019, -68.303)
        val note = fullNotes[0].withLocation(NoteLocation(place = farAway))

        assertEquals(note, roundTrip(note))
    }

    @Test
    fun `locations at the edges of the map round-trip`() {
        val edges =
            listOf(
                NoteCoordinates(latitude = -90.0, longitude = -180.0),
                NoteCoordinates(latitude = 90.0, longitude = 180.0, altitude = 8848.86, accuracy = 0f),
                NoteCoordinates(latitude = 1.0E-7, longitude = -4.5E-5, altitude = -0.0),
                NoteCoordinates(latitude = 0.0, longitude = 0.0, accuracy = 1_500_000f),
            )

        edges.forEach { coordinates ->
            val note = fullNotes[0].withLocation(NoteLocation(coordinates = coordinates))
            assertEquals(note, roundTrip(note))
        }
        assertEquals(
            fullNotes[0].withLocation(NoteLocation(place = home)),
            roundTrip(fullNotes[0].withLocation(NoteLocation(place = home))),
        )
    }

    @Test
    fun `a negative accuracy is stored as 0`() {
        val note = fullNotes[0].withLocation(NoteLocation(coordinates = NoteCoordinates(1.0, 2.0, accuracy = -3f)))

        assertEquals(0f, assertLocation(roundTrip(note)).coordinates?.accuracy)
    }

    @Test
    fun `accuracy finer than a centimeter is rounded`() {
        val note = fullNotes[0].withLocation(NoteLocation(coordinates = NoteCoordinates(1.0, 2.0, accuracy = 12.646f)))

        val location = assertLocation(roundTrip(note))

        assertEquals(12.65f, location.coordinates?.accuracy)
    }

    @Test
    fun `a location with nothing in it isn't written`() {
        val note = fullNotes[0].withLocation(NoteLocation())

        assertFalse("location" in mapper.toRecord(note))
        assertNull(roundTrip(note).location)
    }

    @Test
    fun `a place without a name or center is accepted and written back without them`() {
        val record = mapper.toRecord(fullNotes[0].withLocation(NoteLocation(place = home))).withLocation { JsonObject(it - "placeDetails") }
        assertValidRecord(mapper.collection, record)

        val note = mapper.fromRecord(mapper.rkeyOf(fullNotes[0]), record)

        assertEquals(NotePlace(home.id), assertLocation(note).place)
        assertNull(assertLocation(note).place?.name)
        assertNull(assertLocation(note).place?.latitude)
        assertEquals(record, mapper.toRecord(note))
    }

    @Test
    fun `a place with only some of a name and a center is refused rather than half written`() {
        listOf(NotePlace(home.id, name = "Home"), NotePlace(home.id, latitude = 1.0, longitude = 2.0)).forEach { partial ->
            val note = fullNotes[0].withLocation(NoteLocation(place = partial))

            assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(note) }
        }
    }

    @Test
    fun `an accuracy without coordinates is accepted and left out`() {
        val withoutGeo = mapper.toRecord(fullNotes[0]).withLocation { JsonObject(it - "geo") }
        assertValidRecord(mapper.collection, withoutGeo)
        val onlyAccuracy = mapper.toRecord(fullNotes[0]).withLocation { JsonObject(it - "geo" - "place" - "placeDetails") }
        assertValidRecord(mapper.collection, onlyAccuracy)

        val keepsPlace = mapper.fromRecord(mapper.rkeyOf(fullNotes[0]), withoutGeo)
        val noLocation = mapper.fromRecord(mapper.rkeyOf(fullNotes[0]), onlyAccuracy)

        assertEquals(NoteLocation(place = home), assertLocation(keepsPlace))
        assertNull(noLocation.location)
    }

    @Test
    fun `record keys and block ids match regardless of case`() {
        fullNotes.forEach { note ->
            val upper = note.uid.toString().uppercase()
            val record = mapper.toRecord(note).withBlock { block -> block + ("id" to JsonPrimitive(upper)) }

            assertEquals(note, mapper.fromRecord(upper, record), upper)
            assertEquals(note.uid.toString(), mapper.rkeyOf(mapper.fromRecord(upper, record)))
        }
    }

    @Test
    fun `an audio note's transcript reference matches its key in either case`() {
        val audio = fullNotes[3].withTranscript()
        val upperRef = ref("app.logdate.journal.transcript", "${audioId.toString().uppercase()}:${audioId.toString().uppercase()}")
        val record = mapper.toRecord(audio).withBlock { it + ("transcript" to upperRef) }

        assertEquals(audio.withoutTranscript(), mapper.fromRecord(audioId.toString().uppercase(), record))
    }

    @Test
    fun `entries that can't be one note are refused`() {
        val note = fullNotes[0]
        val rkey = mapper.rkeyOf(note)
        val record = mapper.toRecord(note)
        val block = record.getValue("blocks").jsonArray[0]
        val unknownBlock = buildJsonObject { put("\$type", JsonPrimitive("app.logdate.journal.entry#stickerBlock")) }
        val audio = mapper.toRecord(fullNotes[3].withTranscript())
        val refusals =
            listOf(
                "not-a-uuid" to record,
                Uuid.random().toString() to record,
                rkey to JsonObject(record + ("blocks" to JsonArray(listOf(block, block)))),
                rkey to JsonObject(record + ("blocks" to JsonArray(emptyList()))),
                rkey to JsonObject(record + ("blocks" to JsonArray(listOf(unknownBlock)))),
                rkey to record.withBlock { it + ("format" to JsonPrimitive("html")) },
                rkey to JsonObject(record + ("createdAt" to JsonPrimitive("last tuesday"))),
                rkey to JsonObject(record + ("\$type" to JsonPrimitive("app.logdate.journal.draft"))),
                rkey to record.withLocation { it + ("place" to ref("app.logdate.journal.journal", home.id.toString())) },
                rkey to record.withLocation { JsonObject(it - "place") },
                rkey to record.withLocation { it + ("place" to ref("app.logdate.place.place", "home")) },
                rkey to
                    record.withLocation {
                        it +
                            (
                                "geo" to
                                    buildJsonObject {
                                        put("latitude", JsonPrimitive("north"))
                                        put("longitude", JsonPrimitive("0"))
                                    }
                            )
                    },
                mapper.rkeyOf(fullNotes[3]) to audio.withBlock { it + ("transcript" to ref("app.logdate.journal.transcript", "other")) },
            )

        refusals.forEach { (key, refused) ->
            assertFailsWith<SpaceRecordMappingException>("$key $refused") { mapper.fromRecord(key, refused) }
        }
    }

    @Test
    fun `values outside a record's range are refused`() {
        val refusals =
            listOf(
                fullNotes[0].withLocation(NoteLocation(coordinates = NoteCoordinates(Double.NaN, 0.0))),
                fullNotes[0].withLocation(NoteLocation(coordinates = NoteCoordinates(0.0, 0.0, accuracy = Float.NaN))),
                fullNotes[0].withLocation(NoteLocation(place = home.copy(longitude = Double.POSITIVE_INFINITY))),
                (fullNotes[3] as JournalNote.Audio).copy(durationMs = -1),
                (fullNotes[2] as JournalNote.Video).copy(durationMs = -1),
            )

        refusals.forEach { note -> assertFailsWith<SpaceRecordMappingException>("$note") { mapper.toRecord(note) } }
    }

    @Test
    fun `text and captions up to the lexicon's limit are valid records`() {
        val longest = "é".repeat(100_000)
        val text = JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = longest)
        val image = (fullNotes[1] as JournalNote.Image).copy(caption = longest)
        val place = fullNotes[0].withLocation(NoteLocation(place = home.copy(name = longest)))

        listOf(text, image, place).forEach { note -> assertEquals(note, roundTrip(note)) }
        assertEquals(
            listOf("blocks[0].text: longer than 100000 graphemes"),
            lexiconIssues(mapper.collection, mapper.toRecord(text.copy(content = longest + "é"))),
        )
        assertEquals(
            listOf("blocks[0].caption: longer than 100000 graphemes"),
            lexiconIssues(mapper.collection, mapper.toRecord(image.copy(caption = longest + "é"))),
        )
    }
}
