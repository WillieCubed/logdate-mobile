package app.logdate.space

import app.logdate.lexicon.app.logdate.crypto.SealedPayload
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.lexicon.data.Bytes
import studio.hypertext.atproto.lexicon.data.LexiconData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SealedRecordCodecTest {
    private val owner = "did:plc:owner"
    private val space = SpaceRef.personal(owner)
    private val entry =
        SealedPayload(
            collection = "app.logdate.journal.entry",
            rkey = "3k2a",
            record =
                buildJsonObject {
                    put("\$type", "app.logdate.journal.entry")
                    put("createdAt", "2026-10-19T10:00:00.000Z")
                    put("text", JsonPrimitive("Pancakes at the lake"))
                },
        )

    private suspend fun keys(seed: Int = 7) = SealingKeys.personal(ByteArray(32) { (it + seed).toByte() })

    @Test
    fun `a sealed record opens back to the same payload`() =
        runTest {
            val keys = keys()
            val write = SealedRecordCodec.seal(keys, space, owner, entry)
            assertEquals(entry, SealedRecordCodec.open(keys, space, owner, write.rkey, write.record))
            assertEquals(1L, write.record.version)
            assertEquals(keys.keyId, write.record.keyId)
        }

    @Test
    fun `the sealed key is stable and opaque and never contains the inner key`() =
        runTest {
            val keys = keys()
            val first = SealedRecordCodec.sealedRkey(keys, entry.collection, entry.rkey)
            assertEquals(first, SealedRecordCodec.sealedRkey(keys, entry.collection, entry.rkey))
            assertEquals(26, first.length)
            assertTrue(first.all { it in 'a'..'z' || it in '2'..'7' })
            assertNotEquals(first, SealedRecordCodec.sealedRkey(keys, "app.logdate.journal.journal", entry.rkey))
            assertNotEquals(first, SealedRecordCodec.sealedRkey(keys(seed = 8), entry.collection, entry.rkey))
        }

    @Test
    fun `nothing about the inner record is visible in the envelope`() =
        runTest {
            val write = SealedRecordCodec.seal(keys(), space, owner, entry)
            val visible = write.record.toString() + write.rkey
            listOf("journal", "entry", "2026", "3k2a", "Pancakes").forEach { assertTrue(it !in visible, it) }
            assertTrue(SealedPadding.isPaddedSize(write.record.ciphertext.size - 16))
        }

    @Test
    fun `an envelope moved to another key or space or author can't be opened`() =
        runTest {
            val keys = keys()
            val write = SealedRecordCodec.seal(keys, space, owner, entry)
            val other = SealedRecordCodec.sealedRkey(keys, entry.collection, "other")
            assertFailsWith<SealedRecordException> { SealedRecordCodec.open(keys, space, owner, other, write.record) }
            assertFailsWith<SealedRecordException> {
                SealedRecordCodec.open(keys, SpaceRef.personal("did:plc:else"), owner, write.rkey, write.record)
            }
            assertFailsWith<SealedRecordException> {
                SealedRecordCodec.open(keys, space, "did:plc:else", write.rkey, write.record)
            }
        }

    @Test
    fun `another account's keys can't open it`() =
        runTest {
            val write = SealedRecordCodec.seal(keys(), space, owner, entry)
            assertFailsWith<SealedRecordException> {
                SealedRecordCodec.open(keys(seed = 9), space, owner, write.rkey, write.record)
            }
        }

    @Test
    fun `a sealed key that doesn't match the inner record is refused`() =
        runTest {
            val keys = keys()
            val write = SealedRecordCodec.seal(keys, space, owner, entry)
            val swapped = SealedRecordCodec.seal(keys, space, owner, entry.copy(rkey = "other"))
            // Same envelope stored under the first record's key: the AAD check catches it.
            assertFailsWith<SealedRecordException> { SealedRecordCodec.open(keys, space, owner, write.rkey, swapped.record) }
        }

    @Test
    fun `an envelope of another version or key is refused before decrypting`() =
        runTest {
            val keys = keys()
            val write = SealedRecordCodec.seal(keys, space, owner, entry)
            assertFailsWith<SealedRecordException> {
                SealedRecordCodec.open(keys, space, owner, write.rkey, write.record.copy(version = 2))
            }
            assertFailsWith<SealedRecordException> {
                SealedRecordCodec.open(keys, space, owner, write.rkey, write.record.copy(keyId = "aaaaaaaaaaaaa"))
            }
        }

    @Test
    fun `a tampered envelope fails authentication`() =
        runTest {
            val keys = keys()
            val write = SealedRecordCodec.seal(keys, space, owner, entry)
            val ciphertext =
                write.record.ciphertext
                    .toByteArray()
                    .also { it[0] = (it[0].toInt() xor 1).toByte() }
            val nonce =
                write.record.nonce
                    .toByteArray()
                    .also { it[0] = (it[0].toInt() xor 1).toByte() }
            assertFailsWith<SealedRecordException> {
                SealedRecordCodec.open(keys, space, owner, write.rkey, write.record.copy(ciphertext = Bytes(ciphertext)))
            }
            assertFailsWith<SealedRecordException> {
                SealedRecordCodec.open(keys, space, owner, write.rkey, write.record.copy(nonce = Bytes(nonce)))
            }
            assertFailsWith<SealedRecordException> {
                SealedRecordCodec.open(keys, space, owner, write.rkey, write.record.copy(nonce = Bytes(ByteArray(16))))
            }
            val truncated =
                write.record.ciphertext
                    .toByteArray()
                    .copyOf(write.record.ciphertext.size - 1)
            assertFailsWith<SealedRecordException> {
                SealedRecordCodec.open(keys, space, owner, write.rkey, write.record.copy(ciphertext = Bytes(truncated)))
            }
        }

    @Test
    fun `every seal uses a fresh nonce`() =
        runTest {
            val keys = keys()
            val first = SealedRecordCodec.seal(keys, space, owner, entry)
            val second = SealedRecordCodec.seal(keys, space, owner, entry)
            assertEquals(first.rkey, second.rkey)
            assertEquals(12, first.record.nonce.size)
            assertNotEquals(first.record.nonce, second.record.nonce)
            assertNotEquals(first.record.ciphertext, second.record.ciphertext)
        }

    @Test
    fun `an authentic envelope whose inner key doesn't match its sealed key is refused`() =
        runTest {
            val keys = keys()
            val elsewhere = SealedRecordCodec.sealedRkey(keys, entry.collection, "other")
            val plaintext = SealedPadding.pad(LexiconData.encodeToDagCbor(SealedPayload.serializer(), entry))
            val record = SealedRecordCodec.encrypt(keys, space, owner, elsewhere, plaintext)
            val error =
                assertFailsWith<SealedRecordException> { SealedRecordCodec.open(keys, space, owner, elsewhere, record) }
            assertNoPlaintextIn(error)
        }

    @Test
    fun `an authentic envelope with bad padding or an unreadable payload is refused`() =
        runTest {
            val keys = keys()
            val rkey = SealedRecordCodec.sealedRkey(keys, entry.collection, entry.rkey)
            val badPadding = ByteArray(512).also { it[1] = 1 }
            val notCbor = SealedPadding.pad(byteArrayOf(0xff.toByte(), 0x00))
            val notPayload = SealedPadding.pad(LexiconData.encodeToDagCbor(JsonPrimitive.serializer(), JsonPrimitive("Pancakes")))
            listOf(badPadding, notCbor, notPayload).forEach { plaintext ->
                val record = SealedRecordCodec.encrypt(keys, space, owner, rkey, plaintext)
                val error = assertFailsWith<SealedRecordException> { SealedRecordCodec.open(keys, space, owner, rkey, record) }
                assertNoPlaintextIn(error)
            }
        }

    @Test
    fun `a record the envelope can't encode is refused without echoing it`() =
        runTest {
            val withFloat = entry.copy(record = buildJsonObject { put("mood", JsonPrimitive(0.75)) })
            val error = assertFailsWith<SealedRecordException> { SealedRecordCodec.seal(keys(), space, owner, withFloat) }
            assertTrue("0.75" !in generateSequence<Throwable>(error) { it.cause }.joinToString { it.message.orEmpty() })
        }

    private fun assertNoPlaintextIn(error: Throwable) {
        val messages = generateSequence(error) { it.cause }.joinToString { it.message.orEmpty() }
        listOf("Pancakes", "journal", "3k2a").forEach { assertTrue(it !in messages, "$it leaked into: $messages") }
    }
}
