package app.logdate.client.feature.widgets

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetRestoreRepairTest {
    @Test
    fun `backgrounded or superseded setup cannot write after preparing its selection`() {
        val source = mapOf(3 to choices("retained entry"))
        val preferences = MemoryPreferences(source)
        val journal = MemoryJournal()
        var authorized = true
        val applied =
            WidgetRestoreRepair(journal, preferences).update(3, canCommit = { authorized }) {
                authorized = false
                choices("superseded selection")
            }
        assertFalse(applied)
        assertEquals(source, preferences.values)
        assertEquals(WidgetRestoreState(), journal.state)
    }

    @Test
    fun `repeated system replacement does not mistake a superseded binding for user deletion`() {
        val source = mapOf(5 to choices("retained entry"))
        val preferences = MemoryPreferences(source)
        val repair = WidgetRestoreRepair(MemoryJournal(), preferences)
        repair.remap(intArrayOf(5), intArrayOf(2))
        repair.remap(intArrayOf(5), intArrayOf(3))
        repair.reconcile(setOf(3))
        preferences.values = source
        repair.remap(intArrayOf(5), intArrayOf(4))
        assertEquals(choices("retained entry"), preferences.values[4])
    }

    @Test
    fun `delayed overlapping mapping retains immutable source values and newer target choices`() {
        val source = mapOf(1 to choices("first source"), 2 to choices("second source"))
        val preferences = MemoryPreferences(source)
        val journal = MemoryJournal()
        val repair = WidgetRestoreRepair(journal, preferences)
        repair.update(2) { choices("newer target choice") }
        repair.remap(intArrayOf(1, 2), intArrayOf(2, 3), setOf(2, 3), sourceValues = source)
        assertEquals(choices("newer target choice"), preferences.values[2])
        assertEquals(choices("second source"), preferences.values[3])
        preferences.values = source
        repair.remap(intArrayOf(1, 2), intArrayOf(2, 3), setOf(2, 3), sourceValues = source)
        assertEquals(choices("newer target choice"), preferences.values[2])
        assertEquals(choices("second source"), preferences.values[3])
    }

    @Test
    fun `a remote source ID cannot erase an independent live local widget`() {
        val source = mapOf(1 to choices("remote source"))
        for (retainedSource in listOf(source, null)) {
            val preferences = MemoryPreferences(emptyMap())
            val journal = MemoryJournal()
            val repair = WidgetRestoreRepair(journal, preferences)
            repair.update(1) { choices("independent local choice") }
            val localRecord = journal.state.widgets.getValue(1)
            // A native delivery may replace preferences before its delayed mapping arrives.
            preferences.values = source
            repair.remap(intArrayOf(1), intArrayOf(3), setOf(1, 3), sourceValues = retainedSource)
            assertEquals(choices("independent local choice"), preferences.values[1])
            assertEquals(choices("remote source"), preferences.values[3])
            assertEquals(localRecord, journal.state.widgets[1])
        }
    }

    @Test
    fun `a live source ID collision cannot transfer an unrelated local lineage`() {
        val remote = choices("remote source")
        val local = RestoredWidgetChoices(sourceIds = setOf(9), values = choices("local choice"), explicitlyChosen = true)
        val preferences = MemoryPreferences(mapOf(1 to remote))
        val journal = MemoryJournal().apply { state = WidgetRestoreState(widgets = mapOf(1 to local)) }
        val repair = WidgetRestoreRepair(journal, preferences)
        repair.remap(intArrayOf(1), intArrayOf(3), setOf(1, 3), sourceValues = mapOf(1 to remote))
        assertEquals(local, journal.state.widgets[1])
        assertEquals(
            setOf(1),
            journal.state.widgets
                .getValue(3)
                .sourceIds,
        )
        repair.remove(3)
        repair.reconcile(setOf(1))
        assertEquals(local, journal.state.widgets[1])
        assertFalse(9 in journal.state.retiredSourceIds)
        assertEquals(local.values, preferences.values[1])
    }

    @Test
    fun `source cleanup retains unjournaled live local choices while deleting obsolete source keys`() {
        val local = choices("legacy live local choice")
        val restored = choices("restored choice")
        val preferences = MemoryPreferences(mapOf(1 to local, 2 to local, 4 to restored, 5 to restored))
        val journal =
            MemoryJournal().apply {
                state =
                    WidgetRestoreState(
                        widgets = mapOf(3 to RestoredWidgetChoices(sourceIds = setOf(2, 4), values = restored)),
                        retiredSourceIds = setOf(1, 5),
                    )
            }
        WidgetRestoreRepair(journal, preferences).reconcile(setOf(1, 2, 3))
        assertEquals(mapOf(1 to local, 2 to local, 3 to restored), preferences.values)
    }

    @Test
    fun `an obsolete local source binding still transfers lineage and clears old choices`() {
        val source = choices("source choice")
        val preferences = MemoryPreferences(mapOf(1 to source, 9 to source))
        val journal =
            MemoryJournal().apply {
                state = WidgetRestoreState(widgets = mapOf(1 to RestoredWidgetChoices(sourceIds = setOf(9), values = source)))
            }
        WidgetRestoreRepair(journal, preferences).remap(intArrayOf(1), intArrayOf(3), setOf(3), sourceValues = mapOf(1 to source))
        assertNull(journal.state.widgets[1])
        assertEquals(
            setOf(1, 9),
            journal.state.widgets
                .getValue(3)
                .sourceIds,
        )
        assertEquals(mapOf(3 to source), preferences.values)
    }

    @Test
    fun `an ordinary retired local ID cannot suppress an incoming mapping when reused by a live local widget`() {
        val preferences = MemoryPreferences(emptyMap())
        val journal = MemoryJournal()
        val repair = WidgetRestoreRepair(journal, preferences)
        repair.update(1) { choices("deleted source") }
        repair.remove(1)
        repair.reconcile(emptySet())
        repair.update(1) { choices("reused local choice") }
        preferences.values = mapOf(1 to choices("deleted source"))
        repair.remap(intArrayOf(1), intArrayOf(3), setOf(1, 3), sourceValues = mapOf(1 to choices("deleted source")))
        assertEquals(choices("reused local choice"), preferences.values[1])
        assertEquals(choices("deleted source"), preferences.values[3])
        assertEquals(
            emptySet(),
            journal.state.widgets
                .getValue(1)
                .sourceIds,
        )
        assertEquals(
            setOf(1),
            journal.state.widgets
                .getValue(3)
                .sourceIds,
        )
    }

    @Test
    fun `deleted restored source cannot resurrect after its number becomes an independent live local widget`() {
        val source = mapOf(1 to choices("remote source"))
        val preferences = MemoryPreferences(source)
        val journal = MemoryJournal()
        val repair = WidgetRestoreRepair(journal, preferences)
        repair.remap(intArrayOf(1), intArrayOf(3), setOf(3), sourceValues = source)
        repair.remove(3)
        repair.reconcile(emptySet())
        val persisted = Json.encodeToString(journal.state)
        assertTrue(persisted.contains("\"retiredRestoredSourceIds\":[1]"))
        journal.state = Json.decodeFromString<WidgetRestoreState>(persisted)
        repair.update(1) { choices("independent local choice") }
        preferences.values = source
        repair.remap(intArrayOf(1), intArrayOf(4), setOf(1, 4), sourceValues = source)
        assertEquals(choices("independent local choice"), preferences.values[1])
        assertNull(preferences.values[4])
        assertNull(journal.state.widgets[4])
    }

    @Test
    fun `ordinary local retirement keeps its distinction through persisted state`() {
        val preferences = MemoryPreferences(emptyMap())
        val journal = MemoryJournal()
        val repair = WidgetRestoreRepair(journal, preferences)
        repair.update(1) { choices("ordinary deleted choice") }
        repair.remove(1)
        repair.reconcile(emptySet())
        val persisted = Json.encodeToString(journal.state)
        assertTrue(persisted.contains("\"retiredRestoredSourceIds\":[]"))
        journal.state = Json.decodeFromString<WidgetRestoreState>(persisted)
        repair.update(1) { choices("independent local choice") }
        val source = mapOf(1 to choices("remote source"))
        preferences.values = source
        repair.remap(intArrayOf(1), intArrayOf(4), setOf(1, 4), sourceValues = source)
        assertEquals(choices("independent local choice"), preferences.values[1])
        assertEquals(choices("remote source"), preferences.values[4])
    }

    @Test
    fun `legacy retirement without provenance remains conservative after a round trip`() {
        val legacyStates =
            listOf(
                """{"retiredSourceIds":[1]}""",
                """{"retiredSourceIds":[1],"retiredRestoredSourceIds":null}""",
            )
        legacyStates.forEach { encoded ->
            val preferences = MemoryPreferences(emptyMap())
            val journal = MemoryJournal().apply { state = Json.decodeFromString<WidgetRestoreState>(encoded) }
            journal.state = Json.decodeFromString<WidgetRestoreState>(Json.encodeToString(journal.state))
            val repair = WidgetRestoreRepair(journal, preferences)
            repair.update(1) { choices("independent local choice") }
            val source = mapOf(1 to choices("remote source"))
            preferences.values = source
            repair.remap(intArrayOf(1), intArrayOf(4), setOf(1, 4), sourceValues = source)
            assertEquals(choices("independent local choice"), preferences.values[1])
            assertNull(preferences.values[4])
        }
    }

    @Test
    fun `uncompacted remote deletion blocks a different target with or without a live local ID collision`() {
        for (independentLocalWidget in listOf(false, true)) {
            val source = mapOf(1 to choices("remote source"))
            val preferences = MemoryPreferences(source)
            val journal = MemoryJournal()
            val repair = WidgetRestoreRepair(journal, preferences)
            repair.remap(intArrayOf(1), intArrayOf(3), setOf(3), sourceValues = source)
            repair.remove(3)
            if (independentLocalWidget) repair.update(1) { choices("independent local choice") }
            preferences.values = source
            val live = if (independentLocalWidget) setOf(1, 4) else setOf(4)
            repair.remap(intArrayOf(1), intArrayOf(4), live, sourceValues = source)
            if (independentLocalWidget) assertEquals(choices("independent local choice"), preferences.values[1])
            assertNull(preferences.values[4])
            assertNull(journal.state.widgets[4])
        }
    }

    @Test
    fun `uncompacted ordinary local deletion admits an unrelated mapping when its ID remains live`() {
        val preferences = MemoryPreferences(emptyMap())
        val journal = MemoryJournal()
        val repair = WidgetRestoreRepair(journal, preferences)
        repair.update(1) { choices("ordinary deleted choice") }
        repair.remove(1)
        val source = mapOf(1 to choices("remote source"))
        preferences.values = source
        repair.remap(intArrayOf(1), intArrayOf(4), setOf(1, 4), sourceValues = source)
        assertEquals(choices("remote source"), preferences.values[4])
        assertTrue(
            journal.state.widgets
                .getValue(1)
                .removed,
        )
    }

    @Test
    fun `a different restored source can reuse a deleted destination before compaction`() {
        assertDifferentSourceCanReuseDeletedDestination(compactFirst = false)
    }

    @Test
    fun `a different restored source can reuse a deleted destination after compaction`() {
        assertDifferentSourceCanReuseDeletedDestination(compactFirst = true)
    }

    @Test
    fun `removal retires its remote source before explicit destination reuse and restart`() {
        for (restart in listOf(false, true)) {
            val source = mapOf(1 to choices("remote source"))
            val preferences = MemoryPreferences(source)
            val journal = MemoryJournal()
            var repair = WidgetRestoreRepair(journal, preferences)
            repair.remap(intArrayOf(1), intArrayOf(3), setOf(3), sourceValues = source)
            repair.remove(3)
            assertEquals(setOf(1, 3), journal.state.retiredSourceIds)
            assertEquals(setOf(1), journal.state.retiredRestoredSourceIds)
            if (restart) {
                journal.state = Json.decodeFromString<WidgetRestoreState>(Json.encodeToString(journal.state))
                repair = WidgetRestoreRepair(journal, preferences)
            }
            assertTrue(repair.update(3) { choices("new explicit choice") })
            if (restart) {
                journal.state = Json.decodeFromString<WidgetRestoreState>(Json.encodeToString(journal.state))
                repair = WidgetRestoreRepair(journal, preferences)
            }
            preferences.values = source
            repair.remap(intArrayOf(1), intArrayOf(4), setOf(3, 4), sourceValues = source)
            assertEquals(choices("new explicit choice"), preferences.values[3])
            assertNull(preferences.values[4])
            assertNull(journal.state.widgets[4])
        }
    }

    @Test
    fun `explicit reuse preserves a legacy removed record and unknown retired provenance`() {
        val legacy =
            """{"widgets":{"3":{"sourceIds":[1],"removed":true,"explicitlyChosen":true}},"retiredSourceIds":[7]}"""
        val preferences = MemoryPreferences(emptyMap())
        val journal = MemoryJournal().apply { state = Json.decodeFromString<WidgetRestoreState>(legacy) }
        val repair = WidgetRestoreRepair(journal, preferences)
        assertTrue(repair.update(3) { choices("new explicit choice") })
        journal.state = Json.decodeFromString<WidgetRestoreState>(Json.encodeToString(journal.state))
        assertEquals(setOf(1, 7), journal.state.retiredRestoredSourceIds)
        preferences.values = mapOf(1 to choices("deleted remote source"), 7 to choices("unknown old source"))
        repair.remap(intArrayOf(1, 7), intArrayOf(4, 5), setOf(3, 4, 5), sourceValues = preferences.values)
        assertEquals(mapOf(3 to choices("new explicit choice")), preferences.values)
        assertNull(journal.state.widgets[4])
        assertNull(journal.state.widgets[5])
    }

    @Test
    fun `revoked legacy reconfiguration commits nothing and a later authorized choice still excludes its deleted source`() {
        val legacy = """{"widgets":{"3":{"sourceIds":[1],"removed":true}}}"""
        val preferences = MemoryPreferences(emptyMap())
        val journal = MemoryJournal().apply { state = Json.decodeFromString<WidgetRestoreState>(legacy) }
        val before = journal.state
        val repair = WidgetRestoreRepair(journal, preferences)
        assertFalse(repair.update(3, canCommit = { false }) { choices("unauthorized choice") })
        assertEquals(before, journal.state)
        assertEquals(emptyMap(), preferences.values)
        assertTrue(repair.update(3) { choices("authorized choice") })
        val source = mapOf(1 to choices("deleted source"))
        preferences.values = source
        repair.remap(intArrayOf(1), intArrayOf(4), setOf(3, 4), sourceValues = source)
        assertEquals(mapOf(3 to choices("authorized choice")), preferences.values)
        assertNull(journal.state.widgets[4])
    }

    @Test
    fun `identity restored source retains remote deletion provenance when its number is reused`() {
        val source = mapOf(1 to choices("remote source"))
        val preferences = MemoryPreferences(source)
        val journal = MemoryJournal()
        val repair = WidgetRestoreRepair(journal, preferences)
        repair.remap(intArrayOf(1), intArrayOf(1), setOf(1), sourceValues = source)
        repair.remove(1)
        repair.reconcile(emptySet())
        repair.update(1) { choices("independent local choice") }
        preferences.values = source
        repair.remap(intArrayOf(1), intArrayOf(4), setOf(1, 4), sourceValues = source)
        assertEquals(choices("independent local choice"), preferences.values[1])
        assertNull(preferences.values[4])
    }

    @Test
    fun `an explicit ABA selection still wins over the delayed source mapping`() {
        val source = mapOf(1 to choices("first source"), 2 to choices("second source"))
        val preferences = MemoryPreferences(source)
        val repair = WidgetRestoreRepair(MemoryJournal(), preferences)
        repair.update(2) { choices("different target") }
        repair.update(2) { choices("second source") }
        repair.remap(intArrayOf(1, 2), intArrayOf(2, 3), setOf(2, 3), sourceValues = source)
        assertEquals(choices("second source"), preferences.values[2])
        assertEquals(choices("second source"), preferences.values[3])
    }

    @Test
    fun `a delayed remap preserves a newer explicitly configured destination`() {
        val preferences = MemoryPreferences(mapOf(3 to choices("backup entry")))
        val journal = MemoryJournal()
        val repair = WidgetRestoreRepair(journal, preferences)
        // The receiver could not commit its initial remap; configuration wins before retry.
        repair.update(2) { choices("newer selection") }
        repair.remap(intArrayOf(3), intArrayOf(2))
        assertEquals(choices("newer selection"), preferences.values[2])
        preferences.values = mapOf(3 to choices("backup entry"))
        repair.reconcile(setOf(2))
        assertEquals(mapOf(2 to choices("newer selection")), preferences.values)
    }

    @Test
    fun `deleted ordinary widget cannot return through an older backup after compaction`() {
        val preferences = MemoryPreferences(emptyMap())
        val journal = MemoryJournal()
        val repair = WidgetRestoreRepair(journal, preferences)
        repair.update(3) { choices("deleted entry") }
        repair.remove(3)
        repair.reconcile(emptySet())
        preferences.values = mapOf(3 to choices("deleted entry"))
        repair.remap(intArrayOf(3), intArrayOf(2))
        assertNull(preferences.values[2])
        assertNull(preferences.values[3])
    }

    @Test
    fun `thousands of retired native mappings retain compact deletion provenance`() {
        val preferences = MemoryPreferences(emptyMap())
        val journal = MemoryJournal()
        val repair = WidgetRestoreRepair(journal, preferences)
        for (id in 1..4100) {
            preferences.values = mapOf(id to choices("deleted entry"))
            repair.remap(intArrayOf(id), intArrayOf(id + 10000))
            repair.remove(id + 10000)
            repair.reconcile(emptySet())
        }
        assertTrue(journal.state.widgets.isEmpty())
        assertTrue((1..4100).all { it in journal.state.retiredSourceIds })
        assertTrue(Json.encodeToString(journal.state).encodeToByteArray().size < 65536)
    }

    @Test
    fun `repeated native preferences retain the system remapped widget without a second callback`() {
        val source = mapOf(3 to choices("retained entry"))
        val preferences = MemoryPreferences(source)
        val journal = MemoryJournal()
        WidgetRestoreRepair(journal, preferences).remap(intArrayOf(3), intArrayOf(1))
        assertEquals(choices("retained entry"), preferences.values[1])
        // The second platform delivery replaces preferences but retains the system binding.
        preferences.values = source
        WidgetRestoreRepair(journal, preferences).reconcile(setOf(1))
        assertEquals(choices("retained entry"), preferences.values[1])
        assertFalse(3 in preferences.values)
    }

    @Test
    fun `newer choices survive both cold startup and a repeated native remap`() {
        val source = mapOf(3 to choices("old entry"))
        val preferences = MemoryPreferences(source)
        val journal = MemoryJournal()
        val repair = WidgetRestoreRepair(journal, preferences)
        repair.remap(intArrayOf(3), intArrayOf(1))
        repair.update(1) { choices("newer entry") }
        WidgetRestoreRepair(journal, preferences).reconcile(setOf(1))
        assertEquals(choices("newer entry"), preferences.values[1])
        preferences.values = source
        WidgetRestoreRepair(journal, preferences).remap(intArrayOf(3), intArrayOf(1))
        assertEquals(choices("newer entry"), preferences.values[1])
    }

    @Test
    fun `deleted widgets stay removed when source preferences or callbacks return`() {
        val source = mapOf(3 to choices("old entry"))
        val preferences = MemoryPreferences(source)
        val journal = MemoryJournal()
        val repair = WidgetRestoreRepair(journal, preferences)
        repair.remap(intArrayOf(3), intArrayOf(1))
        repair.remove(1)
        preferences.values = source
        repair.reconcile(emptySet())
        assertEquals(emptyMap(), preferences.values)
        preferences.values = source
        repair.remap(intArrayOf(3), intArrayOf(1), liveIds = emptySet())
        assertEquals(emptyMap(), preferences.values)
    }

    @Test
    fun `a recycled widget ID keeps its new choice`() {
        val source = mapOf(3 to choices("old entry"))
        val preferences = MemoryPreferences(source)
        val journal = MemoryJournal()
        val repair = WidgetRestoreRepair(journal, preferences)
        repair.remap(intArrayOf(3), intArrayOf(1))
        repair.remove(1)
        repair.update(1) { choices("replacement widget") }
        preferences.values = source
        repair.reconcile(setOf(1))
        assertEquals(choices("replacement widget"), preferences.values[1])
        preferences.values = source
        repair.remap(intArrayOf(3), intArrayOf(1))
        assertEquals(choices("replacement widget"), preferences.values[1])
    }

    @Test
    fun `retired widget history compacts so ordinary placement never exhausts the journal`() {
        val preferences = MemoryPreferences(emptyMap())
        val journal = MemoryJournal()
        val repair = WidgetRestoreRepair(journal, preferences)
        for (id in 1..4100) {
            repair.reconcile(setOf(id))
            repair.update(id) { choices("current entry") }
            repair.remove(id)
        }
        repair.reconcile(emptySet())
        assertEquals(emptyMap(), journal.state.widgets)
    }

    @Test
    fun `interruption after journal persistence replays before reading preferences`() {
        val source = mapOf(3 to choices("retained entry"))
        val preferences = MemoryPreferences(source)
        val journal = MemoryJournal()
        preferences.failWrites = true
        assertFailsWith<IllegalStateException> {
            WidgetRestoreRepair(journal, preferences).remap(intArrayOf(3), intArrayOf(1))
        }
        assertEquals(source, preferences.values)
        preferences.failWrites = false
        WidgetRestoreRepair(journal, preferences).reconcile(setOf(1))
        assertEquals(mapOf(1 to choices("retained entry")), preferences.values)
    }

    @Test
    fun `journal persistence failure leaves source preferences untouched`() {
        val source = mapOf(3 to choices("retained entry"))
        val preferences = MemoryPreferences(source)
        val journal = MemoryJournal().apply { failWrites = true }
        assertFailsWith<IllegalStateException> {
            WidgetRestoreRepair(journal, preferences).remap(intArrayOf(3), intArrayOf(1))
        }
        assertEquals(source, preferences.values)
        assertEquals(WidgetRestoreState(), journal.state)
    }

    @Test
    fun `overlapping remaps use one snapshot and preserve unrelated choices`() {
        for (destinations in listOf(intArrayOf(2, 3), intArrayOf(2, 1))) {
            val source = mapOf(1 to choices("first"), 2 to choices("second"), 4 to choices("unrelated"))
            val preferences = MemoryPreferences(source)
            val journal = MemoryJournal()
            val repair = WidgetRestoreRepair(journal, preferences)
            repair.remap(intArrayOf(1, 2), destinations)
            preferences.values = source
            repair.reconcile(destinations.toSet() + 4)
            assertEquals(choices("first"), preferences.values[destinations[0]])
            assertEquals(choices("second"), preferences.values[destinations[1]])
            assertEquals(choices("unrelated"), preferences.values[4])
        }
    }

    @Test
    fun `incomplete or conflicting remap arrays cannot mutate preferences or the journal`() {
        for ((sourceIds, targetIds) in listOf(intArrayOf(3) to intArrayOf(), intArrayOf(3, 4) to intArrayOf(1, 1))) {
            val source = mapOf(3 to choices("retained entry"))
            val preferences = MemoryPreferences(source)
            val journal = MemoryJournal()
            assertFailsWith<IllegalArgumentException> { WidgetRestoreRepair(journal, preferences).remap(sourceIds, targetIds) }
            assertEquals(source, preferences.values)
            assertEquals(WidgetRestoreState(), journal.state)
        }
    }

    private fun assertDifferentSourceCanReuseDeletedDestination(compactFirst: Boolean) {
        for (previousWasExplicitlyChosen in listOf(false, true)) {
            val oldSource = mapOf(1 to choices("old remote source"))
            val newSource = mapOf(2 to choices("new remote source"))
            val preferences = MemoryPreferences(oldSource)
            val journal = MemoryJournal()
            val repair = WidgetRestoreRepair(journal, preferences)
            repair.remap(intArrayOf(1), intArrayOf(3), setOf(3), sourceValues = oldSource)
            if (previousWasExplicitlyChosen) repair.update(3) { choices("old explicit choice") }
            repair.remove(3)
            if (compactFirst) repair.reconcile(emptySet())
            preferences.values = newSource
            repair.remap(intArrayOf(2), intArrayOf(3), setOf(3), sourceValues = newSource)
            assertEquals(choices("new remote source"), preferences.values[3])
            val replacement = journal.state.widgets.getValue(3)
            assertFalse(replacement.removed)
            assertFalse(replacement.explicitlyChosen)
            assertEquals(setOf(2), replacement.sourceIds)
            journal.state = Json.decodeFromString<WidgetRestoreState>(Json.encodeToString(journal.state))
            preferences.values = oldSource
            repair.remap(intArrayOf(1), intArrayOf(4), setOf(3, 4), sourceValues = oldSource)
            assertEquals(choices("new remote source"), preferences.values[3])
            assertNull(preferences.values[4])
            assertNull(journal.state.widgets[4])
        }
    }

    private fun choices(note: String) =
        WidgetSettingValues(strings = mapOf("chosenNoteId" to note, "photoShape" to "CIRCLE"), flags = mapOf("usePhotoPrompt" to false))

    private class MemoryPreferences(
        var values: Map<Int, WidgetSettingValues>,
    ) : WidgetPreferencesStore {
        var failWrites = false

        override fun read() = values

        override fun write(values: Map<Int, WidgetSettingValues>) {
            check(!failWrites) { "Preference persistence interrupted" }
            this.values = values
        }
    }

    private class MemoryJournal : WidgetRestoreStateStore {
        var state = WidgetRestoreState()
        var failWrites = false

        override fun read() = state

        override fun write(state: WidgetRestoreState) {
            check(!failWrites) { "Journal persistence interrupted" }
            this.state = state
        }
    }
}
