package studio.hypertext.atproto.plc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlcOperationsTest {
    @Test
    fun `atprotoGenesis builds canonical unsigned plc operation`() {
        val operation =
            PlcOperations.atprotoGenesis(
                handle = "Alice.LogDate.App.",
                pdsServiceEndpoint = "https://pds.logdate.app/",
                signingKeyDidKey = "did:key:zSigningKey",
            )

        assertEquals(null, operation.prev)
        assertEquals(listOf("at://alice.logdate.app"), operation.alsoKnownAs)
        assertEquals(listOf("did:key:zSigningKey"), operation.rotationKeys)
        assertEquals("did:key:zSigningKey", operation.verificationMethods["atproto"])
        assertEquals(
            PlcService(
                type = "AtprotoPersonalDataServer",
                endpoint = "https://pds.logdate.app",
            ),
            operation.services["atproto_pds"],
        )
    }

    @Test
    fun `atprotoGenesis accepts explicit rotation keys and rejects invalid inputs`() {
        val operation =
            PlcOperations.atprotoGenesis(
                handle = "bob.logdate.app",
                pdsServiceEndpoint = "https://logdate.app",
                signingKeyDidKey = "did:key:zAtproto",
                rotationKeys = listOf("did:key:zRotationA", "did:key:zRotationB"),
            )

        assertEquals(listOf("did:key:zRotationA", "did:key:zRotationB"), operation.rotationKeys)

        assertFailsWith<IllegalArgumentException> {
            PlcOperations.atprotoGenesis(
                handle = "invalid handle",
                pdsServiceEndpoint = "https://logdate.app",
                signingKeyDidKey = "did:key:zAtproto",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PlcOperations.atprotoGenesis(
                handle = "carol.logdate.app",
                pdsServiceEndpoint = "http://logdate.app",
                signingKeyDidKey = "did:key:zAtproto",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PlcOperations.atprotoGenesis(
                handle = "carol.logdate.app",
                pdsServiceEndpoint = "https://logdate.app",
                signingKeyDidKey = "did:key:zAtproto",
                rotationKeys = emptyList(),
            )
        }
    }
}
