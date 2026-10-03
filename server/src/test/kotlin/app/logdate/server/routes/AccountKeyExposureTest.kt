package app.logdate.server.routes

import app.logdate.server.module
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountKeyExposureTest {
    @Test
    fun `application does not expose server recoverable journal keys`() =
        testApplication {
            application { module() }
            assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/account/keys").status)
            assertEquals(HttpStatusCode.NotFound, client.put("/api/v1/account/keys").status)
        }
}
