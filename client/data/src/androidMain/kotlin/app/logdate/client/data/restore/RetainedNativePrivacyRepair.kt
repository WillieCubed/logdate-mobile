package app.logdate.client.data.restore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.database.NativeJournalSnapshotScope
import app.logdate.client.database.encryption.PassphraseBackupStore
import app.logdate.client.database.requirePortableCloudBackupKey
import app.logdate.client.database.verifyNativeJournalDatabaseSnapshot
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.client.device.restore.NativeJournalReplayCustody
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.RetainedJournalWorkspace
import app.logdate.client.device.restore.retainedJournalPassphraseFile
import app.logdate.client.device.restore.retainedJournalWorkspace
import app.logdate.client.device.restore.verifiedNativeJournalSources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.uuid.Uuid

/** Caller holds the native delivery gate; SQLCipher verification never opens the application graph. */
internal class RetainedNativePrivacyRepair(
    private val context: Context,
    private val preferences: DataStore<Preferences> = createAndroidMainDataStore(context),
    private val verifyExisting: (File, File) -> Unit = ::requirePortableCloudBackupKey,
    private val verifySource: (File, ByteArray) -> NativeJournalSnapshotScope = ::verifyNativeJournalDatabaseSnapshot,
) {
    private val detector = PostRestoreDetector(context)

    suspend fun repairUnderDeliveryLock(): Boolean =
        try {
            attemptRepair()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }

    private suspend fun attemptRepair(): Boolean {
        val generation = receivedGeneration() ?: return false
        val current = preferences.data.first()
        val scope = unknownPolicyScope(current) ?: return false
        val keyFile = retainedJournalPassphraseFile(context)
        val keyHash = committedKeyHash(keyFile)
        val key = PassphraseBackupStore(context) { keyFile }.read() ?: return false
        try {
            check(key.size == 32 && committedKeyHash(keyFile) == keyHash)
            check(Files.isRegularFile(context.getDatabasePath("logdate").toPath(), LinkOption.NOFOLLOW_LINKS))
            verifyExisting(context.getDatabasePath("logdate"), keyFile)
            val scratch = File(context.noBackupFilesDir, "native-privacy-repair-${Uuid.random()}")
            check(scratch.mkdir())
            try {
                val candidates = verifiedSources().values.mapNotNull { authenticate(it, scope, key, scratch) }
                val candidate = candidates.distinctBy(Candidate::receiptHash).singleOrNull() ?: return false
                return commit(candidate, generation, scope, keyFile, keyHash)
            } finally {
                check(scratch.deleteRecursively())
            }
        } finally {
            key.fill(0)
        }
    }

    private fun verifiedSources(): Map<String, Map<String, File>> {
        val workspace = retainedJournalWorkspace(context)
        val working = workspace.current()?.phase == RetainedJournalWorkspace.Phase.WORKING
        val incoming = if (working) workspace.incomingPayloads().values.flatten() else emptyList()
        return verifiedNativeJournalSources(incoming + NativeJournalReplayCustody(context).sealedPayloads())
    }

    private suspend fun authenticate(
        files: Map<String, File>,
        current: RetainedJournalSourceScope,
        key: ByteArray,
        scratch: File,
    ): Candidate? =
        try {
            val hashes = files.mapValues { nativeSnapshotChecksum(it.value) }
            val sourcePreferences = files.getValue("files/logdate.preferences_pb")
            val sourceScope =
                readRetainedJournalSourceScope(
                    sourcePreferences,
                    files.getValue("source_scope.json"),
                    scratch,
                    files.filterKeys { it.startsWith("database/") },
                ) ?: return null
            val snapshot = readRetainedPreferencesSnapshot(sourcePreferences, scratch) ?: return null
            val explicitScope = RetainedJournalSourceScope.from(snapshot) ?: return null
            if (!sourceScope.matchesCurrent(current) || sourceScope != explicitScope) return null
            val onboarded = snapshot.asMap()[ONBOARDED] as? Boolean ?: return null
            val security = snapshot.asMap()[SECURITY] as? String ?: return null
            if (security !in setOf("NONE", "BIOMETRIC")) return null
            val expected =
                NativeJournalSnapshotScope(
                    checkNotNull(sourceScope.ownerId),
                    sourceScope.bound,
                    sourceScope.backendOrigin,
                    hashes.getValue("files/logdate.preferences_pb"),
                )
            val verificationKey = key.copyOf()
            val encrypted =
                try {
                    verifySource(files.getValue("database/logdate"), verificationKey)
                } finally {
                    verificationKey.fill(0)
                }
            if (encrypted != expected || !unchanged(files, hashes)) return null
            Candidate(files, hashes, onboarded, security, hashes.getValue("source_scope.json"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    private suspend fun commit(
        candidate: Candidate,
        generation: String,
        scope: RetainedJournalSourceScope,
        keyFile: File,
        keyHash: String,
    ): Boolean {
        var repaired = false
        preferences.updateData { current ->
            repaired = false
            val live = unknownPolicyScope(current)
            if (receivedGeneration() != generation || live == null || !scope.matchesCurrent(live) ||
                retainedJournalPassphraseFile(context).canonicalFile != keyFile.canonicalFile ||
                committedKeyHash(keyFile) != keyHash || !unchanged(candidate.files, candidate.hashes)
            ) {
                current
            } else {
                repaired = true
                current.toMutablePreferences().apply {
                    this[ONBOARDED] = candidate.onboarded
                    this[SECURITY] = candidate.security
                }
            }
        }
        return repaired
    }

    private fun receivedGeneration(): String? =
        detector.nativeRestoreSessionId()?.takeIf {
            detector.hasCompletedNativeRestoreDelivery() && !detector.hasMissingNativePrivacyPayload()
        }

    private fun unknownPolicyScope(current: Preferences): RetainedJournalSourceScope? {
        if (current.asMap().containsKey(ONBOARDED) || current.asMap().containsKey(SECURITY)) return null
        return RetainedJournalSourceScope.from(current)?.takeIf { !it.ownerId.isNullOrBlank() }
    }

    private fun committedKeyHash(file: File): String {
        check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) && file.length() in 1..128)
        return nativeSnapshotChecksum(file)
    }

    private fun unchanged(
        files: Map<String, File>,
        hashes: Map<String, String>,
    ): Boolean = files.mapValues { nativeSnapshotChecksum(it.value) } == hashes

    private data class Candidate(
        val files: Map<String, File>,
        val hashes: Map<String, String>,
        val onboarded: Boolean,
        val security: String,
        val receiptHash: String,
    )

    companion object {
        private val ONBOARDED = booleanPreferencesKey("is_onboarded")
        private val SECURITY = stringPreferencesKey("security_level")
    }
}
