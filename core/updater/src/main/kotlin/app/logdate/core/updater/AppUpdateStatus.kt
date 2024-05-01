package app.logdate.core.updater

enum class AppUpdateStatus {
    AVAILABLE,
    UNAVAILABLE,
    IN_PROGRESS,
    FAILED,
    CANCELED,

    /**
     * The update status is unknown.
     */
    UNKNOWN,
}