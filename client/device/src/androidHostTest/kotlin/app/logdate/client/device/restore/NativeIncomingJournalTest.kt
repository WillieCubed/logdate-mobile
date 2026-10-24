package app.logdate.client.device.restore

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class NativeIncomingJournalTest {
    @Test
    fun `late native files preserve all working paths and different revisions`() =
        fixture { root, store ->
            val working = File(root, "working").apply { writeText("accepted newer content") }
            val id =
                store.begin(
                    kotlin.uuid.Uuid
                        .random()
                        .toString(),
                )
            store.capture(id, "database/logdate", 3, "old".byteInputStream())
            store.capture(id, "database/logdate", 3, "old".byteInputStream())
            store.capture(id, "database/logdate", 4, "old2".byteInputStream())
            store.capture(id, "files/db_recovery/passphrase_backup", 3, "key".byteInputStream())
            val seal = store.finish(id)
            assertEquals("accepted newer content", working.readText())
            assertEquals(3, store.verified(id, seal).size)
            assertEquals(
                setOf("old", "old2"),
                store
                    .verified(id, seal)
                    .filter { it.path == "database/logdate" }
                    .map { it.file.readText() }
                    .toSet(),
            )
        }

    @Test
    fun `process restart resumes pending late key delivery and finalization`() =
        fixture { root, store ->
            val id =
                store.begin(
                    kotlin.uuid.Uuid
                        .random()
                        .toString(),
                )
            store.capture(id, "files/db_recovery/passphrase_backup", 3, "key".byteInputStream())
            val restarted = NativeIncomingJournal(File(root, "incoming"))
            assertEquals(setOf(id), restarted.pending())
            val seal = restarted.finish(id)
            assertEquals(
                "key",
                restarted
                    .verified(id, seal)
                    .single()
                    .file
                    .readText(),
            )
            assertNotEquals(
                id,
                restarted.begin(
                    kotlin.uuid.Uuid
                        .random()
                        .toString(),
                ),
            )
        }

    @Test
    fun `incomplete streams remain retained and a matching retry repairs completion`() =
        fixture { _, store ->
            val id =
                store.begin(
                    kotlin.uuid.Uuid
                        .random()
                        .toString(),
                )
            assertFailsWith<IOException> { store.capture(id, "database/logdate", 6, "old".byteInputStream()) }
            store.capture(id, "database/logdate", 6, "old-db".byteInputStream())
            val seal = store.finish(id)
            assertEquals(1, store.verified(id, seal).count { it.complete })
            assertEquals(2, store.verified(id, seal).size)
        }

    @Test
    fun `corrupted earlier delivery cannot be hidden by later complete delivery`() =
        fixture { _, store ->
            val id =
                store.begin(
                    kotlin.uuid.Uuid
                        .random()
                        .toString(),
                )
            store.capture(id, "database/logdate", 3, "old".byteInputStream())
            val seal = store.finish(id)
            store
                .verified(id, seal)
                .single()
                .file
                .appendText("corrupt")
            assertFailsWith<IllegalStateException> { store.verified(id, seal) }
        }

    @Test
    fun `traversal and oversized paths fail before creating payloads`() =
        fixture { root, store ->
            val id =
                store.begin(
                    kotlin.uuid.Uuid
                        .random()
                        .toString(),
                )
            assertFailsWith<IllegalArgumentException> { store.capture(id, "../canonical", 3, "old".byteInputStream()) }
            assertFailsWith<IllegalArgumentException> { store.capture(id, "files/" + "x".repeat(1100), 3, "old".byteInputStream()) }
            assertFalse(File(root, "canonical").exists())
        }

    @Test
    fun `manifest hash and missing payload are verified from a transferred copy`() =
        fixture { root, store ->
            val id =
                store.begin(
                    kotlin.uuid.Uuid
                        .random()
                        .toString(),
                )
            store.capture(id, "database/logdate-wal", 3, "wal".byteInputStream())
            val seal = store.finish(id)
            val destination = File(root, "destination")
            File(root, "incoming").copyRecursively(destination)
            val transferred = NativeIncomingJournal(destination)
            assertContentEquals(
                "wal".toByteArray(),
                transferred
                    .verified(id, seal)
                    .single()
                    .file
                    .readBytes(),
            )
            transferred
                .verified(id, seal)
                .single()
                .file
                .delete()
            assertFailsWith<IllegalStateException> { transferred.verified(id, seal) }
        }

    @Test
    fun `new delivery B is independently readable after interrupted delivery A`() =
        fixture { _, store ->
            val generationA =
                kotlin.uuid.Uuid
                    .random()
                    .toString()
            val generationB =
                kotlin.uuid.Uuid
                    .random()
                    .toString()
            val a = store.begin(generationA)
            assertFailsWith<IOException> { store.capture(a, "database/logdate", 6, "old".byteInputStream()) }
            val b = store.begin(generationB)
            assertNotEquals(a, b)
            store.capture(b, "database/logdate", 7, "new-db!".byteInputStream())
            store.capture(b, "database/logdate-wal", 7, "new-wal".byteInputStream())
            val hashB = store.finish(b)
            val fullB = store.coherentVerified(b, hashB)!!
            assertEquals("new-db!", fullB.getValue("database/logdate").file.readText())
            assertEquals("new-wal", fullB.getValue("database/logdate-wal").file.readText())
            val hashA = store.finish(a)
            assertEquals(
                "old",
                store
                    .verified(a, hashA)
                    .single()
                    .file
                    .readText(),
            )
            kotlin.test.assertNull(store.coherentVerified(a, hashA))
        }

    @Test
    fun `different full revisions of one path cannot form a database sidecar cohort`() =
        fixture { _, store ->
            val id =
                store.begin(
                    kotlin.uuid.Uuid
                        .random()
                        .toString(),
                )
            store.capture(id, "database/logdate", 3, "old".byteInputStream())
            store.capture(id, "database/logdate-wal", 3, "wal".byteInputStream())
            store.capture(id, "database/logdate", 3, "new".byteInputStream())
            val hash = store.finish(id)
            assertEquals(3, store.verified(id, hash).size)
            kotlin.test.assertNull(store.coherentVerified(id, hash))
        }

    @Test
    fun `five thousand media originals use bounded per record inventory instead of aggregate manifest`() =
        fixture { root, store ->
            val id =
                store.begin(
                    kotlin.uuid.Uuid
                        .random()
                        .toString(),
                )
            val padding = List(4) { "p".repeat(80) }.joinToString("/")
            repeat(5001) { index -> store.capture(id, "files/media/$padding/$index.jpg", 1, byteArrayOf(7).inputStream()) }
            val hash = store.finish(id)
            assertEquals(5001, store.verified(id, hash).size)
            assertEquals(5001, store.coherentVerified(id, hash)!!.size)
            val entries = File(root, "incoming/$id/records").walkTopDown().filter { it.name == "entry.json" }.toList()
            assertEquals(5001, entries.size)
            kotlin.test.assertTrue(entries.sumOf(File::length) > 1024 * 1024)
            kotlin.test.assertTrue(entries.all { it.length() <= 8192 })
            kotlin.test.assertTrue(File(root, "incoming/$id/manifest.json").length() <= 8192)
        }

    private fun fixture(block: (File, NativeIncomingJournal) -> Unit) {
        val root = Files.createTempDirectory("native-incoming-journal-").toFile()
        try {
            block(root, NativeIncomingJournal(File(root, "incoming")))
        } finally {
            root.deleteRecursively()
        }
    }
}
