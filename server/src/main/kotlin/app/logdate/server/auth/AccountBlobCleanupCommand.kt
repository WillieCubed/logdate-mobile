package app.logdate.server.auth

import app.logdate.server.database.DatabaseConfig
import app.logdate.server.logdate.FilesystemLogDateBlobStorage
import app.logdate.server.sync.GcsMediaStorage
import io.github.aakira.napier.Napier
import kotlin.system.exitProcess

/** Dispatch before constructing the HTTP server or resolving unrelated server dependencies. */
fun handleAccountBlobCleanupCommand(args: Array<String>): Boolean {
    if (args.isEmpty()) return false
    require(args.contentEquals(arrayOf("--cleanup-account-blobs"))) { "Unknown server arguments" }
    try {
        runAccountBlobCleanupCommand()
    } catch (_: Exception) {
        Napier.e("Account blob cleanup failed")
        exitProcess(1)
    }
    return true
}

/** A one-shot process for request-allocated hosts; never starts the HTTP server. */
fun runAccountBlobCleanupCommand() {
    val source = DatabaseConfig.createDataSource()
    try {
        requireAccountBlobCleanupSchema(source)
        val storage = GcsMediaStorage.fromEnvironment() ?: FilesystemLogDateBlobStorage.fromEnvironment()
        checkNotNull(storage) { "Durable blob storage is required for account cleanup" }
        AccountBlobCleanup(source, storage).runOnce()
    } finally {
        (source as? AutoCloseable)?.close()
    }
}
