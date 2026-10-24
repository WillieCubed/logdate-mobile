package app.logdate.client.device.restore

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.uuid.Uuid

/** A portable token conveys no deletion authority without its matching untransferred marker. */
class NativeJournalTransferOwnership(
    private val marker: File,
    private val current: File,
) {
    fun begin(staging: File) {
        check(!staging.exists() && !current.exists())
        check(checkNotNull(staging.parentFile).canonicalFile == checkNotNull(current.parentFile).canonicalFile)
        check(staging.name.matches(Regex("\\.[A-Za-z0-9-]+\\.preparing")))
        val token = Uuid.random().toString()
        retainedJournalWriteAtomic(marker, "$token:building:${staging.name}")
        retainedJournalDurableDirectory(staging)
        retainedJournalWriteAtomic(File(staging, "sender_owner"), token)
    }

    fun record(staging: File) {
        check(!current.exists())
        check(checkNotNull(staging.parentFile).canonicalFile == checkNotNull(current.parentFile).canonicalFile)
        check(staging.name.matches(Regex("\\.[A-Za-z0-9-]+\\.preparing")))
        val token =
            if (marker.exists()) {
                retainedJournalReadBounded(marker, 512).split(':').also {
                    check(it.size == 3 && it[1] == "building" && it[2] == staging.name)
                }[0]
            } else {
                Uuid.random().toString()
            }
        retainedJournalWriteAtomic(File(staging, "sender_owner"), token)
        val hash = retainedJournalChecksum(File(staging, NATIVE_SOURCE_RECEIPT_PATH))
        retainedJournalWriteAtomic(marker, "$token:$hash:${staging.name}")
    }

    fun discardOwned() {
        if (!marker.exists()) {
            check(!current.exists()) { "Incoming native journal transfer must remain in custody" }
            return
        }
        val parts = retainedJournalReadBounded(marker, 512).split(':')
        check(parts.size == 3 && Uuid.parse(parts[0]).toString() == parts[0])
        check(parts[1] == "building" || parts[1].matches(Regex("[a-f0-9]{64}")))
        check(parts[2].matches(Regex("\\.[A-Za-z0-9-]+\\.preparing")))
        val quarantine = File(current.parentFile, ".${parts[0]}.deleting")
        if (!quarantine.exists()) {
            val staging = File(current.parentFile, parts[2])
            val owned = if (staging.exists()) staging else current.takeIf { it.exists() && parts[1] != "building" }
            if (owned != null) {
                val token = File(owned, "sender_owner")
                if (!token.exists() && parts[1] == "building") {
                    check(owned == staging)
                } else {
                    check(retainedJournalReadBounded(token, 128) == parts[0])
                }
                if (parts[1] != "building") check(retainedJournalChecksum(File(owned, NATIVE_SOURCE_RECEIPT_PATH)) == parts[1])
                Files.move(owned.toPath(), quarantine.toPath(), StandardCopyOption.ATOMIC_MOVE)
                retainedJournalSyncDirectory(current.parentFile!!)
            }
        }
        if (quarantine.exists()) {
            check(Files.isDirectory(quarantine.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
            check(quarantine.deleteRecursively())
            retainedJournalSyncDirectory(current.parentFile!!)
        }
        check(marker.delete())
        retainedJournalSyncDirectory(marker.parentFile!!)
    }
}
