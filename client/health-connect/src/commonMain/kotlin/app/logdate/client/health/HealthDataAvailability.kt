package app.logdate.client.health

/**
 * Availability states for platform health data integrations.
 */
enum class HealthDataAvailability {
    AVAILABLE,
    PROVIDER_UPDATE_REQUIRED,
    NOT_AVAILABLE,
}
