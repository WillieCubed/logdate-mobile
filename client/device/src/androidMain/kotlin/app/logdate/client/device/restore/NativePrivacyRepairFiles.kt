package app.logdate.client.device.restore

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/** Privacy-only repair requires the same retained journal, sidecar membership, keys and drafts. */
internal fun verifyNativePrivacyRepairFiles(
    current: Map<String, File>,
    incoming: Map<String, File>,
    inspectIsolatedCopy: (Map<String, File>) -> Unit,
): Map<String, String> {
    val before = privacyRepairCriticalHashes(current)
    check(privacyRepairCriticalHashes(incoming) == before) { "Incoming native journal inputs do not match protected local bytes" }
    inspectIsolatedCopy(incoming.filterKeys { it != SHM_PATH })
    check(privacyRepairCriticalHashes(current) == before) { "Protected local journal inputs changed during inspection" }
    check(privacyRepairCriticalHashes(incoming) == before) { "Incoming journal inputs changed during inspection" }
    return before
}

internal fun captureNativePrivacyRepairFiles(context: Context): Map<String, File> =
    buildMap {
        val databaseParent = checkNotNull(context.getDatabasePath("logdate").parentFile)
        if (privacyRepairDirectoryExists(databaseParent)) {
            for (name in DATABASE_NAMES) addPrivacyRepairFile(this, "database/$name", context.getDatabasePath(name))
        }
        val files = context.filesDir
        if (privacyRepairDirectoryExists(files)) {
            for (relative in RECOVERY_ROOTS) {
                val directory = privacyRepairNestedDirectory(files, relative) ?: continue
                collectPrivacyRepairDirectory(this, directory, "files/$relative")
            }
            val drafts = privacyRepairNestedDirectory(files, "datastore")
            if (drafts != null) addPrivacyRepairFile(this, ENTRY_DRAFT_PATH, File(drafts, "entry_drafts.preferences_pb"))
        }
    }

/** SHM is a cache; this inventory becomes repair authority only after isolated database validation. */
internal fun captureNativePrivacyRepairInventory(context: Context): Map<String, String> =
    buildMap {
        captureNativePrivacyRepairFiles(context).forEach { (path, file) ->
            requirePrivacyRepairRegularFile(file)
            if (path != SHM_PATH) put(path, privacyRepairFileSha256(file))
        }
        if (privacyRepairDirectoryExists(context.noBackupFilesDir)) {
            val installation = File(context.noBackupFilesDir, ".logdate_installation_id")
            if (privacyRepairAttributes(installation) != null) {
                put("local/.logdate_installation_id", privacyRepairFileSha256(installation))
            }
        }
    }

internal fun isNativePrivacyRepairProtectedPath(path: String): Boolean =
    path.startsWith("database/") ||
        RECOVERY_ROOTS.any { path.startsWith("files/$it/") } ||
        path == ENTRY_DRAFT_PATH

private fun privacyRepairCriticalHashes(files: Map<String, File>): Map<String, String> {
    check(files.keys.none { it.startsWith("database/") } || "database/logdate" in files) {
        "Retained journal sidecars cannot establish a missing database"
    }
    return buildMap {
        files.forEach { (path, file) ->
            check(isNativePrivacyRepairProtectedPath(path) && path.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
                "Unexpected protected native journal path"
            }
            requirePrivacyRepairRegularFile(file)
            if (path != SHM_PATH) put(path, privacyRepairFileSha256(file))
        }
    }
}

private fun addPrivacyRepairFile(
    destination: MutableMap<String, File>,
    path: String,
    file: File,
) {
    val attributes = privacyRepairAttributes(file) ?: return
    check(attributes.isRegularFile) { "Protected native journal input must be a regular file" }
    destination[path] = file
}

private fun privacyRepairNestedDirectory(
    root: File,
    relative: String,
): File? {
    var directory = root
    for (part in relative.split('/')) {
        directory = File(directory, part)
        if (!privacyRepairDirectoryExists(directory)) return null
    }
    return directory
}

private fun collectPrivacyRepairDirectory(
    destination: MutableMap<String, File>,
    directory: File,
    prefix: String,
) {
    check(privacyRepairDirectoryExists(directory)) { "Protected recovery directory disappeared" }
    Files.newDirectoryStream(directory.toPath()).use { children ->
        for (child in children) {
            val file = child.toFile()
            val attributes = checkNotNull(privacyRepairAttributes(file)) { "Protected recovery input disappeared" }
            val path = "$prefix/${file.name}"
            when {
                attributes.isDirectory -> collectPrivacyRepairDirectory(destination, file, path)
                attributes.isRegularFile -> destination[path] = file
                else -> error("Protected recovery input must not be a symlink or special file")
            }
        }
    }
}

private fun privacyRepairDirectoryExists(file: File): Boolean {
    val attributes = privacyRepairAttributes(file) ?: return false
    check(attributes.isDirectory) { "Protected recovery root must be a directory without symlinks" }
    return true
}

private fun requirePrivacyRepairRegularFile(file: File) {
    check(privacyRepairAttributes(file)?.isRegularFile == true) { "Protected native journal input must be a regular file" }
}

private fun privacyRepairAttributes(file: File): BasicFileAttributes? =
    try {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (_: NoSuchFileException) {
        null
    }

private fun privacyRepairFileSha256(file: File): String {
    requirePrivacyRepairRegularFile(file)
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            check(count > 0) { "Protected journal read did not advance" }
            digest.update(buffer, 0, count)
        }
    }
    requirePrivacyRepairRegularFile(file)
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private const val SHM_PATH = "database/logdate-shm"
private const val ENTRY_DRAFT_PATH = "files/datastore/entry_drafts.preferences_pb"
private val DATABASE_NAMES = listOf("logdate", "logdate-wal", "logdate-shm", "logdate-journal")
private val RECOVERY_ROOTS = listOf("db_recovery", "identity_recovery", "archive_recovery/records")
