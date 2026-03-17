package app.logdate.wear.presentation.common

/**
 * Feedback shown to the user after saving an entry,
 * indicating where the data went.
 */
enum class SaveFeedback {
    /**
     * Phone is connected; entry is being synced.
     */
    SYNCING_TO_PHONE,

    /**
     * Phone is not connected; entry saved locally on the watch.
     */
    SAVED_LOCALLY,
}
