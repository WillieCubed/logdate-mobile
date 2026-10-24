package app.logdate.client.device.restore

import java.io.File
import kotlin.uuid.Uuid

/** An untransferred local intent can restart copying only before any retained custody exists. */
internal fun retainedJournalHasInterruptedPreparation(
    authorityFile: File,
    retainedDirectory: File,
    acceptedWorkingFile: File,
): Boolean {
    if (!authorityFile.exists()) return false
    check(authorityFile.isFile && authorityFile.length() == 36L) { "Local retention intent is invalid" }
    val token = authorityFile.readText()
    check(runCatching { Uuid.parse(token).toString() == token }.getOrDefault(false)) { "Local retention intent is invalid" }
    check(!acceptedWorkingFile.exists()) { "A missing working session must preserve its retained custody" }
    check(!retainedDirectory.exists() || retainedDirectory.listFiles()?.isEmpty() == true) {
        "Existing retained journal custody requires its original session"
    }
    return true
}

internal fun retainedJournalMatchesLocalSession(
    file: File,
    id: String,
): Boolean = file.isFile && file.length() in 1..128 && file.readText() == id
