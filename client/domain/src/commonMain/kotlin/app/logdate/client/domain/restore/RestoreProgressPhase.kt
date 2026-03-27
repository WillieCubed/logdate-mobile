package app.logdate.client.domain.restore

enum class RestoreProgressPhase {
    RESTORING_JOURNALS,
    RESTORING_NOTES,
    RESTORING_LINKS,
    RESTORING_DRAFTS,
    RESTORING_PROFILE,
    RESTORING_PLACES,
    RESTORING_LOCATION_HISTORY,
}
