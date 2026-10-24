package app.logdate.client.device.restore

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class NativeJournalSourceReceiptTest {
    @Test
    fun `receipt before or after database callbacks admits the same exact source`() {
        for (receiptFirst in listOf(true, false)) {
            fixture { source, workspace ->
                val id = workspace.beginIncomingDelivery(Uuid.random().toString())
                val files =
                    source.files.entries
                        .toList()
                        .let { if (receiptFirst) it.reversed() else it }
                files.forEach { (path, file) -> capture(workspace, id, path, file) }
                workspace.finishIncomingDelivery()

                val admitted = verifiedNativeJournalSources(workspace.incomingPayloads().values.flatten())

                val journal = admitted.values.single()
                assertEquals("sender database", journal.getValue("database/logdate").readText())
                assertEquals("sender preferences", journal.getValue("files/logdate.preferences_pb").readText())
                assertTrue(journal.containsKey("source_scope.json"))
            }
        }
    }

    @Test
    fun `interrupted delivery can use later identical complete bytes but never another revision`() =
        fixture { source, workspace ->
            val first = workspace.beginIncomingDelivery(Uuid.random().toString())
            capture(workspace, first, RECEIPT, source.receipt)
            capture(workspace, first, PREFERENCES, source.preferences)
            assertFailsWith<IOException> {
                workspace.captureIncomingFile(first, DATABASE, 100, "partial".byteInputStream())
            }
            workspace.finishIncomingDelivery()
            assertTrue(verifiedNativeJournalSources(workspace.incomingPayloads().values.flatten()).isEmpty())

            val unrelated = workspace.beginIncomingDelivery(Uuid.random().toString())
            workspace.captureIncomingFile(unrelated, DATABASE, 15, "different bytes".byteInputStream())
            workspace.finishIncomingDelivery()
            assertTrue(verifiedNativeJournalSources(workspace.incomingPayloads().values.flatten()).isEmpty())

            val complete = workspace.beginIncomingDelivery(Uuid.random().toString())
            capture(workspace, complete, DATABASE, source.database)
            workspace.finishIncomingDelivery()
            assertEquals(
                "sender database",
                verifiedNativeJournalSources(workspace.incomingPayloads().values.flatten())
                    .values
                    .single()
                    .getValue("database/logdate")
                    .readText(),
            )
        }

    @Test
    fun `changed preferences cannot borrow an otherwise matching source receipt`() =
        fixture { source, workspace ->
            source.preferences.writeText("replacement owner's preferences")
            val id = workspace.beginIncomingDelivery(Uuid.random().toString())
            source.files.forEach { (path, file) -> capture(workspace, id, path, file) }
            workspace.finishIncomingDelivery()
            assertTrue(verifiedNativeJournalSources(workspace.incomingPayloads().values.flatten()).isEmpty())
        }

    @Test
    fun `missing declared WAL keeps the entire journal in custody`() =
        fixture { source, workspace ->
            val wal = File(source.root, "source-wal").apply { writeText("committed WAL") }
            source.writeReceipt(mapOf("database/logdate" to source.database, "database/logdate-wal" to wal))
            val id = workspace.beginIncomingDelivery(Uuid.random().toString())
            source.files.forEach { (path, file) -> capture(workspace, id, path, file) }
            workspace.finishIncomingDelivery()
            assertTrue(verifiedNativeJournalSources(workspace.incomingPayloads().values.flatten()).isEmpty())
        }

    @Test
    fun `missing owner or malformed binding never becomes scope authority`() {
        for ((key, value) in listOf("canonicalOwnerId" to "", "canonicalOwnerBound" to "unknown")) {
            fixture { source, workspace ->
                val record = Json.parseToJsonElement(source.receipt.readText()).let { it as JsonObject }.toMutableMap()
                record[key] = JsonPrimitive(value)
                source.receipt.writeText(JsonObject(record).toString())
                val id = workspace.beginIncomingDelivery(Uuid.random().toString())
                source.files.forEach { (path, file) -> capture(workspace, id, path, file) }
                workspace.finishIncomingDelivery()
                assertTrue(verifiedNativeJournalSources(workspace.incomingPayloads().values.flatten()).isEmpty())
            }
        }
    }

    @Test
    fun `legacy delivery without sender receipt remains preserved but unadmitted`() =
        fixture { source, workspace ->
            val id = workspace.beginIncomingDelivery(Uuid.random().toString())
            source.files.filterKeys { it != RECEIPT }.forEach { (path, file) -> capture(workspace, id, path, file) }
            workspace.finishIncomingDelivery()
            assertTrue(verifiedNativeJournalSources(workspace.incomingPayloads().values.flatten()).isEmpty())
            assertEquals(2, workspace.incomingPayloads().getValue(id).size)
        }

    @Test
    fun `receipt rejects unsafe database inventories before writing`() =
        fixture { source, _ ->
            assertFailsWith<IllegalArgumentException> {
                source.writeReceipt(mapOf("database/another-database" to source.database))
            }
        }

    @Test
    fun `pending owner adoption cannot produce a receipt but completed historical adoption can`() =
        fixture { source, _ ->
            val stableReceipt = source.receipt.readBytes()
            val preferences =
                mutablePreferencesOf(
                    stringPreferencesKey("identity.canonical_owner_id") to "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                    stringPreferencesKey("backend_url") to "https://cloud.example",
                    stringPreferencesKey("identity.pending_history_owner_adoption.v1") to "durable unfinished adoption",
                )
            assertFailsWith<IllegalStateException> {
                writeNativeJournalSourceReceipt(
                    source.receipt,
                    preferences,
                    source.preferences,
                    mapOf("database/logdate" to source.database),
                )
            }
            assertTrue(stableReceipt.contentEquals(source.receipt.readBytes()))
            preferences.remove(stringPreferencesKey("identity.pending_history_owner_adoption.v1"))
            preferences[stringPreferencesKey("identity.completed_history_owner_adoption.v1")] = "completed prior-owner history"
            writeNativeJournalSourceReceipt(source.receipt, preferences, source.preferences, mapOf("database/logdate" to source.database))
            verifyNativeJournalSourceReceipt(source.receipt, source.preferences, mapOf("database/logdate" to source.database))
        }

    private fun capture(
        workspace: RetainedJournalWorkspace,
        id: String,
        path: String,
        file: File,
    ) = file.inputStream().use { workspace.captureIncomingFile(id, path, file.length(), it) }

    private fun fixture(block: (Source, RetainedJournalWorkspace) -> Unit) {
        val root = Files.createTempDirectory("native-source-receipt-").toFile()
        try {
            val original = File(root, "original").apply { writeText("original encrypted journal") }
            val workspace =
                RetainedJournalWorkspace(
                    File(root, "working/session.json"),
                    File(root, "working/retained"),
                    { mapOf("database/logdate" to original) },
                    setOf("database/logdate"),
                    File(root, "working/no-backup/authority"),
                )
            workspace.prepare()
            block(Source(root), workspace)
        } finally {
            check(root.deleteRecursively())
        }
    }

    private class Source(
        val root: File,
    ) {
        val database = File(root, "sender-database").apply { writeText("sender database") }
        val preferences = File(root, "sender-preferences").apply { writeText("sender preferences") }
        val receipt = File(root, "sender-receipt")
        val files get() = linkedMapOf(DATABASE to database, PREFERENCES to preferences, RECEIPT to receipt)

        init {
            writeReceipt(mapOf("database/logdate" to database))
        }

        fun writeReceipt(databases: Map<String, File>) =
            writeNativeJournalSourceReceipt(
                receipt,
                mutablePreferencesOf(
                    stringPreferencesKey("identity.canonical_owner_id") to "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                    stringPreferencesKey("identity.canonical_owner_bound") to "true",
                    stringPreferencesKey("backend_url") to "https://cloud.example",
                ),
                preferences,
                databases,
            )
    }

    private companion object {
        const val PREFIX = "files/native_journal_transfer/current/"
        const val DATABASE = PREFIX + "database/logdate"
        const val PREFERENCES = PREFIX + "files/logdate.preferences_pb"
        const val RECEIPT = PREFIX + "files/db_recovery/native_source_scope.json"
    }
}
