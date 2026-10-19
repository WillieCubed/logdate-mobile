package app.logdate.space

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Known answers for the personal space's key schedule. Every LogDate client must derive these
 * exact values from the same identity key, or it can't open what another client sealed.
 *
 * - content key = HKDF-SHA256(ikm = identity key, salt = "app.logdate.space.personal", info = "sealed-record v1"), 32 bytes
 * - index key = HKDF-SHA256(ikm = identity key, salt = "app.logdate.space.personal", info = "sealed-rkey v1"), 32 bytes
 * - keyId = base32(HMAC-SHA256(content key, "keyId")[0 until 8])
 * - sealed rkey = base32(HMAC-SHA256(index key, "{collection}/{rkey}")).take(26)
 *
 * base32 is RFC 4648, lowercase, without padding.
 */
class SealingKeysTest {
    private val identityKey = ByteArray(32) { it.toByte() }

    @Test
    fun `the personal key schedule matches the known answers`() =
        runTest {
            val keys = SealingKeys.personal(identityKey)
            assertEquals("a468cefb3a8ef131555d994017ef9cb4c62a87db9bf1303cc16e62be0db765d5", keys.contentKey.toHex())
            assertEquals("4de7829b169a2f838d86425e7190546702c1629b3384d2978ebaa3dda082ec38", keys.indexKey.toHex())
            assertEquals("rjbotc7k4vxiy", keys.keyId)
            assertEquals("n4mi5n4etheqzgucpsng6icwjf", SealedRecordCodec.sealedRkey(keys, "app.logdate.journal.entry", "3k2a"))
        }

    @Test
    fun `an identity key of the wrong size is refused`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { SealingKeys.personal(ByteArray(16)) }
        }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
