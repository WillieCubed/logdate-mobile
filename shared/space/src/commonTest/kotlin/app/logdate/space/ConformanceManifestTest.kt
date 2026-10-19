package app.logdate.space

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The suite's manifest says which version of the fixture shapes it uses and lists every file. A
 * copy this module doesn't understand, or a sealed file it never reads, must fail here instead of
 * being skipped.
 */
class ConformanceManifestTest {
    @Test
    fun `the suite is a version this module understands`() {
        assertEquals(ConformanceFixtures.SUPPORTED_VERSION, ConformanceFixtures.manifest.int("version"))
    }

    @Test
    fun `every sealed file in the manifest is read by these tests`() {
        val listed =
            ConformanceFixtures.manifest
                .getValue("files")
                .jsonArray
                .map { it.jsonPrimitive.content }
        assertEquals(ConformanceFixtures.sealedFiles.sorted(), listed.filter { it.startsWith("sealed/") }.sorted())
        assertEquals(ConformanceFixtures.sealedFiles.sorted(), SealedConformanceFixtures.names.filter { it.startsWith("sealed/") }.sorted())
    }
}
