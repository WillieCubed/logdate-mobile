package app.logdate.client.feature.widgets

import kotlinx.serialization.Serializable

@Serializable
internal data class WidgetSettingValues(
    val strings: Map<String, String> = emptyMap(),
    val flags: Map<String, Boolean> = emptyMap(),
)

@Serializable
internal data class RestoredWidgetChoices(
    val sourceIds: Set<Int> = emptySet(),
    val values: WidgetSettingValues = WidgetSettingValues(),
    val removed: Boolean = false,
    val explicitlyChosen: Boolean = false,
)

@Serializable
internal data class WidgetRestoreState(
    val widgets: Map<Int, RestoredWidgetChoices> = emptyMap(),
    val version: Int = 1,
    val retiredSourceIds: Set<Int> = emptySet(),
    // Older journals cannot distinguish local IDs from remote deletion provenance.
    val retiredRestoredSourceIds: Set<Int>? = null,
)

internal interface WidgetRestoreStateStore {
    fun read(): WidgetRestoreState

    fun write(state: WidgetRestoreState)
}

internal interface WidgetPreferencesStore {
    fun read(): Map<Int, WidgetSettingValues>

    fun write(values: Map<Int, WidgetSettingValues>)
}

/** Orders installation-local recovery state before the preferences Android can replace. */
internal class WidgetRestoreRepair(
    private val journal: WidgetRestoreStateStore,
    private val preferences: WidgetPreferencesStore,
) {
    fun remap(
        oldIds: IntArray,
        newIds: IntArray,
        liveIds: Set<Int> = newIds.toSet(),
        sourceValues: Map<Int, WidgetSettingValues>? = null,
    ) {
        require(oldIds.size == newIds.size && oldIds.all { it > 0 } && newIds.all { it > 0 })
        require(oldIds.distinct().size == oldIds.size && newIds.distinct().size == newIds.size)
        val moves = oldIds.zip(newIds).filter { (_, new) -> new in liveIds }
        if (moves.isEmpty()) {
            reconcile(liveIds)
            return
        }
        val retained = journal.read()
        val snapshot = preferences.read()
        val records = retained.widgets.toMutableMap()
        val removed = retained.widgets.filterValues { it.removed }
        val retired = retained.retiredSourceIds + removed.flatMap { (id, record) -> record.sourceIds + id }
        val retiredRestoredSources =
            (retained.retiredRestoredSourceIds ?: retained.retiredSourceIds) + removed.values.flatMap { it.sourceIds }
        val accepted =
            moves.filter { (old, _) ->
                old !in retired || (old in liveIds && old !in retiredRestoredSources)
            }
        accepted.map { it.first }.filter { it !in newIds && it !in liveIds }.forEach(records::remove)
        accepted.forEach { (old, new) ->
            val previous = retained.widgets[new]
            val previousSourceIds =
                retained.widgets[old]
                    ?.takeIf { old !in liveIds }
                    ?.sourceIds
                    .orEmpty()
            records[new] =
                if (previous != null && !previous.removed && (previous.explicitlyChosen || old in previous.sourceIds)) {
                    previous.copy(sourceIds = previous.sourceIds + old + previousSourceIds)
                } else {
                    RestoredWidgetChoices(
                        sourceIds = setOf(old) + previousSourceIds,
                        values = (sourceValues ?: snapshot)[old] ?: WidgetSettingValues(),
                    )
                }
        }
        val state = retained.copy(widgets = records, retiredSourceIds = retired, retiredRestoredSourceIds = retiredRestoredSources)
        journal.write(state)
        preferences.write(reconciled(state, snapshot, liveIds))
    }

    fun reconcile(liveIds: Set<Int>) {
        val state = journal.read()
        if (state.widgets.isEmpty() && state.retiredSourceIds.isEmpty()) return
        val snapshot = preferences.read()
        val updated = reconciled(state, snapshot, liveIds)
        if (updated != snapshot) preferences.write(updated)
        val retained = state.widgets.filterKeys { it in liveIds }
        val removed = state.widgets.filter { (id, record) -> id !in liveIds && record.removed }
        val retired = state.retiredSourceIds + removed.flatMap { (id, record) -> record.sourceIds + id }
        val retiredRestoredSources =
            (state.retiredRestoredSourceIds ?: state.retiredSourceIds) + removed.values.flatMap { it.sourceIds }
        if (retained != state.widgets) {
            journal.write(state.copy(widgets = retained, retiredSourceIds = retired, retiredRestoredSourceIds = retiredRestoredSources))
        }
    }

    fun update(
        id: Int,
        explicitlyChosen: Boolean = true,
        canCommit: () -> Boolean = { true },
        change: (WidgetSettingValues) -> WidgetSettingValues,
    ): Boolean {
        require(id > 0)
        val state = journal.read()
        val snapshot = preferences.read()
        val previous = state.widgets[id]
        val values = change(previous?.takeUnless { it.removed }?.values ?: snapshot[id] ?: WidgetSettingValues())
        val record =
            RestoredWidgetChoices(
                sourceIds = previous?.sourceIds.orEmpty(),
                values = values,
                explicitlyChosen = explicitlyChosen || previous?.explicitlyChosen == true,
            )
        if (!canCommit()) return false
        journal.write(retainRemovalProvenance(state, id, previous).copy(widgets = state.widgets + (id to record)))
        preferences.write(snapshot + (id to values))
        return true
    }

    fun remove(id: Int) {
        val state = journal.read()
        val record = (state.widgets[id] ?: RestoredWidgetChoices()).copy(values = WidgetSettingValues(), removed = true)
        journal.write(retainRemovalProvenance(state, id, record).copy(widgets = state.widgets + (id to record)))
        preferences.write(preferences.read() - id)
    }

    private fun retainRemovalProvenance(
        state: WidgetRestoreState,
        id: Int,
        record: RestoredWidgetChoices?,
    ): WidgetRestoreState {
        if (record?.removed != true) return state
        return state.copy(
            retiredSourceIds = state.retiredSourceIds + record.sourceIds + id,
            retiredRestoredSourceIds = (state.retiredRestoredSourceIds ?: state.retiredSourceIds) + record.sourceIds,
        )
    }

    private fun reconciled(
        state: WidgetRestoreState,
        snapshot: Map<Int, WidgetSettingValues>,
        liveIds: Set<Int>,
    ): Map<Int, WidgetSettingValues> {
        val updated = snapshot.toMutableMap()
        state.retiredSourceIds.filterNot { it in liveIds }.forEach(updated::remove)
        val applicable = state.widgets.filter { (id, record) -> id in liveIds || record.removed }
        applicable.values
            .flatMap { it.sourceIds }
            .filterNot { it in liveIds }
            .forEach(updated::remove)
        applicable.forEach { (id, record) ->
            if (record.removed) {
                updated.remove(id)
            } else {
                updated[id] = record.values
            }
        }
        return updated
    }
}
