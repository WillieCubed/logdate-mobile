package app.logdate.space

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Known answers for the personal space's key schedule, read from `conformance/sealed/key-schedule.json`.
 * Every LogDate client must derive these exact values from the same identity key, or it can't open
 * what another client sealed.
 *
 * - content key = HKDF-SHA256(ikm = identity key, salt = "app.logdate.space.personal", info = "sealed-record v1"), 32 bytes
 * - index key = HKDF-SHA256(ikm = identity key, salt = "app.logdate.space.personal", info = "sealed-rkey v1"), 32 bytes
 * - keyId = base32(HMAC-SHA256(content key, "keyId")[0 until 8])
 * - sealed rkey = base32(HMAC-SHA256(index key, "{collection}/{rkey}")).take(26)
 *
 * base32 is RFC 4648, lowercase, without padding.
 */
class SealingKeysTest {
    @Test
    fun `the personal key schedule matches the known answers`() =
        runTest {
            val vectors = ConformanceFixtures.keySchedule.list("vectors")
            assertTrue(vectors.size > 1)
            vectors.forEach { vector ->
                val name = vector.string("name")
                val keys = SealingKeys.personal(vector.string("identityKey").hexToBytes())
                assertEquals(vector.string("contentKey"), keys.contentKey.toHex(), "$name: content key")
                assertEquals(vector.string("indexKey"), keys.indexKey.toHex(), "$name: index key")
                assertEquals(vector.string("keyId"), keys.keyId, "$name: key id")
                vector.list("sealedRkeys").forEach { entry ->
                    val inner = "${entry.string("collection")}/${entry.string("rkey")}"
                    assertEquals(
                        entry.string("sealedRkey"),
                        SealedRecordCodec.sealedRkey(keys, entry.string("collection"), entry.string("rkey")),
                        "$name: sealed key of $inner",
                    )
                }
            }
        }

    @Test
    fun `an identity key of the wrong size is refused`() =
        runTest {
            val sizes = ConformanceFixtures.keySchedule.ints("refusedIdentityKeyLengths")
            assertTrue(sizes.isNotEmpty())
            sizes.forEach { size -> assertFailsWith<IllegalArgumentException>("$size bytes") { SealingKeys.personal(ByteArray(size)) } }
        }
}
