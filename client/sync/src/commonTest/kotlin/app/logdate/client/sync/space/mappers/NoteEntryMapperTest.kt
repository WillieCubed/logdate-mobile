package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptSegment
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.shared.model.PhotoPresentation
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
import kotlin.time.Instant
import kotlin.uuid.Uuid

class NoteEntryMapperTest {
    private val created = Instant.parse("2026-10-12T07:41:09.512Z")
    private val updated = Instant.parse("2026-10-13T21:02:00.000000001Z")
    private val home = NotePlace(Uuid.parse("0b8f3c1e-6a2d-4e9f-8c7b-1d2e3f4a5b6c"), "Casa da Avó 🏡", 38.7223, -9.1393)
    private val mapper = NoteEntryMapper()
    private val textId = Uuid.random()
    private val imageId = Uuid.random()
    private val videoId = Uuid.random()
    private val audioId = Uuid.random()
    private val photoRef = uploadedRef(imageId, "aa", "image/jpeg")
    private val clipRef = uploadedRef(videoId, "bb", "video/mp4", size = null)
    private val voiceRef = uploadedRef(audioId, "cc", "audio/mp4")

    private val fullLocation =
        NoteLocation(
            coordinates = NoteCoordinates(latitude = -33.868820, longitude = 151.209295, altitude = -12.5, accuracy = 4.75f),
            place = home,
        )

    private val fullNotes: List<JournalNote> =
        listOf(
            JournalNote.Text(
                uid = textId,
                creationTimestamp = created,
                lastUpdated = updated,
                content = "# A walk\n\nBy the river, with **Ana**. 日本語も。👨‍👩‍👧",
                location = fullLocation,
                timeZoneId = "Australia/Sydney",
                capturedOn = CaptureDevice.PHONE,
                capturedDuring = CaptureActivity.MOVING,
            ),
            JournalNote.Image(
                uid = imageId,
                creationTimestamp = created,
                lastUpdated = updated,
                mediaRef = photoRef,
                caption = "The view from the *top*",
                presentation = PhotoPresentation.Framed,
                location = fullLocation,
                timeZoneId = "Asia/Kolkata",
                capturedOn = CaptureDevice.WATCH,
                capturedDuring = CaptureActivity.IN_VEHICLE,
            ),
            JournalNote.Video(
                uid = videoId,
                creationTimestamp = created,
                lastUpdated = updated,
                mediaRef = clipRef,
                caption = "Waves",
                durationMs = 61_250,
                location = fullLocation,
                timeZoneId = "America/St_Johns",
                capturedOn = CaptureDevice.PHONE,
                capturedDuring = CaptureActivity.MOVING,
            ),
            JournalNote.Audio(
                uid = audioId,
                creationTimestamp = created,
                lastUpdated = updated,
                mediaRef = voiceRef,
                durationMs = 8_200,
                caption = "Morning thoughts",
                location = fullLocation,
                timeZoneId = "Europe/Lisbon",
                capturedOn = CaptureDevice.WATCH,
                capturedDuring = CaptureActivity.IN_VEHICLE,
            ),
        )

    private fun roundTrip(note: JournalNote): JournalNote {
        val record = mapper.toRecord(note)
        assertValidRecord(mapper.collection, record)
        return mapper.fromRecord(mapper.rkeyOf(note), record)
    }

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
    fun `a media file that isn't uploaded yet is named without its path`() {
        val image =
            JournalNote.Image(
                creationTimestamp = created,
                lastUpdated = created,
                mediaRef = "file:///private/Diary/secret-name.jpg",
            )

        val failure = assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(image) }

        assertFalse("secret-name" in failure.message.orEmpty())
        assertFalse("Diary" in failure.message.orEmpty())
    }

    @Test
    fun `an audio note refers to its transcript in its own record`() {
        val audio =
            (fullNotes[3] as JournalNote.Audio).copy(
                transcript =
                    TranscriptDocument(
                        status = TranscriptDocumentStatus.FINAL,
                        segments = listOf(TranscriptSegment("a", "Hello", 0, 900)),
                    ),
            )
        val transcripts = NoteTranscriptMapper()
        val transcript = NoteTranscript(audio.uid, audio.transcript!!)

        val entry = mapper.toRecord(audio)
        val ref =
            entry
                .getValue("blocks")
                .jsonArray[0]
                .jsonObject
                .getValue("transcript")
                .jsonObject

        assertValidRecord(mapper.collection, entry)
        assertEquals("app.logdate.journal.transcript", ref["collection"]?.jsonPrimitive?.content)
        assertEquals(transcripts.rkeyOf(transcript), ref["rkey"]?.jsonPrimitive?.content)
        val fromEntry = mapper.fromRecord(mapper.rkeyOf(audio), entry) as JournalNote.Audio
        val fromTranscript = transcripts.fromRecord(transcripts.rkeyOf(transcript), transcripts.toRecord(transcript))
        assertNull(fromEntry.transcript)
        assertEquals(audio, fromEntry.copy(transcript = fromTranscript.document))
    }

    @Test
    fun `media blocks carry the uploaded blob and the file's own type`() {
        val image =
            mapper
                .toRecord(fullNotes[1])
                .getValue("blocks")
                .jsonArray[0]
                .jsonObject
        val stored = image.getValue("media").jsonObject

        assertEquals("image/jpeg", stored["mimeType"]?.jsonPrimitive?.content)
        assertEquals("61204", stored["size"]?.jsonPrimitive?.content)
        assertEquals(
            "application/octet-stream",
            stored
                .getValue("blob")
                .jsonObject["mimeType"]
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(listOf(cidOf("aa")), mapper.blobCidsOf(fullNotes[1]))
        assertEquals(emptyList(), mapper.blobCidsOf(fullNotes[0]))
    }

    @Test
    fun `a note whose file hasn't been uploaded can't become a record`() {
        val image = JournalNote.Image(creationTimestamp = created, lastUpdated = created, mediaRef = "logdate-media://library/new.jpg")

        assertEquals(true, assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(image) }.retryable)
        assertEquals(true, assertFailsWith<SpaceRecordMappingException> { mapper.blobCidsOf(image) }.retryable)
    }

    @Test
    fun `a note whose file no device has becomes an entry without media`() {
        val unavailable =
            listOf(
                "",
                " ",
                "logdate-unavailable://archive/recovery-1/note/original",
                "logdate-unavailable://native/Y29udGVudDovL21lZGlhLzQy",
            )
        val notes =
            fullNotes.filterNot { it is JournalNote.Text }.flatMap { note ->
                unavailable.map { mediaRef ->
                    when (note) {
                        is JournalNote.Image -> note.copy(mediaRef = mediaRef)
                        is JournalNote.Video -> note.copy(mediaRef = mediaRef)
                        is JournalNote.Audio -> note.copy(mediaRef = mediaRef, transcript = TranscriptDocument())
                        is JournalNote.Text -> note
                    }
                }
            }

        notes.forEach { note ->
            val record = mapper.toRecord(note)
            val block = record.getValue("blocks").jsonArray[0].jsonObject
            val missing = "logdate-unavailable://archive/${note.uid}/original"

            assertValidRecord(mapper.collection, record)
            assertFalse("media" in block, "$note")
            assertEquals(emptyList(), mapper.blobCidsOf(note))
            val readBack = mapper.fromRecord(mapper.rkeyOf(note), record)
            assertEquals(note.withMediaRef(missing).withoutTranscript(), readBack)
        }
    }

    @Test
    fun `a note's unavailable file reads back the same each time`() {
        val image = (fullNotes[1] as JournalNote.Image).copy(mediaRef = "")
        val once = mapper.fromRecord(mapper.rkeyOf(image), mapper.toRecord(image))

        assertEquals(once, roundTrip(once))
    }

    @Test
    fun `a missing updatedAt reads as the creation time`() {
        val record = JsonObject(mapper.toRecord(fullNotes[0]) - "updatedAt")

        assertEquals(created, mapper.fromRecord(mapper.rkeyOf(fullNotes[0]), record).lastUpdated)
    }

    @Test
    fun `capture details a newer app records read as unknown`() {
        val record =
            JsonObject(
                mapper.toRecord(fullNotes[0]) +
                    mapOf("capturedOn" to JsonPrimitive("glasses"), "capturedDuring" to JsonPrimitive("swimming")),
            )
        assertValidRecord(mapper.collection, record)

        val note = mapper.fromRecord(mapper.rkeyOf(fullNotes[0]), record)

        assertNull(note.capturedOn)
        assertNull(note.capturedDuring)
    }

    @Test
    fun `a photo framing a newer app adds reads as edge to edge`() {
        val note = fullNotes[1]
        val record = mapper.toRecord(note).withBlock { it + ("presentation" to JsonPrimitive("polaroid")) }

        assertEquals(PhotoPresentation.EdgeToEdge, (mapper.fromRecord(mapper.rkeyOf(note), record) as JournalNote.Image).presentation)
    }

    @Test
    fun `text marked as markdown reads as written`() {
        val note = fullNotes[0]
        val record = mapper.toRecord(note).withBlock { it + ("format" to JsonPrimitive("markdown")) }

        assertEquals(note, mapper.fromRecord(mapper.rkeyOf(note), record))
    }

    @Test
    fun `plain text reads as markdown that shows the same characters`() {
        val texts =
            mapOf(
                "*not bold*" to "\\*not bold\\*",
                "# not a heading" to "\\# not a heading",
                "1. not a list" to "1\\. not a list",
                "2) not a list" to "2\\) not a list",
                "- not a bullet" to "\\- not a bullet",
                "> not a quote" to "\\> not a quote",
                "---" to "\\---",
                "snake_case and `code` and [link](x) and <b>" to "snake\\_case and \\`code\\` and \\[link](x) and \\<b>",
                "Tom & Jerry &amp; co" to "Tom & Jerry \\&amp; co",
                "back\\slash" to "back\\\\slash",
                "a | b ~~c~~" to "a \\| b \\~\\~c\\~\\~",
                "Wow! 100% 3.5 - fine" to "Wow! 100% 3.5 - fine",
            )

        texts.forEach { (plain, markdown) ->
            val note = fullNotes[0] as JournalNote.Text
            val record = mapper.toRecord(note).withBlock { it + ("text" to JsonPrimitive(plain)) + ("format" to JsonPrimitive("plain")) }

            val read = mapper.fromRecord(mapper.rkeyOf(note), record) as JournalNote.Text

            assertEquals(markdown, read.content, plain)
            assertEquals(markdown, writtenText(read), "plain text round-trips as equivalent markdown")
        }
    }

    @Test
    fun `plain text keeps its line breaks and blank lines`() {
        assertEquals("one\\\ntwo\n\nthree\\\n\\# four", plainTextAsMarkdown("one\ntwo\n\nthree\r\n# four"))
        assertEquals("a\n\n\nb", plainTextAsMarkdown("a\n  \n\nb"))
        assertEquals("", plainTextAsMarkdown(""))
    }

    @Test
    fun `escaped plain text reads back as the characters it was written with`() {
        val samples =
            listOf(
                "*not bold* _nor this_ ~~nor~~ `nor` [nor](this) <nor> a|b \\ end\\",
                "# h\n## h\n> q\n- b\n+ b\n1. l\n10) l\n=== \n--\n&copy; &#35; &x",
                "plain 日本語 👨‍👩‍👧 text, with: punctuation! (and) {braces} #hash 2.5 a-b",
            )

        samples.forEach { plain -> assertEquals(plain, readMarkdownEscapes(plainTextAsMarkdown(plain)!!), plain) }
    }

    @Test
    fun `plain text with an indented line is refused because markdown drops the indentation`() {
        val note = fullNotes[0]
        val record =
            mapper.toRecord(note).withBlock {
                it + ("text" to JsonPrimitive("poem\n  indented")) +
                    ("format" to JsonPrimitive("plain"))
            }

        val failure = assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(note), record) }

        assertFalse("indented" in failure.message.orEmpty())
        assertNull(plainTextAsMarkdown("\tcode"))
    }

    @Test
    fun `a media file's text alternative and aspect ratio are written back as they arrived`() {
        val note = fullNotes[1] as JournalNote.Image
        val alt = "A lake at dawn, 100% still & quiet 🌅"
        val withAlt = note.copy(mediaRef = uploadedRef(imageId, "aa", "image/jpeg", alt = alt))
        val sent =
            mapper.toRecord(withAlt).withBlock { block ->
                val media = block.getValue("media").jsonObject
                val ratio =
                    buildJsonObject {
                        put("width", JsonPrimitive(4))
                        put("height", JsonPrimitive(3))
                    }
                block + ("media" to JsonObject(media + ("aspectRatio" to ratio)))
            }
        assertValidRecord(mapper.collection, sent)

        val received = mapper.fromRecord(mapper.rkeyOf(note), sent) as JournalNote.Image
        val resent = mapper.toRecord(received)

        assertEquals(sent, resent)
        assertEquals(
            alt,
            resent
                .getValue("blocks")
                .jsonArray[0]
                .jsonObject
                .getValue("media")
                .jsonObject["alt"]
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun `a video's length survives being read and written again`() {
        val video = fullNotes[2] as JournalNote.Video
        val received = mapper.fromRecord(mapper.rkeyOf(video), mapper.toRecord(video)) as JournalNote.Video

        assertEquals(61_250L, received.durationMs)
        assertEquals(mapper.toRecord(video), mapper.toRecord(received))
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

    private fun writtenText(note: JournalNote.Text): String =
        mapper
            .toRecord(note)
            .getValue("blocks")
            .jsonArray[0]
            .jsonObject
            .getValue("text")
            .jsonPrimitive.content

    /** The text Markdown shows for [markdown] made only of escapes and hard breaks, as the CommonMark spec reads them. */
    private fun readMarkdownEscapes(markdown: String): String {
        val punctuation = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
        val out = StringBuilder()
        var index = 0
        while (index < markdown.length) {
            val char = markdown[index]
            val next = markdown.getOrNull(index + 1)
            when {
                char == '\\' && next != null && next in punctuation -> {
                    out.append(next)
                    index += 2
                }

                char == '\\' && next == '\n' -> {
                    out.append('\n')
                    index += 2
                }

                else -> {
                    out.append(char)
                    index++
                }
            }
        }
        return out.toString()
    }

    private fun assertLocation(note: JournalNote): NoteLocation = note.location ?: error("no location")

    private fun JournalNote.withLocation(location: NoteLocation): JournalNote =
        when (this) {
            is JournalNote.Text -> copy(location = location)
            is JournalNote.Image -> copy(location = location)
            is JournalNote.Video -> copy(location = location)
            is JournalNote.Audio -> copy(location = location)
        }

    private fun JournalNote.withMediaRef(mediaRef: String): JournalNote =
        when (this) {
            is JournalNote.Text -> this
            is JournalNote.Image -> copy(mediaRef = mediaRef)
            is JournalNote.Video -> copy(mediaRef = mediaRef)
            is JournalNote.Audio -> copy(mediaRef = mediaRef)
        }

    private fun JournalNote.withoutTranscript(): JournalNote = if (this is JournalNote.Audio) copy(transcript = null) else this

    private fun JournalNote.withTranscript(): JournalNote = (this as JournalNote.Audio).copy(transcript = TranscriptDocument())

    private fun JsonObject.withBlock(change: (JsonObject) -> Map<String, kotlinx.serialization.json.JsonElement>): JsonObject {
        val block = getValue("blocks").jsonArray[0].jsonObject
        return JsonObject(this + ("blocks" to JsonArray(listOf(JsonObject(change(block))))))
    }

    private fun JsonObject.withLocation(change: (JsonObject) -> Map<String, kotlinx.serialization.json.JsonElement>): JsonObject =
        JsonObject(this + ("location" to JsonObject(change(getValue("location").jsonObject))))

    private fun ref(
        collection: String,
        rkey: String,
    ) = buildJsonObject {
        put("collection", JsonPrimitive(collection))
        put("rkey", JsonPrimitive(rkey))
    }
}
