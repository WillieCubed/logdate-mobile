package app.logdate.client.device.restore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.logdate.client.datastore.createAndroidMainDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

internal fun nativePrivacyRepairDelivery(
    context: Context,
    inspect: (Map<String, File>) -> Unit,
): NativePrivacyRepairDelivery {
    val preferences = createAndroidMainDataStore(context)
    return NativePrivacyRepairDelivery(
        context,
        preferences,
        inspect,
        { readNativePrivacyRepairPreferences(context, it) },
        { captureNativePrivacyRepairFiles(context) },
        { captureNativePrivacyRepairInventory(context) },
        accountSource = { accountNativePrivacyRepairSource(context, preferences, it) },
    )
}

/** Caller holds the native delivery lock. Startup can resume only an already authorized completion. */
suspend fun resumeNativePrivacyRepairUnderDeliveryLock(context: Context) {
    val custody = NativeJournalReplayCustody(context)
    val preferences = createAndroidMainDataStore(context)
    try {
        NativePrivacyRepairIntent(
            context,
            preferences,
            {
                id,
                hash,
                ->
                readNativePrivacyRepairCohortPreferences(custody, id, hash, captureNativePrivacyRepairInventory(context)) {
                    readNativePrivacyRepairPreferences(context, it)
                }
            },
            { captureNativePrivacyRepairInventory(context) },
            accountSource = { accountNativePrivacyRepairSource(context, preferences, it) },
        ).resume()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        throw NativeRestorePrivacyMetadataRequiredException(error)
    }
}

/** Decode a private copy, never registering DataStore against the retained source or primary file. */
internal suspend fun readNativePrivacyRepairPreferences(
    context: Context,
    source: File,
): Preferences {
    check(Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS) && source.length() in 1..MAX_PREFERENCES_BYTES)
    val root = context.noBackupFilesDir.toPath()
    check(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
    val directory = Files.createTempDirectory(root, "privacy-policy-read-").toFile()
    val copy = File(directory, "policy.preferences_pb")
    val job = SupervisorJob()
    return try {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        Files.newInputStream(source.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
            copy.outputStream().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(count > 0)
                    size += count
                    check(size <= MAX_PREFERENCES_BYTES)
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
        }
        check(size > 0)
        val expected = digest.digest()
        val snapshot =
            PreferenceDataStoreFactory
                .create(scope = CoroutineScope(job + Dispatchers.IO)) { copy }
                .data
                .first()
                .toPreferences()
        check(Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS) && source.length() == size)
        val current = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(source.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(8192)
            var checked = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(count > 0)
                checked += count
                check(checked <= MAX_PREFERENCES_BYTES)
                current.update(buffer, 0, count)
            }
            check(checked == size)
        }
        check(MessageDigest.isEqual(expected, current.digest())) { "Incoming privacy preferences changed during inspection" }
        snapshot
    } finally {
        withContext(NonCancellable) {
            job.cancelAndJoin()
            check(directory.deleteRecursively()) { "Could not remove isolated privacy preference copy" }
        }
    }
}

private const val MAX_PREFERENCES_BYTES = 4L * 1024 * 1024
