package app.logdate.space

import app.logdate.lexicon.app.logdate.crypto.Sealed
import app.logdate.lexicon.app.logdate.crypto.SealedPayload
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.lexicon.data.LexiconData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Known answers for whole sealed envelopes, read from `conformance/sealed/envelopes.json`, so a
 * change that breaks sealing and opening the same way (the additional data, the padding layout,
 * the length's byte order, the DAG-CBOR payload) still fails. Round trips can't catch that;
 * another client would.
 *
 * Each envelope was built twice before it was accepted: by `tools/lexicon-check/conformance.mjs`
 * with Node's crypto, and by `conformance/sealed/reference/verify.py` with Python's
 * `cryptography` package. Run `python3 conformance/sealed/reference/verify.py` to reproduce them.
 */
class SealedEnvelopeConformanceTest {
    @Test
    fun `every payload encodes to the known DAG-CBOR bytes`() =
        runTest {
            ConformanceFixtures.envelopes.forEach { entry ->
                val encoded = LexiconData.encodeToDagCbor(SealedPayload.serializer(), payload(entry))
                assertDigest(entry.obj("dagCbor"), encoded, "${entry.string("name")}: DAG-CBOR")
                assertEquals(entry.int("paddedLength"), SealedPadding.paddedSize(encoded.size), "${entry.string("name")}: padded length")
            }
        }

    // Sealing happens once per envelope: the JDK refuses to encrypt twice under the same key and
    // nonce, and the fixtures fix the nonce.
    @Test
    fun `sealing with a fixed nonce produces the known envelope that opens to the payload`() =
        runTest {
            ConformanceFixtures.envelopes.forEach { entry ->
                val name = entry.string("name")
                val write = seal(entry)
                assertEquals(entry.string("sealedRkey"), write.rkey, "$name: sealed key")
                assertEquals(1L, write.record.version, "$name: version")
                assertEquals(entry.string("keyId"), write.record.keyId, "$name: key id")
                val nonce = write.record.nonce.toByteArray()
                assertEquals(entry.string("nonce"), nonce.toHex(), "$name: nonce")
                assertDigest(entry.obj("ciphertext"), write.record.ciphertext.toByteArray(), "$name: ciphertext")

                val known = entry["envelope"]?.let(::sealed) ?: write.record
                assertEquals(known, write.record, "$name: stored envelope")
                val opened = SealedRecordCodec.open(keys(entry), space(entry), entry.string("author"), entry.string("sealedRkey"), known)
                assertEquals(payload(entry), opened, "$name: opened payload")
            }
        }

    @Test
    fun `an envelope changed in any listed way is refused`() =
        runTest {
            val withRefusals = ConformanceFixtures.envelopes.filter { "refusedEnvelopes" in it }
            assertTrue(withRefusals.isNotEmpty())
            withRefusals.forEach { entry ->
                entry.list("refusedEnvelopes").forEach { refusal ->
                    val envelope = JsonObject(entry.obj("envelope") + (refusal["envelope"] as? JsonObject).orEmpty())
                    assertFailsWith<SealedRecordException>("${entry.string("name")}: ${refusal.string("name")}") {
                        SealedRecordCodec.open(
                            keys(entry),
                            refusal["space"]?.let { space(it.text()) } ?: space(entry),
                            refusal["author"]?.text() ?: entry.string("author"),
                            refusal["sealedRkey"]?.text() ?: entry.string("sealedRkey"),
                            sealed(envelope),
                        )
                    }
                }
            }
        }

    private suspend fun seal(entry: JsonObject): SealedWrite =
        SealedRecordCodec.sealWithNonce(
            keys(entry),
            space(entry),
            entry.string("author"),
            payload(entry),
            entry.string("nonce").hexToBytes(),
        )

    private suspend fun keys(entry: JsonObject): SealingKeys = SealingKeys.personal(entry.string("identityKey").hexToBytes())

    private fun space(entry: JsonObject): SpaceRef = space(entry.string("space"))

    private fun space(uri: String): SpaceRef = requireNotNull(SpaceRef.parse(uri)) { "Not a space URI: $uri" }

    private fun payload(entry: JsonObject): SealedPayload =
        LexiconData.json.decodeFromJsonElement(SealedPayload.serializer(), expanded(entry.obj("payload"), entry.list("expand")))

    private fun sealed(envelope: JsonElement): Sealed = LexiconData.json.decodeFromJsonElement(Sealed.serializer(), envelope)

    private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())

    private suspend fun assertDigest(
        expected: JsonObject,
        actual: ByteArray,
        label: String,
    ) {
        assertEquals(expected.int("length"), actual.size, "$label: length")
        assertEquals(expected.string("sha256"), actual.sha256Hex(), "$label: SHA-256")
        expected["hex"]?.let { assertEquals(it.text(), actual.toHex(), "$label: bytes") }
    }
}
