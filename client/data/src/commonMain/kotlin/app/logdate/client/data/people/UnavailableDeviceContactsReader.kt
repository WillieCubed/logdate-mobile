package app.logdate.client.data.people

import app.logdate.client.repository.knowledge.DeviceContact
import app.logdate.client.repository.knowledge.DeviceContactsReader

class UnavailableDeviceContactsReader : DeviceContactsReader {
    override fun supportsSelectedContactsPicker(): Boolean = false

    override suspend fun readAllContacts(): List<DeviceContact> = emptyList()

    override suspend fun readSelectedContacts(sessionUri: String): List<DeviceContact> = emptyList()
}
