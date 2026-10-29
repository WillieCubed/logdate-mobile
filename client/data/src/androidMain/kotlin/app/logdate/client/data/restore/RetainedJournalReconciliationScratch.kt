package app.logdate.client.data.restore

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

internal fun clearAbandonedDatabaseCopies(
    root: File,
    noBackupFilesDir: File,
) {
    val expectedParent = noBackupFilesDir.canonicalFile
    check(root.absoluteFile.parentFile?.canonicalFile == expectedParent && root.name == "retained-journal-merge-scratch")
    if (root.exists()) {
        check(Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root.toPath()))
        root
            .listFiles()
            .orEmpty()
            .filter { candidate ->
                (
                    candidate.name.startsWith("retained-db-") ||
                        candidate.name.startsWith("preferences-read-")
                ) &&
                    Files.isDirectory(candidate.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                    !Files.isSymbolicLink(candidate.toPath())
            }.forEach { candidate -> check(candidate.deleteRecursively()) }
    } else {
        check(root.mkdirs())
    }
}

internal fun clearAbandonedDraftCopies(
    root: File,
    noBackupFilesDir: File,
) {
    check(root.absoluteFile.parentFile?.canonicalFile == noBackupFilesDir.canonicalFile && root.name == "retained-draft-reads")
    if (!root.exists()) return
    check(Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root.toPath()))
    root
        .listFiles()
        .orEmpty()
        .filter { candidate ->
            candidate.name.startsWith("draft-read-") &&
                Files.isDirectory(candidate.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(candidate.toPath())
        }.forEach { candidate -> check(candidate.deleteRecursively()) }
}

internal fun databaseKeyFiles(files: Map<String, File>): List<File> =
    files
        .filter { (path, _) ->
            path.substringAfterLast('/') in setOf("passphrase_backup", "working_passphrase_backup") &&
                !path.endsWith(".part") &&
                (path.startsWith("db_recovery/") || path.startsWith("files/db_recovery/"))
        }.values
        .toList()
