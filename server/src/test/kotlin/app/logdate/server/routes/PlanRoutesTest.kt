package app.logdate.server.routes

import app.logdate.server.module
import app.logdate.shared.model.EntitlementTierWire
import app.logdate.shared.model.PlanCatalogResponse
import app.logdate.shared.model.PlanOption
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the publicly readable plan catalog.
 *
 * The catalog has to be reachable without an account, because it is shown while someone is
 * deciding whether to create one.
 */
class PlanRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `plans are readable without authentication`() =
        testApplication {
            application { module(isDatabaseAvailable = false) }

            val response = client.get("/api/v1/plans")

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `a deployment without billing answers with an empty catalog rather than an error`() =
        testApplication {
            application { module(isDatabaseAvailable = false) }

            val response = client.get("/api/v1/plans")
            val body = json.decodeFromString<PlanCatalogResponse>(response.bodyAsText())

            // Self-hosted servers genuinely sell nothing. Clients read an empty list as "hide plan
            // selection", so this must not be a failure status.
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.success)
            assertTrue(body.data.isEmpty())
        }

    @Test
    fun `a plan is purchasable only when it carries a store product`() {
        val free =
            PlanOption(
                id = "free",
                name = "Free",
                tier = EntitlementTierWire.FREE,
                storageBytesLimit = 1_000_000_000L,
                backupCountLimit = 3,
            )
        val standard =
            free.copy(
                id = "standard",
                name = "Standard",
                tier = EntitlementTierWire.STANDARD,
                playProductId = "logdate_standard_monthly",
            )

        assertFalse(free.isPurchasable)
        assertTrue(standard.isPurchasable)
    }
}
