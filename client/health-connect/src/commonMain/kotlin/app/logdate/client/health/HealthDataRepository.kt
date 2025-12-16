package app.logdate.client.health

/**
 * Repository interface for general health data operations.
 * This handles basic health data service availability and
 * general health data permissions.
 */
interface HealthDataRepository {
    /**
     * Checks if health data services are available on the device.
     * 
     * @return true if health data is available, false otherwise
     */
    suspend fun isHealthDataAvailable(): Boolean
    
    /**
     * Gets a list of available data types from health data services.
     *
     * @return List of available data type identifiers
     */
    suspend fun getAvailableDataTypes(): List<String>
}