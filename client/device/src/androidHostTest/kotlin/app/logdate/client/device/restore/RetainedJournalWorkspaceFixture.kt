package app.logdate.client.device.restore

import java.io.File
import java.nio.file.Files
import kotlin.test.assertContentEquals

internal class RetainedJournalWorkspaceFixture : AutoCloseable {
    val root: File = Files.createTempDirectory("retained-journal-workspace-").toFile()
    val retained = File(root, "retained")
    val database = File(root, "database/logdate")
    private val db =
        listOf("logdate", "logdate-wal", "logdate-shm", "logdate-journal").associate { name ->
            "database/$name" to
                File(root, "database/$name").apply {
                    checkNotNull(parentFile).mkdirs()
                    writeBytes((name + " encrypted bytes").encodeToByteArray())
                }
        }
    val keys =
        listOf(
            "passphrase_backup",
            "passphrase_backup.part",
            "phrase_backup",
            "account_key_backup",
            "media_key_backup",
            "secure.xml",
            "secure.xml.bak",
        ).associate { name ->
            "keys/$name" to
                File(root, "keys/$name").apply {
                    checkNotNull(parentFile).mkdirs()
                    writeBytes((name + " original key bytes").encodeToByteArray())
                }
        }
    val before = sources().mapValues { it.value.readBytes() }

    fun sources() = db + keys

    fun store(step: (String) -> Unit = {}) =
        RetainedJournalWorkspace(
            File(root, "session.json"),
            retained,
            ::sources,
            db.keys,
            File(root, "no-backup/authority"),
            afterDurableStep = step,
        )

    fun assertRetained(session: RetainedJournalWorkspace.Session) {
        before.forEach { (name, bytes) -> assertContentEquals(bytes, File(retained, "${session.id}/$name").readBytes()) }
    }

    override fun close() {
        root.deleteRecursively()
    }
}
