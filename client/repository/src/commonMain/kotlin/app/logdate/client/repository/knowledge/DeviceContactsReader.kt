package app.logdate.client.repository.knowledge

data class DeviceContact(
    val lookupKey: String,
    val displayName: String,
    val photoUri: String? = null,
    val aliases: List<String> = emptyList(),
)

interface DeviceContactsReader {
    fun supportsSelectedContactsPicker(): Boolean

    suspend fun readAllContacts(): List<DeviceContact>

    suspend fun readSelectedContacts(sessionUri: String): List<DeviceContact>
}
