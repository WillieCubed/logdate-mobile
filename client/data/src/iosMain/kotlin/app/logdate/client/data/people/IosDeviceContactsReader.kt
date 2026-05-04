@file:OptIn(ExperimentalForeignApi::class)

package app.logdate.client.data.people

import app.logdate.client.repository.knowledge.DeviceContact
import app.logdate.client.repository.knowledge.DeviceContactsReader
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Contacts.CNContact
import platform.Contacts.CNContactFamilyNameKey
import platform.Contacts.CNContactFetchRequest
import platform.Contacts.CNContactGivenNameKey
import platform.Contacts.CNContactNicknameKey
import platform.Contacts.CNContactStore
import platform.Foundation.NSError

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
            memScoped {
                val errorVar: ObjCObjectVar<NSError?> = alloc()
                store.enumerateContactsWithFetchRequest(
                    fetchRequest = request,
                    error = errorVar.ptr,
                ) { contact, _ ->
                    if (contact != null) {
                        contact.toDeviceContact()?.let(collected::add)
                    }
                }
                val error = errorVar.value
                if (error != null) {
                    Napier.w("CNContactStore enumeration failed: ${error.localizedDescription}")
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
