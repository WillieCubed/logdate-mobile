package app.logdate.client.people

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsPickerSessionContract
import androidx.activity.result.contract.ActivityResultContract

private const val SELECTED_CONTACTS_PICKER_MIN_API = 37
private const val DEFAULT_SELECTION_LIMIT = 50

private val REQUESTED_CONTACT_DATA_FIELDS =
    arrayListOf(
        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
    )

class SelectedContactsPickerContract : ActivityResultContract<Unit, String?>() {
    override fun createIntent(
        context: Context,
        input: Unit,
    ): Intent =
        if (Build.VERSION.SDK_INT >= SELECTED_CONTACTS_PICKER_MIN_API) {
            Intent(ContactsPickerSessionContract.ACTION_PICK_CONTACTS).apply {
                putStringArrayListExtra(
                    ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS,
                    REQUESTED_CONTACT_DATA_FIELDS,
                )
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                putExtra(ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_SELECTION_LIMIT, DEFAULT_SELECTION_LIMIT)
            }
        } else {
            Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): String? {
        if (resultCode != Activity.RESULT_OK) {
            return null
        }

        return intent?.dataString
    }
}
