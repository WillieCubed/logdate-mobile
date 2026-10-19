package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.shared.model.location.HistoryCorrection
import app.logdate.shared.model.location.HistoryField
import app.logdate.shared.model.location.TravelMode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PlaceCorrectionRecordMapperTest {
    private val mapper = PlaceCorrectionRecordMapper

    private fun correction(
        field: HistoryField,
        value: String,
        target: String = "4b8e0d2a-1c3f-4e5a-9b7c-0d1e2f3a4b5c",
        supersedes: List<String> = emptyList(),
    ) = HistoryCorrection("c-${field.name.lowercase()}", target, field, value, supersedes)

    private val everyKind =
        listOf(
            correction(HistoryField.LABEL, "Grandma's house 🏡", target = "place:0b6e1c4d-2f3a-4b5c-8d9e-0f1a2b3c4d5e"),
            correction(HistoryField.PLACE, "0b6e1c4d-2f3a-4b5c-8d9e-0f1a2b3c4d5e"),
            correction(HistoryField.START, "2026-10-19T09:30:00Z"),
            correction(HistoryField.END, "2026-10-19T11:15:30.250Z"),
            correction(HistoryField.DELETE, "true", target = "derived:journey:account-1:phone:a:b"),
        ) + TravelMode.entries.map { correction(HistoryField.ACTIVITY, it.name).copy(id = "c-activity-${it.name.lowercase()}") }

    @Test
    fun `every kind of correction round-trips`() {
        everyKind.forEach { assertEquals(it, mapper.roundTrip(it)) }
    }

    @Test
    fun `a correction keeps when it was made`() {
        val made = correction(HistoryField.LABEL, "Home").copy(createdAt = kotlin.time.Instant.parse("2026-10-19T12:00:00.250Z"))

        assertEquals(made, mapper.roundTrip(made))
        assertEquals("2026-10-19T12:00:00.250Z", mapper.toRecord(made)["createdAt"]?.jsonPrimitive?.content)
    }

    @Test
    fun `superseded corrections round-trip in order`() {
        val replacing = correction(HistoryField.LABEL, "Home", supersedes = listOf("c-9", "c-1", "c-5"))
        assertEquals(replacing, mapper.roundTrip(replacing))
        val full = correction(HistoryField.LABEL, "Home", supersedes = List(100) { "c-$it" })
        assertEquals(full, mapper.roundTrip(full))
    }

    @Test
    fun `each value has a field of its own type`() {
        val place = mapper.toRecord(everyKind[1])
        assertEquals("place", place["field"]?.jsonPrimitive?.content)
        assertEquals(
            "app.logdate.place.place",
            place
                .getValue("place")
                .jsonObject["collection"]
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals("2026-10-19T09:30:00Z", mapper.toRecord(everyKind[2])["startsAt"]?.jsonPrimitive?.content)
        assertEquals("2026-10-19T11:15:30.250Z", mapper.toRecord(everyKind[3])["endsAt"]?.jsonPrimitive?.content)
        val delete = mapper.toRecord(everyKind[4])
        listOf("label", "place", "activity", "startsAt", "endsAt", "supersedes").forEach { assertFalse(it in delete) }
        val vehicle = mapper.toRecord(correction(HistoryField.ACTIVITY, "VEHICLE"))
        assertEquals("vehicle", vehicle["activity"]?.jsonPrimitive?.content)
    }

    @Test
    fun `values that don't fit their field are refused`() {
        assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(correction(HistoryField.DELETE, "false")) }
        assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(correction(HistoryField.ACTIVITY, "FLYING")) }
        assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(correction(HistoryField.START, "yesterday")) }
        assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(correction(HistoryField.END, "2026-10-19T11:15:30.25Z")) }
        assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(correction(HistoryField.PLACE, "not a key")) }
        assertFailsWith<SpaceRecordMappingException> {
            mapper.toRecord(
                correction(HistoryField.LABEL, "Home", supersedes = List(101) { "c-$it" }),
            )
        }
    }

    @Test
    fun `records that don't describe one change are refused`() {
        val label = mapper.toRecord(everyKind[0])
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord("c", JsonObject(label + ("field" to JsonPrimitive("colour")))) }
        assertFailsWith<SpaceRecordMappingException> {
            mapper.fromRecord(
                "c",
                JsonObject(
                    label + ("startsAt" to JsonPrimitive("2026-10-19T09:30:00Z")),
                ),
            )
        }
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord("c", JsonObject(label - "label")) }
        val activity = mapper.toRecord(correction(HistoryField.ACTIVITY, "BUS"))
        assertFailsWith<SpaceRecordMappingException> {
            mapper.fromRecord(
                "c",
                JsonObject(activity + ("activity" to JsonPrimitive("ferry"))),
            )
        }
    }

    @Test
    fun `moving corrections over keeps only the ones not already replaced`() {
        val first = correction(HistoryField.LABEL, "Home").copy(id = "c1")
        val second = correction(HistoryField.LABEL, "Home sweet home", supersedes = listOf("c1")).copy(id = "c2")
        val third = correction(HistoryField.LABEL, "Grandma's", supersedes = listOf("c1", "c2")).copy(id = "c3")
        val unknown = correction(HistoryField.LABEL, "Elsewhere", supersedes = listOf("c3", "c-missing", "c1")).copy(id = "c4")

        val trimmed = mapper.withoutRedundantSupersedes(listOf(first, second, third, unknown))

        assertEquals(listOf(emptyList(), listOf("c1"), listOf("c2"), listOf("c3", "c-missing")), trimmed.map { it.supersedes })
        assertEquals(replaced(listOf(first, second, third, unknown)), replaced(trimmed))
    }

    @Test
    fun `a long chain of renames fits after moving over`() {
        val chain =
            (0 until 250).map { index ->
                correction(HistoryField.LABEL, "Name $index", supersedes = (0 until index).map { "c$it" }).copy(id = "c$index")
            }
        val trimmed = mapper.withoutRedundantSupersedes(chain)
        assertEquals(replaced(chain), replaced(trimmed))
        trimmed.forEach { assertEquals(it, mapper.roundTrip(it)) }
    }

    @Test
    fun `corrections that replace each other keep both`() {
        val a = correction(HistoryField.LABEL, "A", supersedes = listOf("b")).copy(id = "a")
        val b = correction(HistoryField.LABEL, "B", supersedes = listOf("a")).copy(id = "b")
        val c = correction(HistoryField.LABEL, "C", supersedes = listOf("a", "b")).copy(id = "c")
        assertEquals(listOf("a", "b"), mapper.withoutRedundantSupersedes(listOf(a, b, c)).last().supersedes)
    }

    /** Every correction each correction replaces, directly or through others. */
    private fun replaced(corrections: List<HistoryCorrection>): Map<String, Set<String>> {
        val byId = corrections.associateBy { it.id }

        fun closure(id: String): Set<String> {
            val found = mutableSetOf<String>()
            val pending = ArrayDeque(byId[id]?.supersedes.orEmpty())
            while (pending.isNotEmpty()) {
                val next = pending.removeFirst()
                if (found.add(next)) pending += byId[next]?.supersedes.orEmpty()
            }
            return found
        }
        return corrections.associate { it.id to closure(it.id) }
    }
}
