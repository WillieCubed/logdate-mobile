package app.logdate.client.sync.cloud

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** A server error code missing from the allowlist reaches the app as `UNKNOWN_ERROR` and loses its meaning. */
class SafeCloudErrorCodeCoverageTest {
    @Test
    fun `every error code the server documents reaches the app intact`() {
        val docs = File("../../server/src/main/kotlin/app/logdate/server/routes/docs")
        assertTrue(docs.isDirectory, "server route docs not found at ${docs.absolutePath}")
        val documented =
            docs
                .listFiles { file -> file.extension == "kt" }
                .orEmpty()
                .flatMap { file -> ERROR_CASE.findAll(file.readText()).map { it.groupValues[1] }.toList() }
                .toSet()
        assertTrue(documented.isNotEmpty(), "no ErrorCase codes found in ${docs.absolutePath}")
        val missing = documented - SAFE_CLOUD_ERROR_CODES
        assertTrue(missing.isEmpty(), "server error codes missing from SAFE_CLOUD_ERROR_CODES: ${missing.sorted()}")
    }

    private companion object {
        val ERROR_CASE = Regex("""ErrorCase\(\s*"([A-Z][A-Z0-9_]+)"""")
    }
}
