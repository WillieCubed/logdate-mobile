@file:OptIn(ExperimentalForeignApi::class)

package app.logdate.client.data.people

import app.logdate.client.repository.knowledge.DeviceContact
import app.logdate.client.repository.knowledge.DeviceContactsReader
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Contacts.CNContact
import platform.Contacts.CNContactFamilyNameKey
import platform.Contacts.CNContactFetchRequest
import platform.Contacts.CNContactGivenNameKey
import platform.Contacts.CNContactNicknameKey
import platform.Contacts.CNContactStore

/**
 * Reads contacts from the user's iOS address book using the Contacts framework. Permission
 * is requested elsewhere (see IosPermissionManager); this reader assumes the caller has
 * already verified the user granted access.
 */
class IosDeviceContactsReader : DeviceContactsReader {
    private val store = CNContactStore()

    override fun supportsSelectedContactsPicker(): Boolean = false

    override suspend fun readAllContacts(): List<DeviceContact> =
        withContext(Dispatchers.Default) {
            val keys: List<String> =
                listOf(
                    CNContactGivenNameKey,
                    CNContactFamilyNameKey,
                    CNContactNicknameKey,
                )
            val request = CNContactFetchRequest(keysToFetch = keys)
            val collected = mutableListOf<DeviceContact>()
            store.enumerateContactsWithFetchRequest(
                fetchRequest = request,
                error = null,
            ) { contact, _ ->
                if (contact != null) {
                    contact.toDeviceContact()?.let(collected::add)
                }
            }
            collected
        }

    override suspend fun readSelectedContacts(sessionUri: String): List<DeviceContact> = emptyList()

    private fun CNContact.toDeviceContact(): DeviceContact? {
        val displayName =
            listOfNotNull(
                givenName.takeIf { it.isNotBlank() },
                familyName.takeIf { it.isNotBlank() },
            ).joinToString(" ")
        if (displayName.isBlank()) return null
        val nick = nickname.takeIf { it.isNotBlank() }
        return DeviceContact(
            lookupKey = identifier,
            displayName = displayName,
            photoUri = null,
            aliases = listOfNotNull(nick),
        )
    }
}
