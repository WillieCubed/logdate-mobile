package app.logdate.client.e2e

import android.app.backup.BackupAgent
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.device.restore.NATIVE_JOURNAL_TRANSFER_PREFIX
import app.logdate.client.device.restore.NATIVE_SOURCE_RECEIPT_PATH
import app.logdate.client.device.restore.NativeBackupAgent
import app.logdate.client.device.restore.NativeJournalReplayCustody
import app.logdate.client.device.restore.NativeJournalRestoreAuthority
import app.logdate.client.device.restore.writeNativeJournalSourceReceipt
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Real BackupAgent FILE callbacks; only synthetic paths in a fresh Managed Device debug install. */
@RunWith(AndroidJUnit4::class)
class NativeJournalCompletedReplayTest {
    @Test
    fun rawDatabasePreferencesAndMediaReplayCannotOverwriteAcceptedEdits() =
        fixture { context, source ->
            val agent = agent(context)
            try {
                deliver(agent, context.getDatabasePath("logdate"), "old raw database".encodeToByteArray(), source)
                deliver(agent, File(context.filesDir, "logdate.preferences_pb"), "old preferences".encodeToByteArray(), source)
                deliver(agent, File(context.filesDir, "media/original.png"), "old original media".encodeToByteArray(), source)
                agent.onRestoreFinished()
                assertEquals("new local edit", context.getDatabasePath("logdate").readText())
                assertEquals("new preferences", File(context.filesDir, "logdate.preferences_pb").readText())
                assertEquals("new original media", File(context.filesDir, "media/original.png").readText())
                val retained = NativeJournalReplayCustody(context).sealedPayloads()
                assertEquals(
                    setOf("database/logdate", "files/logdate.preferences_pb", "files/media/original.png"),
                    retained.map { it.path }.toSet(),
                )
            } finally {
                agent.onDestroy()
            }
        }

    @Test
    fun sourceReceiptFirstOrLastAllowsOnlyExactKnownFileReplay() {
        for (receiptFirst in listOf(true, false)) {
            fixture { context, source ->
                val agent = agent(context)
                try {
                    val payloads =
                        linkedMapOf(
                            "database/logdate" to File(source, "source-db"),
                            NATIVE_SOURCE_RECEIPT_PATH to File(source, "source-receipt"),
                        ).entries.toList().let { if (receiptFirst) it.reversed() else it }
                    payloads.forEach { (path, file) ->
                        deliver(
                            agent,
                            File(context.filesDir, NATIVE_JOURNAL_TRANSFER_PREFIX.removePrefix("files/") + path),
                            file.readBytes(),
                            source,
                        )
                    }
                    agent.onRestoreFinished()
                    assertEquals("new local edit", context.getDatabasePath("logdate").readText())
                    assertTrue(!NativeJournalReplayCustody(context).hasInputs())
                } finally {
                    agent.onDestroy()
                }
            }
        }
    }

    private fun fixture(block: (Context, File) -> Unit) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("nativeJournalReplay") == "true")
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        check(Build.HARDWARE in setOf("ranchu", "goldfish") && base.packageName.endsWith(".debug"))
        val root = File(base.cacheDir, "native-journal-replay-${Uuid.random()}").apply { check(mkdirs()) }
        val context =
            object : ContextWrapper(base) {
                override fun getFilesDir() = File(root, "files").apply { mkdirs() }

                override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }

                override fun getDatabasePath(name: String) = File(root, "databases/$name").apply { checkNotNull(parentFile).mkdirs() }

                override fun getApplicationContext(): Context = this
            }
        try {
            val source = File(root, "source").apply { mkdirs() }
            val sourceDatabase = File(source, "source-db").apply { writeText("older source DB") }
            val sourcePreferences = File(source, "source-preferences").apply { writeText("sender preferences") }
            val sourceReceipt = File(source, "source-receipt")
            writeNativeJournalSourceReceipt(
                sourceReceipt,
                mutablePreferencesOf(
                    stringPreferencesKey("identity.canonical_owner_id") to "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                    stringPreferencesKey("backend_url") to "https://cloud.example",
                ),
                sourcePreferences,
                mapOf("database/logdate" to sourceDatabase),
            )
            val payloads =
                mapOf(
                    "database/logdate" to sourceDatabase,
                    "files/logdate.preferences_pb" to sourcePreferences,
                    NATIVE_SOURCE_RECEIPT_PATH to sourceReceipt,
                )
            val authority = NativeJournalRestoreAuthority(context)
            authority.begin(resumeInterrupted = false)
            val receipt = checksum(sourceReceipt)
            authority.prepareInstallation(receipt, context.getDatabasePath("logdate"), checksum(sourceDatabase))
            context.getDatabasePath("logdate").writeText("new local edit")
            File(context.filesDir, "logdate.preferences_pb").writeText("new preferences")
            File(context.filesDir, "media/original.png").apply {
                checkNotNull(parentFile).mkdirs()
                writeText("new original media")
            }
            authority.rememberCompletedSnapshot(receipt, payloads)
            block(context, source)
        } finally {
            check(root.deleteRecursively())
        }
    }

    private fun agent(context: Context): NativeBackupAgent =
        NativeBackupAgent().also {
            ContextWrapper::class.java
                .getDeclaredMethod(
                    "attachBaseContext",
                    Context::class.java,
                ).apply { isAccessible = true }
                .invoke(it, context)
        }

    private fun deliver(
        agent: NativeBackupAgent,
        destination: File,
        bytes: ByteArray,
        source: File,
    ) {
        val incoming = File(source, "incoming-${Uuid.random()}").apply { writeBytes(bytes) }
        ParcelFileDescriptor.open(incoming, ParcelFileDescriptor.MODE_READ_ONLY).use {
            agent.onRestoreFile(it, bytes.size.toLong(), destination, BackupAgent.TYPE_FILE, 0x180L, 0L)
        }
    }

    private fun checksum(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
            "%02x".format(it)
        }
}
