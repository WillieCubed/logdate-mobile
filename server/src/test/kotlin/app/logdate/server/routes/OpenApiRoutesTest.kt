package app.logdate.server.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenApiRoutesTest {
    @Test
    fun `openapi routes return server error when source cannot render`() =
        testApplication {
            application {
                routing {
                    openApiRoutes(
                        baseDoc =
                            OpenApiDoc
                                .Builder()
                                .apply {
                                    info =
                                        OpenApiInfo(
                                            title = "Test",
                                            version = "1.0.0",
                                        )
                                }.build(),
                        jsonSource = OpenApiDocSource.FirstOf(OpenApiDocSource.File("missing-openapi-doc.json")),
                        yamlSource = OpenApiDocSource.Text("openapi: 3.1.0", ContentType.Application.Yaml),
                    )
                }
            }

            val jsonResponse = client.get("/openapi.json")
            assertEquals(HttpStatusCode.NotAcceptable, jsonResponse.status)

            val yamlResponse = client.get("/openapi.yaml")
            assertEquals(HttpStatusCode.OK, yamlResponse.status)
            assertTrue(yamlResponse.bodyAsText().contains("openapi: 3.1.0"))
        }
}
