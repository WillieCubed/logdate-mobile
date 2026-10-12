package app.logdate.wear.sync.v2.delivery

sealed interface NoteSchedule {
    data object Outbox : NoteSchedule

    data class Ledger(
        val dueAt: Long?,
    ) : NoteSchedule
}
