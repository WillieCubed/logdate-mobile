package app.logdate.client.device.restore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** Called only by an acknowledged durable intent whose isolated database proof already succeeded. */
internal suspend fun accountNativePrivacyRepairSource(
    context: Context,
    preferences: DataStore<Preferences>,
    acknowledgement: NativePrivacyRepairAcknowledgement,
): Boolean {
    requireAccountingState(context, preferences, acknowledgement)
    val custody = NativeJournalReplayCustody(context)
    val payloads = custody.verifiedCohort(acknowledgement.cohortId, acknowledgement.manifestSha256)
    check(payloads.all { it.complete })
    val selected =
        verifiedNativePrivacyRepairCohort(
            custody,
            acknowledgement.cohortId,
            acknowledgement.manifestSha256,
            acknowledgement.protectedInventory,
        )
    val preferencesHash = retainedJournalChecksum(checkNotNull(selected["files/logdate.preferences_pb"]))
    // SQLCipher inspection deliberately omitted SHM; no user data can be admitted from that cache.
    if (!nativePrivacyRepairCohortCovered(
            payloads.filterNot {
                it.path == "database/logdate-shm"
            },
            acknowledgement.protectedInventory,
            preferencesHash,
        )
    ) {
        return false
    }
    custody.verifiedCohort(acknowledgement.cohortId, acknowledgement.manifestSha256)
    requireAccountingState(context, preferences, acknowledgement)
    currentCoroutineContext().ensureActive()
    val protectedDigest =
        MessageDigest
            .getInstance("SHA-256")
            .digest(Json.encodeToString(acknowledgement.protectedInventory.toSortedMap().toMap()).encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
    writeNativePrivacyRepairAccounting(
        File(context.noBackupFilesDir, "native-journal-replay-custody/${acknowledgement.cohortId}"),
        NativePrivacyRepairAccounting(
            generation = acknowledgement.identity.generation,
            accountDeliveryId = acknowledgement.identity.accountDeliveryId,
            cohortId = acknowledgement.cohortId,
            manifestSha256 = acknowledgement.manifestSha256,
            protectedInventorySha256 = protectedDigest,
            acceptedPreferencesSha256 = acknowledgement.acceptedPreferencesSha256,
        ),
    )
    return true
}

private suspend fun requireAccountingState(
    context: Context,
    preferences: DataStore<Preferences>,
    acknowledgement: NativePrivacyRepairAcknowledgement,
) {
    currentCoroutineContext().ensureActive()
    val current = checkNotNull(PostRestoreDetector(context).capturePrivacyRepairIdentity())
    check(current.privacyReceived && nativePrivacyRepairIdentityMayAdvance(acknowledgement.identity, current))
    check(captureNativePrivacyRepairInventory(context) == acknowledgement.protectedInventory)
    val snapshot = preferences.data.first()
    check(NativeRestorePrivacyPolicy.hasKnownPrivacyMetadata(snapshot))
    check(nativePrivacyPreferenceFingerprint(snapshot) == acknowledgement.acceptedPreferencesSha256)
}
