package app.logdate.client.device.restore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.shared.config.DefaultLogDateConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** The active session is portable; retained snapshots travel only through direct device transfer. */
fun retainedJournalWorkspace(context: Context): RetainedJournalWorkspace {
    val app = context.applicationContext
    val marker = File(app.filesDir, "db_recovery/retained_workspace.json")
    val sourceScope = File(app.noBackupFilesDir, ".retained_workspace_source_scope.json")
    val databases =
        listOf(
            "logdate",
            "logdate-wal",
            "logdate-shm",
            "logdate-journal",
        ).associate { "database/$it" to app.getDatabasePath(it) }
    return RetainedJournalWorkspace(
        marker,
        File(app.filesDir, "retained_journals"),
        sources = {
            buildMap {
                putAll(databases)
                if (sourceScope.exists()) put("source_scope.json", sourceScope)

                fun addDirectory(
                    label: String,
                    root: File,
                ) {
                    if (!root.exists()) return
                    root.walkTopDown().filter(File::isFile).forEach { file ->
                        val relative = file.relativeTo(root).invariantSeparatorsPath
                        if (file != marker &&
                            !file.name.startsWith(".retained-journal-") &&
                            !(label in setOf("files/media", "files/audio_notes") && relative.startsWith("staging/"))
                        ) {
                            put("$label/${file.relativeTo(root).invariantSeparatorsPath}", file)
                        }
                    }
                }
                addDirectory("db_recovery", File(app.filesDir, "db_recovery"))
                addDirectory("identity_recovery", File(app.filesDir, "identity_recovery"))
                addDirectory("shared_prefs", File(app.applicationInfo.dataDir, "shared_prefs"))
                File(app.filesDir, "logdate.preferences_pb").takeIf(File::exists)?.let { put("files/logdate.preferences_pb", it) }
                addDirectory("files/datastore", File(app.filesDir, "datastore"))
                addDirectory("files/media", File(app.filesDir, "media"))
                addDirectory("files/user_media", File(app.filesDir, "user_media"))
                addDirectory("files/audio_notes", File(app.filesDir, "audio_notes"))
            }
        },
        databasePaths = databases.keys,
        authorityFile = File(app.noBackupFilesDir, ".retained_workspace_authority"),
        beforePrepare = { captureRetainedSourceScope(app, sourceScope) },
    )
}

fun retainedJournalWorkingKeyName(context: Context): String =
    retainedJournalWorkspace(context).current()?.takeIf { it.phase == RetainedJournalWorkspace.Phase.WORKING }?.primaryKeyName
        ?: "db_encryption_key"

fun retainedJournalPassphraseFile(context: Context): File =
    File(
        context.filesDir,
        if (retainedJournalWorkspace(context).current()?.phase == RetainedJournalWorkspace.Phase.WORKING) {
            "db_recovery/working_passphrase_backup"
        } else {
            "db_recovery/passphrase_backup"
        },
    )

fun requireRetainedJournalBackupReady(
    context: Context,
    deviceTransfer: Boolean,
) {
    check(!NativeJournalReplayCustody(context).hasInputs()) {
        "Pending native source custody must be preserved before another backup"
    }
    retainedJournalWorkspace(context).requireBackupReady(
        deviceTransfer,
        context.getDatabasePath("logdate"),
        retainedJournalPassphraseFile(context),
    )
}

fun retainedNativeDestination(
    context: Context,
    destination: File,
): String {
    val app = context.applicationContext
    val candidate = destination.canonicalFile.toPath()
    val domains =
        listOf(
            "database" to app.getDatabasePath("logdate").parentFile!!,
            "files" to app.filesDir,
            "shared_prefs" to File(app.applicationInfo.dataDir, "shared_prefs"),
        )
    return domains.firstNotNullOfOrNull { (domain, root) ->
        val base = root.canonicalFile.toPath()
        if (candidate != base && candidate.startsWith(base)) "$domain/${base.relativize(candidate)}" else null
    } ?: throw java.io.IOException("Native journal destination is outside supported backup domains")
}

private fun captureRetainedSourceScope(
    context: Context,
    destination: File,
) = captureRetainedSourceScope(destination) {
    runBlocking(Dispatchers.IO) { withTimeout(15_000) { createAndroidMainDataStore(context).data.first() } }
}

/** A failed read leaves the source journal and any prior anchor untouched for a later retry. */
internal fun captureRetainedSourceScope(
    destination: File,
    readPreferences: () -> Preferences,
) {
    val preferences = readPreferences()
    val scope =
        buildJsonObject {
            put("version", 1)
            put("readState", "captured")
            preferences[stringPreferencesKey("identity.canonical_owner_id")]?.let { put("canonicalOwnerId", it) }
            preferences[stringPreferencesKey("identity.canonical_owner_bound")]?.let { put("canonicalOwnerBound", it) }
            val storedOrigin = preferences[stringPreferencesKey("backend_url")]
            put("backendOrigin", storedOrigin ?: DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL)
            put("originProvenance", if (storedOrigin == null) "default-at-capture" else "stored-backend-url")
        }.toString().encodeToByteArray()
    check(scope.size <= 8192)
    val temporary = File.createTempFile(".source-scope-", ".part", destination.parentFile)
    try {
        FileOutputStream(temporary).use { output ->
            output.write(scope)
            output.fd.sync()
        }
        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        FileChannel.open(checkNotNull(destination.parentFile).toPath(), StandardOpenOption.READ).use { it.force(true) }
    } finally {
        temporary.delete()
    }
}
