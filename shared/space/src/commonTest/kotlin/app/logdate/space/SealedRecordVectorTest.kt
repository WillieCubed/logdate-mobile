package app.logdate.space

import app.logdate.lexicon.app.logdate.crypto.Sealed
import app.logdate.lexicon.app.logdate.crypto.SealedPayload
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.lexicon.data.Bytes
import studio.hypertext.atproto.lexicon.data.LexiconData
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A known answer for the whole sealed envelope, so a change that breaks sealing and opening the
 * same way (the additional data, the padding layout, the length's byte order, the DAG-CBOR
 * payload) still fails. Round trips can't catch that; another client would.
 *
 * The vector comes from this Python script, which builds every part itself, DAG-CBOR included,
 * with only the `cryptography` package (`pip install cryptography`):
 *
 * ```python
 * import base64, hashlib, hmac
 * from cryptography.hazmat.primitives import hashes
 * from cryptography.hazmat.primitives.ciphers.aead import AESGCM
 * from cryptography.hazmat.primitives.kdf.hkdf import HKDF
 *
 * def cbor_head(major, n):
 *     if n < 24: return bytes([major << 5 | n])
 *     if n < 256: return bytes([major << 5 | 24, n])
 *     return bytes([major << 5 | 25]) + n.to_bytes(2, "big")
 *
 * def dag_cbor(value):  # text strings and maps with text keys, keys sorted by length then bytes
 *     if isinstance(value, str):
 *         raw = value.encode()
 *         return cbor_head(3, len(raw)) + raw
 *     keys = sorted(value, key=lambda k: (len(k.encode()), k.encode()))
 *     return cbor_head(5, len(keys)) + b"".join(dag_cbor(k) + dag_cbor(value[k]) for k in keys)
 *
 * def hkdf(ikm, info):
 *     return HKDF(hashes.SHA256(), 32, b"app.logdate.space.personal", info.encode()).derive(ikm)
 *
 * def b32(raw):
 *     return base64.b32encode(raw).decode().lower().rstrip("=")
 *
 * identity = bytes(range(32))
 * content_key = hkdf(identity, "sealed-record v1")
 * index_key = hkdf(identity, "sealed-rkey v1")
 * author = "did:plc:owner"
 * space = f"at://{author}/space/app.logdate.space.personal/self"
 * payload = {
 *     "collection": "app.logdate.journal.entry",
 *     "rkey": "3k2a",
 *     "record": {"$type": "app.logdate.journal.entry", "text": "Pancakes at the lake"},
 * }
 * sealed_rkey = b32(hmac.new(index_key, b"app.logdate.journal.entry/3k2a", hashlib.sha256).digest())[:26]
 * encoded = dag_cbor(payload)
 * size = 512
 * while size < len(encoded) + 4: size *= 2
 * padded = len(encoded).to_bytes(4, "big") + encoded + bytes(size - 4 - len(encoded))
 * nonce = bytes(range(0xA0, 0xAC))
 * aad = f"app.logdate.crypto.sealed|1|{space}|{author}|{sealed_rkey}".encode()
 * print("dagcbor", encoded.hex())
 * print("ciphertext", AESGCM(content_key).encrypt(nonce, padded, aad).hex())
 * ```
 */
class SealedRecordVectorTest {
    private val identityKey = ByteArray(32) { it.toByte() }
    private val author = "did:plc:owner"
    private val space = SpaceRef.personal(author)
    private val nonce = ByteArray(12) { (0xa0 + it).toByte() }
    private val payload =
        SealedPayload(
            collection = "app.logdate.journal.entry",
            rkey = "3k2a",
            record =
                buildJsonObject {
                    put("\$type", "app.logdate.journal.entry")
                    put("text", "Pancakes at the lake")
                },
        )

    @Test
    fun `the payload encodes to the known DAG-CBOR bytes`() {
        assertEquals(DAG_CBOR, LexiconData.encodeToDagCbor(SealedPayload.serializer(), payload).toHex())
    }

    @Test
    fun `sealing with a fixed nonce produces the known envelope`() =
        runTest {
            val keys = SealingKeys.personal(identityKey)
            val write = SealedRecordCodec.sealWithNonce(keys, space, author, payload, nonce)
            assertEquals(SEALED_RKEY, write.rkey)
            assertEquals(1L, write.record.version)
            assertEquals("rjbotc7k4vxiy", write.record.keyId)
            assertEquals(
                nonce.toHex(),
                write.record.nonce
                    .toByteArray()
                    .toHex(),
            )
            assertEquals(
                CIPHERTEXT,
                write.record.ciphertext
                    .toByteArray()
                    .toHex(),
            )
        }

    @Test
    fun `the known envelope opens to the payload`() =
        runTest {
            val keys = SealingKeys.personal(identityKey)
            val record = Sealed(ciphertext = Bytes(CIPHERTEXT.hexToBytes()), keyId = "rjbotc7k4vxiy", nonce = Bytes(nonce), version = 1)
            assertEquals(payload, SealedRecordCodec.open(keys, space, author, SEALED_RKEY, record))
        }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val SEALED_RKEY = "n4mi5n4etheqzgucpsng6icwjf"
        const val DAG_CBOR =
            "a364726b657964336b3261667265636f7264a264746578747450616e63616b657320617420746865206c616b6565247479706578196170702e" +
                "6c6f67646174652e6a6f75726e616c2e656e7472796a636f6c6c656374696f6e78196170702e6c6f67646174652e6a6f75726e616c2e" +
                "656e747279"
        val CIPHERTEXT =
            listOf(
                "326d4bab3add6f215ef65fbe2baf3f308793f90c9ddf58019c8e06fe6f02a2a58947e4113204f03115195ee06e08698981e0c793a693b536",
                "b94c6af9ff4749c882b1d7ed6fd5d0bb82013dbb2f145f0dfac73e6cad91df24decb46dd8fdf228e05b17a05ecd5eeef2ed1fd7c9f477d81",
                "2f09d12bdc5b08347812054b0c50ba382fd2c929e29baeff877d32504356e7fb5c6cb28a5f24ba931cfe1eb65d5ed9970d0e6e4e79fc75ab",
                "7c1c5f06bb944363f57e78ad379337957cfbb96b840d94d48fb84aab6ec24029659db2d57de8c1f3b8d8d011b6b64a25094af063f7e07562",
                "4e3aced79843a349c4542164b65cb0839fa4cfd6cd6e98897ec4d40ce858b877e66f4e00ac041995bb929edc5a7cf411342e43b8d96bc904",
                "4544ee5706c7913bbec51625924c83e5236e0c7b3ca367f68194a90a8804846d806bf514d0bb80cb540b3681e1e76da4ce9b5fba4cd89197",
                "0fc08e62e0ff4743463fda3f66054fc95067fa0a48619fc1113177a450e01774714131b6577156be26080caf484a21af69bb216f68ad553a",
                "0b59f488681b0977649b03cdf133f94efea7052ec1d7fe87248b373ba1b7eb8c46d982391859647652ccd0c1591ac781dec13c645e427869",
                "7184296e0916541dac798771a9e0ed54fd2956b4247cf6fe893f479c25c1313ccc761ec041a23a99f3a3f6878a54162dab582b6c8b2e7f73",
                "f29731deaa713ca0233223fe97b733505affd21af3f0e130",
            ).joinToString("")
    }
}
