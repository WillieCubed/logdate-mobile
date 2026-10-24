package app.logdate.client.device.restore

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeJournalMediaInventoryTest {
    @Test
    fun `inventory authenticates canonical and legacy originals without staging or symlinks`() =
        fixture { root ->
            val files = File(root, "files")
            val bytes = "source image".encodeToByteArray()
            val hash = sha256(bytes)
            val canonical = "files/media/objects/sha256/${hash.take(2)}/$hash.png"
            write(files, canonical.removePrefix("files/"), bytes)
            write(files, "user_media/older photo.jpg", bytes)
            write(files, "audio_notes/voice.m4a", "voice".encodeToByteArray())
            write(files, "audio_notes/staging/unfinished.m4a", bytes)
            write(files, "user_media/.odd%20name.part", bytes)
            write(files, "user_media/empty-original.jpg", byteArrayOf())
            val link = File(files, "user_media/link.png")
            Files.createSymbolicLink(link.toPath(), File(files, "user_media/older photo.jpg").toPath())

            val inventoryFile = File(root, NATIVE_MEDIA_INVENTORY_PATH)
            writeNativeJournalMediaInventory(inventoryFile, files)
            val inventory = verifyNativeJournalMediaInventory(inventoryFile, sha256(inventoryFile.readBytes()))
            assertEquals(5, inventory.files.size)
            assertEquals(hash, inventory.entry(canonical)?.sha256)
            assertEquals(hash, inventory.entry("files/user_media/older photo.jpg")?.sha256)
            assertEquals(
                "voice".encodeToByteArray().size.toLong(),
                inventory.entry("files/audio_notes/voice.m4a")?.size,
            )
            assertNull(inventory.entry("files/audio_notes/staging/unfinished.m4a"))
            assertEquals(hash, inventory.entry("files/user_media/.odd%20name.part")?.sha256)
            assertEquals(0L, inventory.entry("files/user_media/empty-original.jpg")?.size)
            assertEquals(sha256(byteArrayOf()), inventory.entry("files/user_media/empty-original.jpg")?.sha256)
            assertNull(inventory.entry("files/user_media/link.png"))
            assertTrue(inventoryFile.readBytes().size < 8192)
        }

    @Test
    fun `tampered or missing inventory cannot authorize media`() =
        fixture { root ->
            val files = File(root, "files")
            write(files, "user_media/photo.jpg", "original".encodeToByteArray())
            val inventoryFile = File(root, NATIVE_MEDIA_INVENTORY_PATH)
            writeNativeJournalMediaInventory(inventoryFile, files)
            val expected = sha256(inventoryFile.readBytes())
            inventoryFile.writeText(inventoryFile.readText().replace("photo.jpg", "other.jpg"))
            assertFailsWith<IllegalStateException> { verifyNativeJournalMediaInventory(inventoryFile, expected) }
            check(inventoryFile.delete())
            assertFailsWith<Exception> { verifyNativeJournalMediaInventory(inventoryFile, expected) }
        }

    @Test
    fun `v2 receipt retains journal compatibility but v3 receipt pins a separate inventory hash`() =
        fixture { root ->
            val files = File(root, "files")
            write(files, "user_media/photo.jpg", "original".encodeToByteArray())
            val inventory = File(root, NATIVE_MEDIA_INVENTORY_PATH)
            writeNativeJournalMediaInventory(inventory, files)
            val preferencesFile = File(root, "preferences").apply { writeText("preferences") }
            val database = File(root, "database").apply { writeText("encrypted journal") }
            val receipt = File(root, "receipt")
            val preferences =
                mutablePreferencesOf(
                    stringPreferencesKey("identity.canonical_owner_id") to "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                    stringPreferencesKey("backend_url") to "https://cloud.example",
                )
            writeNativeJournalSourceReceipt(
                receipt,
                preferences,
                preferencesFile,
                mapOf("database/logdate" to database),
            )
            assertEquals(
                2,
                verifyNativeJournalSourceReceipt(receipt, preferencesFile, mapOf("database/logdate" to database)).version,
            )
            writeNativeJournalSourceReceipt(
                receipt,
                preferences,
                preferencesFile,
                mapOf("database/logdate" to database),
                mediaInventory = inventory,
            )
            val v3 = verifyNativeJournalSourceReceipt(receipt, preferencesFile, mapOf("database/logdate" to database))
            assertEquals(3, v3.version)
            assertEquals(sha256(inventory.readBytes()), v3.mediaInventorySha256)
            assertTrue(receipt.readBytes().size < 8192)
        }

    @Test
    fun `only the v3 receipt's exact complete inventory is admitted with its source`() =
        fixture { root ->
            val files = File(root, "files")
            write(files, "user_media/photo.jpg", "original".encodeToByteArray())
            val inventory = File(root, NATIVE_MEDIA_INVENTORY_PATH)
            writeNativeJournalMediaInventory(inventory, files)
            val wrong = File(root, "wrong-inventory").apply { writeText("{}") }
            val preferencesFile = File(root, "preferences").apply { writeText("preferences") }
            val database = File(root, "database").apply { writeText("encrypted journal") }
            val receipt = File(root, "receipt")
            writeNativeJournalSourceReceipt(
                receipt,
                mutablePreferencesOf(
                    stringPreferencesKey("identity.canonical_owner_id") to "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                    stringPreferencesKey("backend_url") to "https://cloud.example",
                ),
                preferencesFile,
                mapOf("database/logdate" to database),
                mediaInventory = inventory,
            )

            fun payload(
                path: String,
                file: File,
            ) = NativeIncomingJournal.Payload(NATIVE_JOURNAL_TRANSFER_PREFIX + path, file, true, file.length())
            val core =
                listOf(
                    payload(NATIVE_SOURCE_RECEIPT_PATH, receipt),
                    payload("files/logdate.preferences_pb", preferencesFile),
                    payload("database/logdate", database),
                )
            val receiptHash = sha256(receipt.readBytes())
            assertTrue(verifiedNativeJournalMediaInventories(core).isEmpty())
            assertTrue(verifiedNativeJournalMediaInventories(core + payload(NATIVE_MEDIA_INVENTORY_PATH, wrong)).isEmpty())
            val delivered = core + payload(NATIVE_MEDIA_INVENTORY_PATH, inventory)
            assertEquals("files/user_media/photo.jpg", verifiedNativeJournalMediaInventories(delivered)[receiptHash]?.files?.single()?.path)
            assertEquals(inventory, verifiedNativeJournalSources(delivered).values.single()[NATIVE_MEDIA_INVENTORY_PATH])
        }

    private fun fixture(block: (File) -> Unit) {
        val root = Files.createTempDirectory("native-media-inventory-").toFile()
        try {
            block(root)
        } finally {
            check(root.deleteRecursively())
        }
    }

    private fun write(
        root: File,
        path: String,
        bytes: ByteArray,
    ) {
        val destination = File(root, path)
        check(destination.parentFile!!.mkdirs() || destination.parentFile!!.isDirectory)
        destination.writeBytes(bytes)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
