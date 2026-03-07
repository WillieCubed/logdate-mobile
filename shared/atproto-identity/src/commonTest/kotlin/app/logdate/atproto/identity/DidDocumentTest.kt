package app.logdate.atproto.identity

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DidDocumentTest {
    private val json: Json = Json { encodeDefaults = true }

    @Test
    fun serializesAndDeserializesDidDocument() {
        val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
        val document =
            DidDocument(
                id = did,
                alsoKnownAs = listOf("at://alice.test"),
                verificationMethod =
                    listOf(
                        VerificationMethod(
                            id = "#atproto",
                            type = "Multikey",
                            controller = did,
                            publicKeyMultibase = "zDnaeh",
                        ),
                    ),
                service =
                    listOf(
                        Service(
                            id = "#pds",
                            type = "AtprotoPersonalDataServer",
                            serviceEndpoint = "https://example.com",
                        ),
                    ),
            )

        val encoded = json.encodeToString(document)
        val decoded = json.decodeFromString<DidDocument>(encoded)

        assertEquals(document, decoded)
        assertEquals(listOf("https://www.w3.org/ns/did/v1"), decoded.context)
        assertEquals("#atproto", decoded.verificationMethod.single().id)
        assertEquals("#pds", decoded.service.single().id)
    }
}
