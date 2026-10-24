package app.logdate.client.device.restore

import android.content.Context
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.uuid.Uuid

/** First-delivery media stays outside replay routing until an installed snapshot authenticates it. */
class NativeInitialSelectedMedia(
    private val context: Context,
) {
    private val root = File(context.noBackupFilesDir, DIRECTORY)
    private val journal = NativeIncomingJournal(root)

    fun hasInputs(): Boolean {
        requireRoot()
        return root.listFiles().orEmpty().isNotEmpty()
    }

    fun capture(
        path: String,
        size: Long,
        input: InputStream,
    ) {
        require(isSelectedMediaPath(path))
        requireRoot()
        val id = Uuid.random().toString()
        journal.begin(id)
        journal.capture(id, path, size, input)
    }

    /** Only exact inventory bytes move; admission into live media remains the reconciliation job's job. */
    fun promoteCompleted(): Boolean {
        requireRoot()
        val authority = NativeJournalRestoreAuthority(context)
        if (authority.completedReceipt() == null || authority.completedSourceReceipt() == null) return false
        val inventory = authority.completedMediaInventory() ?: return false
        var promoted = false
        root.listFiles().orEmpty().forEach { directory ->
            check(Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS))
            val id = directory.name
            val hash = journal.finish(id)
            val payloads = journal.verified(id, hash)
            if (payloads.isNotEmpty() &&
                payloads.all { payload ->
                    val expected = inventory.entry(payload.path)
                    payload.complete && isSelectedMediaPath(payload.path) && expected != null &&
                        expected.size == payload.expectedSize && expected.sha256 == retainedJournalChecksum(payload.file)
                }
            ) {
                NativeJournalReplayCustody(context).adoptInitialSelectedMedia(id, hash)
                promoted = true
            }
        }
        return promoted
    }

    private fun requireRoot() {
        check(!root.exists() || Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS))
        check(!Files.isSymbolicLink(root.toPath()))
    }

    companion object {
        internal const val DIRECTORY = "native-initial-selected-media"

        internal fun isSelectedMediaPath(path: String): Boolean =
            listOf("files/media/", "files/user_media/", "files/audio_notes/").any(path::startsWith) &&
                path.length <= 1024 &&
                path.split('/').none { it.isEmpty() || it == "." || it == ".." || '\\' in it || it.any { char -> char.code < 32 } }

        fun destinationPath(
            context: Context,
            destination: File,
        ): String? {
            val base = context.filesDir.absoluteFile.toPath()
            val target = destination.absoluteFile.toPath()
            if (!target.startsWith(base)) return null
            val path = "files/${base.relativize(target)}"
            if (!isSelectedMediaPath(path)) return null
            check(target == target.normalize())
            var current = base
            check(!Files.isSymbolicLink(current))
            base.relativize(target).forEach { segment ->
                current = current.resolve(segment)
                check(!Files.isSymbolicLink(current))
            }
            return path
        }
    }
}
