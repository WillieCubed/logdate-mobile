package app.logdate.client.domain.timeline

import app.logdate.client.repository.journals.JournalNote
import kotlinx.datetime.Instant

/**
 * Adapter class that allows JournalNote instances to be used with the TimelineConfig's
 * conceptual day grouping logic.
 */
class JournalNoteAdapter(private val note: JournalNote) : JournalEntry {
    override val timestamp: Instant
        get() = note.creationTimestamp
    
    /**
     * Get the underlying JournalNote.
     */
    fun getNote(): JournalNote = note
    
    companion object {
        /**
         * Converts a list of JournalNotes to JournalEntries for use with TimelineConfig.
         */
        fun fromNotes(notes: List<JournalNote>): List<JournalNoteAdapter> {
            return notes.map { JournalNoteAdapter(it) }
        }
        
        /**
         * Extracts the original JournalNotes from a list of JournalNoteAdapters.
         */
        fun toNotes(adapters: List<JournalNoteAdapter>): List<JournalNote> {
            return adapters.map { it.getNote() }
        }
    }
}