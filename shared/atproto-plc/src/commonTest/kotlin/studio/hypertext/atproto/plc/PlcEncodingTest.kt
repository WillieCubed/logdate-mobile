package studio.hypertext.atproto.plc

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the PLC (Placeholder) DID method encoding and derivation logic.
 *
 * This suite validates the cryptographic and structural requirements for `did:plc`
 * identifiers, specifically:
 * - Deterministic encoding of signed operations.
 * - Correct derivation of the DID string from genesis operations.
 * - Validation of operation integrity (signatures, sequence, and payload).
 *
 * The tests use real-world audit samples to ensure compatibility with the
 * official AT Protocol PLC specification.
 */
class PlcEncodingTest {
    @Test
    fun `signed genesis derives stable plc did from real audit sample`() {
        val signedGenesis = sampleGenesisOperation()

        val unsignedBytes = PlcEncoding.encodeUnsigned(signedGenesis.unsigned())
        val signedBytes = PlcEncoding.encodeSigned(signedGenesis)
        val derivedDid = PlcEncoding.deriveDid(signedGenesis)

        assertTrue(unsignedBytes.isNotEmpty())
        assertTrue(signedBytes.isNotEmpty())
        assertNotEquals(unsignedBytes.decodeToString(), signedBytes.decodeToString())
        assertEquals("did:plc:ewvi7nxzyoun6zhxrhs64oiz", derivedDid.toString())
    }

    @Test
    fun `signed operation encoding is deterministic`() {
        val operation = sampleGenesisOperation()

        val first = PlcEncoding.encodeSigned(operation)
        val second = PlcEncoding.encodeSigned(operation)

        assertContentEquals(first, second)
    }

    @Test
    fun `deriveDid rejects unsigned or non genesis operations`() {
        val signedGenesis = sampleGenesisOperation()

        assertFailsWith<IllegalArgumentException> {
            PlcEncoding.deriveDid(signedGenesis.copy(sig = null))
        }
        assertFailsWith<IllegalArgumentException> {
            PlcEncoding.deriveDid(signedGenesis.copy(prev = "bafy-prev"))
        }
    }

    @Test
    fun `unsigned helper preserves payload and signed helper attaches signature`() {
        val signedGenesis = sampleGenesisOperation()
        val unsigned = signedGenesis.unsigned()
        val resigned = unsigned.signed("sig-value")

        assertEquals(null, unsigned.prev)
        assertEquals(listOf("at://atprotocol.bsky.social"), unsigned.alsoKnownAs)
        assertEquals("sig-value", resigned.sig)
        assertEquals(unsigned.verificationMethods, resigned.verificationMethods)
    }

    private fun sampleGenesisOperation(): PlcOperation =
        PlcOperation(
            sig = "lza4at_jCtGo_TYgL5PC1ZNP7lhF4DV8H50LWHhvdHcB143x1wEwqZ43xvV36Pws6OOnJLJrkibEUFDFqkhIhg",
            prev = null,
            services =
                mapOf(
                    "atproto_pds" to
                        PlcService(
                            type = "AtprotoPersonalDataServer",
                            endpoint = "https://bsky.social",
                        ),
                ),
            alsoKnownAs = listOf("at://atprotocol.bsky.social"),
            rotationKeys =
                listOf(
                    "did:key:zQ3shhCGUqDKjStzuDxPkTxN6ujddP4RkEKJJouJGRRkaLGbg",
                    "did:key:zQ3shpKnbdPx3g3CmPf5cRVTPe1HtSwVn5ish3wSnDPQCbLJK",
                ),
            verificationMethods =
                mapOf(
                    "atproto" to "did:key:zQ3shXjHeiBuRCKmM36cuYnm7YEMzhGnCmCyW92sRJ9pribSF",
                ),
        )
}
