package app.logdate.client.device.restore

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes
import kotlin.uuid.Uuid

internal fun readNativePrivacyRepairIdentity(directory: File): NativePrivacyRepairIdentity? =
    runCatching {
        val privacy = checkNotNull(optionalRepairMarker(directory, PRIVACY)).split(':')
        check(privacy.size in 2..3 && privacy[0] in setOf("pending", "received"))
        val generation = repairIdentifier(privacy[1])
        check(privacy.size == 2 || privacy[2] == "prefs-v1")
        val account = repairIdentifier(checkNotNull(optionalRepairMarker(directory, ".logdate_account_admission_$generation")))
        val initialized = optionalRepairMarker(directory, INITIALIZED)?.also { check(it == "initialized") } != null
        val content = repairContentIdentity(directory, account, initialized)
        NativePrivacyRepairIdentity(
            generation,
            account,
            content.first,
            content.second,
            privacy.size == 3,
            privacy[0] == "received",
            initialized,
            repairAccountState(directory, account),
        )
    }.getOrNull()

internal fun acknowledgeNativePrivacyRepair(
    directory: File,
    expected: NativePrivacyRepairIdentity,
): Boolean {
    val current = readNativePrivacyRepairIdentity(directory) ?: return false
    if (!nativePrivacyRepairIdentityMayAdvance(expected, current)) return false
    if (!current.privacyReceived) {
        retainedJournalWriteAtomic(
            File(directory, PRIVACY),
            "received:${current.generation}${if (current.retainedPreferences) ":prefs-v1" else ""}",
        )
    }
    return true
}

internal fun nativePrivacyRepairIdentityMayAdvance(
    expected: NativePrivacyRepairIdentity,
    current: NativePrivacyRepairIdentity,
): Boolean =
    current.copy(accountState = expected.accountState, privacyReceived = expected.privacyReceived) == expected &&
        accountResolutionMayAdvance(expected.accountState, current.accountState) &&
        (!expected.privacyReceived || current.privacyReceived)

private fun repairContentIdentity(
    directory: File,
    account: String,
    initialized: Boolean,
): Pair<NativePrivacyRepairContentState, String?> {
    val content = optionalRepairMarker(directory, CONTENT)
    if (content == null) {
        check(initialized)
        val fingerprints = checkNotNull(optionalRepairMarker(directory, FINGERPRINTS, 512)).split('\n')
        check(fingerprints.isNotEmpty() && fingerprints.all { it.matches(Regex("[a-f0-9]{64}")) })
        return NativePrivacyRepairContentState.ACKNOWLEDGED to null
    }
    val parts = content.split(':')
    check(parts.size == 2 && repairIdentifier(parts[1]) == account)
    val state =
        when (parts[0]) {
            "content-present" -> NativePrivacyRepairContentState.PRESENT
            "content-missing" -> NativePrivacyRepairContentState.MISSING
            "content-complete" -> NativePrivacyRepairContentState.COMPLETE
            else -> error("Native delivery has not completed")
        }
    return state to parts[1]
}

private fun repairAccountState(
    directory: File,
    account: String,
): NativePrivacyRepairAccountState {
    val pending = optionalRepairMarker(directory, PENDING_ACCOUNT)?.let(::repairIdentifier)
    val resolved = optionalRepairMarker(directory, RESOLVED_ACCOUNT)?.let(::repairIdentifier)
    return when {
        pending == account && resolved == null -> NativePrivacyRepairAccountState.PENDING
        pending == account && resolved == account -> NativePrivacyRepairAccountState.ACKNOWLEDGEMENT_WRITTEN
        pending == null && resolved == account -> NativePrivacyRepairAccountState.RESOLVED
        else -> error("Native account delivery changed")
    }
}

private fun accountResolutionMayAdvance(
    expected: NativePrivacyRepairAccountState,
    current: NativePrivacyRepairAccountState,
): Boolean =
    when (expected) {
        NativePrivacyRepairAccountState.PENDING -> true
        NativePrivacyRepairAccountState.ACKNOWLEDGEMENT_WRITTEN -> current != NativePrivacyRepairAccountState.PENDING
        NativePrivacyRepairAccountState.RESOLVED -> current == NativePrivacyRepairAccountState.RESOLVED
    }

private fun optionalRepairMarker(
    directory: File,
    name: String,
    maximum: Int = 128,
): String? {
    val file = File(directory, name)
    return try {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        retainedJournalReadBounded(file, maximum)
    } catch (_: NoSuchFileException) {
        null
    }
}

private fun repairIdentifier(value: String): String {
    check(Uuid.parse(value).toString() == value)
    return value
}

private const val PRIVACY = ".logdate_native_restore_privacy_received"
private const val CONTENT = ".logdate_native_restore_pending"
private const val INITIALIZED = ".logdate_device_initialized"
private const val PENDING_ACCOUNT = ".logdate_account_restore_pending"
private const val RESOLVED_ACCOUNT = ".logdate_account_restore_resolved"
private const val FINGERPRINTS = ".logdate_native_restore_fingerprints"
