package app.logdate.core.data.journals

interface JournalUserDataRepository {
    /**
     * Changes the favorited status of a journal.
     *
     * @param journalId The ID of the journal to change the favorited status of.
     * @param isFavorite Whether the journal should be favorited or not.
     */
    suspend fun changeFavoritedStatus(journalId: String, isFavorite: Boolean = true)

    /**
     * Marks a journal as archived.
     *
     * @param journalId The ID of the journal to archive.
     * @param isArchived Whether the journal should be archived or not, defaults to true.
     */
    suspend fun changeArchiveStatus(journalId: String, isArchived: Boolean = true)
}