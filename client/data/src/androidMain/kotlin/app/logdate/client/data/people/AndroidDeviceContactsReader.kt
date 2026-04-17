package app.logdate.client.data.people

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import app.logdate.client.repository.knowledge.DeviceContact
import app.logdate.client.repository.knowledge.DeviceContactsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SELECTED_CONTACTS_PICKER_MIN_API = 37

private val ALL_CONTACTS_PROJECTION =
    arrayOf(
        ContactsContract.Contacts.LOOKUP_KEY,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        ContactsContract.Contacts.PHOTO_URI,
    )

private val NICKNAME_PROJECTION =
    arrayOf(
        ContactsContract.Data.LOOKUP_KEY,
        ContactsContract.CommonDataKinds.Nickname.NAME,
    )

private val SELECTED_CONTACT_LOOKUP_KEYS =
    arrayOf(
        "lookup_key",
        "contact_lookup_key",
        "contact_id",
        "_id",
        "id",
        "display_name_primary",
        "display_name",
    )

private val SELECTED_CONTACT_NAME_KEYS = arrayOf("display_name_primary", "display_name", "name", "data1")
private val SELECTED_CONTACT_PHOTO_KEYS = arrayOf("photo_uri", "photo_thumb_uri")
private val SELECTED_CONTACT_NICKNAME_KEYS = arrayOf("nickname", "data1")

class AndroidDeviceContactsReader(
    context: Context,
) : DeviceContactsReader {
    private val contentResolver: ContentResolver = context.contentResolver

    override fun supportsSelectedContactsPicker(): Boolean = Build.VERSION.SDK_INT >= SELECTED_CONTACTS_PICKER_MIN_API

    override suspend fun readAllContacts(): List<DeviceContact> =
        withContext(Dispatchers.IO) {
            val aliasesByLookupKey = readNicknamesByLookupKey()
            val contacts = mutableListOf<DeviceContact>()

            val cursor =
                contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    ALL_CONTACTS_PROJECTION,
                    "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} IS NOT NULL",
                    null,
                    "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
                )
            cursor?.use { cursorResult ->
                val lookupKeyColumn = cursorResult.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                val displayNameColumn = cursorResult.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val photoUriColumn = cursorResult.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)

                while (cursorResult.moveToNext()) {
                    val lookupKey = cursorResult.getString(lookupKeyColumn)?.trim().orEmpty()
                    val displayName = cursorResult.getString(displayNameColumn)?.trim().orEmpty()
                    if (lookupKey.isBlank() || displayName.isBlank()) {
                        continue
                    }

                    contacts +=
                        DeviceContact(
                            lookupKey = lookupKey,
                            displayName = displayName,
                            photoUri = normalizeOptionalText(cursorResult.getString(photoUriColumn)),
                            aliases = normalizePersonAliases(aliasesByLookupKey[lookupKey].orEmpty(), displayName),
                        )
                }
            }

            contacts
        }

    override suspend fun readSelectedContacts(sessionUri: String): List<DeviceContact> =
        withContext(Dispatchers.IO) {
            if (sessionUri.isBlank()) {
                return@withContext emptyList()
            }

            val contacts = linkedMapOf<String, MutableParsedContact>()
            val uri = runCatching { Uri.parse(sessionUri) }.getOrNull() ?: return@withContext emptyList()

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val columnNames = cursor.columnNames.orEmpty()
                while (cursor.moveToNext()) {
                    val row =
                        buildMap {
                            columnNames.forEachIndexed { index, name ->
                                put(name, cursor.getString(index))
                            }
                        }

                    val key =
                        row.findValue(*SELECTED_CONTACT_LOOKUP_KEYS)
                    val displayName = row.findValue(*SELECTED_CONTACT_NAME_KEYS)
                    if (key.isNullOrBlank() || displayName.isNullOrBlank()) {
                        continue
                    }

                    val parsed = contacts.getOrPut(key) { MutableParsedContact(lookupKey = key) }
                    parsed.displayName = parsed.displayName ?: displayName
                    parsed.photoUri =
                        parsed.photoUri
                            ?: normalizeOptionalText(row.findValue(*SELECTED_CONTACT_PHOTO_KEYS))

                    val rawNickname = row.findValue(*SELECTED_CONTACT_NICKNAME_KEYS)
                    val nickname =
                        rawNickname?.takeIf { it.isNotBlank() && !it.equals(displayName, ignoreCase = true) }
                    if (nickname != null) {
                        parsed.aliases += nickname
                    }
                }
            }

            contacts.values.mapNotNull { it.toDeviceContact() }
        }

    private fun readNicknamesByLookupKey(): Map<String, List<String>> {
        val nicknames = linkedMapOf<String, MutableSet<String>>()

        val cursor =
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                NICKNAME_PROJECTION,
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE),
                null,
            )
        cursor?.use { cursorResult ->
            val lookupKeyColumn = cursorResult.getColumnIndex(ContactsContract.Data.LOOKUP_KEY)
            val nicknameColumn = cursorResult.getColumnIndex(ContactsContract.CommonDataKinds.Nickname.NAME)

            while (cursorResult.moveToNext()) {
                val lookupKey = cursorResult.getString(lookupKeyColumn)?.trim().orEmpty()
                val nickname = cursorResult.getString(nicknameColumn)?.trim().orEmpty()
                if (lookupKey.isBlank() || nickname.isBlank()) {
                    continue
                }

                nicknames.getOrPut(lookupKey) { linkedSetOf() } += nickname
            }
        }

        return nicknames.mapValues { it.value.toList() }
    }

    private data class MutableParsedContact(
        val lookupKey: String,
        var displayName: String? = null,
        var photoUri: String? = null,
        val aliases: MutableSet<String> = linkedSetOf(),
    ) {
        fun toDeviceContact(): DeviceContact? {
            val resolvedName = displayName?.trim().orEmpty()
            if (resolvedName.isBlank()) {
                return null
            }

            return DeviceContact(
                lookupKey = lookupKey,
                displayName = resolvedName,
                photoUri = normalizeOptionalText(photoUri),
                aliases = normalizePersonAliases(aliases, resolvedName),
            )
        }
    }
}

private fun Map<String, String?>.findValue(vararg keys: String): String? {
    val normalizedKeys = keys.toSet()
    entries.forEach { (key, value) ->
        if (value.isNullOrBlank()) {
            return@forEach
        }

        val normalizedKey = key.lowercase()
        if (normalizedKeys.any { normalizedKey == it || normalizedKey.endsWith(".$it") }) {
            return value
        }
    }

    return null
}
