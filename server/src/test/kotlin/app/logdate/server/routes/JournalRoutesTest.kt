package app.logdate.server.routes

import app.logdate.server.module
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class JournalRoutesTest {
    @Test
    fun testJournalRoutesRequireAuthAndEnforceMethods() =
        testApplication {
            application { module() }

            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/journals").status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/journals/test").status)
            assertEquals(HttpStatusCode.MethodNotAllowed, client.post("/api/v1/journals").status)
            assertEquals(HttpStatusCode.Unauthorized, client.put("/api/v1/journals/test").status)
            assertEquals(HttpStatusCode.Unauthorized, client.delete("/api/v1/journals/test").status)
        }
}
