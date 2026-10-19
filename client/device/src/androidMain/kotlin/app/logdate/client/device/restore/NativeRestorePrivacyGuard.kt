package app.logdate.client.device.restore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.first
import java.io.File

/** Shared by database consumers, including widgets that run before any Activity. */
class NativeRestorePrivacyGuard(
    private val context: Context,
    private val preferences: DataStore<Preferences>,
) {
    suspend fun verifyBeforeContent(hasRetainedContent: suspend () -> Boolean) {
        val detector = PostRestoreDetector(context)
        if (!detector.shouldValidatePrivacyMetadata()) return
        val snapshot = preferences.data.first()
        val missingDelivery = detector.hasMissingNativePrivacyPayload()
        NativeRestorePrivacyPolicy.verifyBeforeContent(if (missingDelivery) emptyPreferences() else snapshot) {
            hasRetainedContent() ||
                File(context.filesDir, "datastore/entry_drafts.preferences_pb").exists() ||
                snapshot.asMap().any { (key, value) -> key.name.contains("draft") && value.toString().isNotBlank() }
        }
        if (missingDelivery) detector.acknowledgeEmptyNativePrivacyPayload()
    }
}

internal fun canEstablishNativePrivacyDefaults(
    context: Context,
    preferences: Preferences,
): Boolean =
    !PostRestoreDetector(context).shouldValidatePrivacyMetadata() ||
        (
            !context.getDatabasePath("logdate").exists() &&
                preferences.asMap().none { (key, value) -> key.name.contains("draft") && value.toString().isNotBlank() } &&
                listOf("media", "user_media", "audio_notes", "camera-captures", "db_recovery", "identity_recovery", "datastore").none {
                    File(context.filesDir, it).exists()
                }
        )
