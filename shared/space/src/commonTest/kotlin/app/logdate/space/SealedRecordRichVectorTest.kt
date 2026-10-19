package app.logdate.space

import app.logdate.lexicon.app.logdate.crypto.SealedPayload
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import studio.hypertext.atproto.lexicon.data.LexiconData
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A known answer for an envelope whose record holds every kind of value a record can: positive,
 * negative and 64-bit integers, an array, a nested object, bytes and a CID link. Its text is long
 * enough that the payload passes 64 KiB and pads to a whole multiple of 64 KiB rather than a power
 * of two. [SealedRecordVectorTest] covers a small record in the smallest bucket.
 *
 * The ciphertext is 128 KiB, so the test pins its length and SHA-256 rather than its bytes. The
 * envelope it opens is the one sealing produces, after checking that envelope's hash, so it is
 * the same envelope this script produces. The vector comes from this Python script, which builds
 * every part itself, DAG-CBOR included, with only the `cryptography` package
 * (`pip install cryptography`):
 *
 * ```python
 * import base64, hashlib, hmac
 * from cryptography.hazmat.primitives import hashes
 * from cryptography.hazmat.primitives.ciphers.aead import AESGCM
 * from cryptography.hazmat.primitives.kdf.hkdf import HKDF
 *
 * class Link(str): pass
 *
 * def cbor_head(major, n):
 *     if n < 24: return bytes([major << 5 | n])
 *     for info, width in ((24, 1), (25, 2), (26, 4), (27, 8)):
 *         if n < 1 << (8 * width): return bytes([major << 5 | info]) + n.to_bytes(width, "big")
 *
 * def cid_bytes(cid):  # "b" + lowercase unpadded base32 of the binary CID
 *     raw = cid[1:].upper()
 *     return base64.b32decode(raw + "=" * (-len(raw) % 8))
 *
 * def dag_cbor(value):  # keys sorted by length then bytes; a CID link is tag 42 over 0x00 + CID
 *     if isinstance(value, int):
 *         return cbor_head(0, value) if value >= 0 else cbor_head(1, -1 - value)
 *     if isinstance(value, Link):
 *         return bytes([0xD8, 42]) + dag_cbor(b"\x00" + cid_bytes(value))
 *     if isinstance(value, str):
 *         raw = value.encode()
 *         return cbor_head(3, len(raw)) + raw
 *     if isinstance(value, bytes):
 *         return cbor_head(2, len(value)) + value
 *     if isinstance(value, list):
 *         return cbor_head(4, len(value)) + b"".join(dag_cbor(v) for v in value)
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
 * link = Link("b" + b32(bytes([0x01, 0x71, 0x12, 0x20]) + hashlib.sha256(b"Pancakes at the lake").digest()))
 * phrase = "Pancakes at the lake. "
 * payload = {
 *     "collection": "app.logdate.journal.entry",
 *     "rkey": "3k2b",
 *     "record": {
 *         "$type": "app.logdate.journal.entry",
 *         "mood": -3,
 *         "steps": 12345,
 *         "createdMs": 5000000000123,
 *         "tags": ["lake", "breakfast", 7],
 *         "place": {"name": "The lake", "visits": {"count": 2, "lastYear": 2025}},
 *         "thumbnail": bytes(range(1, 10)),
 *         "photo": link,
 *         "text": phrase * 3200,
 *     },
 * }
 * sealed_rkey = b32(hmac.new(index_key, b"app.logdate.journal.entry/3k2b", hashlib.sha256).digest())[:26]
 * encoded = dag_cbor(payload)
 * size = -(-(len(encoded) + 4) // 65536) * 65536  # past 64 KiB, buckets are multiples of 64 KiB
 * padded = len(encoded).to_bytes(4, "big") + encoded + bytes(size - 4 - len(encoded))
 * nonce = bytes(range(0xB0, 0xBC))
 * aad = f"app.logdate.crypto.sealed|1|{space}|{author}|{sealed_rkey}".encode()
 * ciphertext = AESGCM(content_key).encrypt(nonce, padded, aad)
 * print("link", link)
 * print("rkey", sealed_rkey)
 * print("dagcbor", encoded.hex())
 * print("ciphertext", len(ciphertext), hashlib.sha256(ciphertext).hexdigest())
 * ```
 */
class SealedRecordRichVectorTest {
    private val identityKey = ByteArray(32) { it.toByte() }
    private val author = "did:plc:owner"
    private val space = SpaceRef.personal(author)
    private val nonce = ByteArray(12) { (0xb0 + it).toByte() }
    private val payload =
        SealedPayload(
            collection = "app.logdate.journal.entry",
            rkey = "3k2b",
            record =
                buildJsonObject {
                    put("\$type", "app.logdate.journal.entry")
                    put("mood", -3)
                    put("steps", 12_345)
                    put("createdMs", 5_000_000_000_123L)
                    putJsonArray("tags") {
                        add("lake")
                        add("breakfast")
                        add(7)
                    }
                    putJsonObject("place") {
                        put("name", "The lake")
                        putJsonObject("visits") {
                            put("count", 2)
                            put("lastYear", 2025)
                        }
                    }
                    putJsonObject("thumbnail") { put("\$bytes", "AQIDBAUGBwgJ") }
                    putJsonObject("photo") { put("\$link", LINK) }
                    put("text", PHRASE.repeat(PHRASE_REPEATS))
                },
        )

    @Test
    fun `the payload encodes to the known DAG-CBOR bytes`() {
        val encoded = LexiconData.encodeToDagCbor(SealedPayload.serializer(), payload)
        assertEquals(DAG_CBOR_LENGTH, encoded.size)
        assertEquals(dagCbor(), encoded.toHex())
        assertEquals(131_072, SealedPadding.paddedSize(encoded.size))
    }

    @Test
    fun `sealing with a fixed nonce produces the known envelope that opens to the payload`() =
        runTest {
            val keys = SealingKeys.personal(identityKey)
            val write = SealedRecordCodec.sealWithNonce(keys, space, author, payload, nonce)
            assertEquals(SEALED_RKEY, write.rkey)
            assertEquals("rjbotc7k4vxiy", write.record.keyId)
            val ciphertext = write.record.ciphertext.toByteArray()
            assertEquals(CIPHERTEXT_LENGTH, ciphertext.size)
            assertEquals(CIPHERTEXT_SHA256, sha256(ciphertext).toHex())

            assertEquals(payload, SealedRecordCodec.open(keys, space, author, SEALED_RKEY, write.record))
        }

    private suspend fun sha256(bytes: ByteArray): ByteArray =
        CryptographyProvider.Default
            .get(SHA256)
            .hasher()
            .hash(bytes)

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun dagCbor(): String = DAG_CBOR_BEFORE_TEXT + PHRASE.encodeToByteArray().toHex().repeat(PHRASE_REPEATS) + DAG_CBOR_AFTER_TEXT

    private companion object {
        const val SEALED_RKEY = "ap6hv32266rmajaeifsuukhv5t"
        const val LINK = "bafyreidpai6xycffexlsyxnuh653jnsg4ewa2czatzn6wos7nioh6zd2nm"
        const val PHRASE = "Pancakes at the lake. "
        const val PHRASE_REPEATS = 3200
        const val DAG_CBOR_LENGTH = 70_671

        /** The DAG-CBOR up to and including the text's head (`7a00011300`: a 70,400-byte string). */
        const val DAG_CBOR_BEFORE_TEXT =
            "a364726b657964336b3262667265636f7264a9646d6f6f6422647461677383646c616b6569627265616b666173740764746578747a00011300"

        /** The DAG-CBOR after the text: `$type`, the `photo` link (tag 42), `place`, `steps`, `createdMs`, `thumbnail`, `collection`. */
        const val DAG_CBOR_AFTER_TEXT =
            "65247479706578196170702e6c6f67646174652e6a6f75726e616c2e656e7472796570686f746fd82a582500017112206f023d7c08a525d72c" +
                "5db43fbbb4b646e12c0d0b209e5beb3a5f6a1c7f647a6b65706c616365a2646e616d6568546865206c616b6566766973697473a265636f75" +
                "6e7402686c617374596561721907e965737465707319303969637265617465644d731b0000048c2739507b697468756d626e61696c4901" +
                "02030405060708096a636f6c6c656374696f6e78196170702e6c6f67646174652e6a6f75726e616c2e656e747279"
        const val CIPHERTEXT_LENGTH = 131_088
        const val CIPHERTEXT_SHA256 = "257bd4540cf442f6c74c758748bc259c4700828bed95f37c93631c8c5e416354"
    }
}
