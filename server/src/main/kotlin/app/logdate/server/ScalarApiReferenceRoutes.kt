package app.logdate.server

import com.scalar.maven.core.ScalarHtmlRenderer
import com.scalar.maven.core.ScalarProperties
import com.scalar.maven.core.config.ScalarAgentOptions
import com.scalar.maven.core.enums.ScalarLayout
import com.scalar.maven.core.enums.ScalarTheme
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route

private val scalarProperties =
    ScalarProperties().apply {
        url = "/openapi.json"
        path = "/docs"
        pageTitle = "LogDate API Reference"
        theme = ScalarTheme.DEFAULT
        layout = ScalarLayout.MODERN
        setHideTestRequestButton(false)
        setPersistAuth(false)
        setTelemetry(false)
        setWithDefaultFonts(false)
        setShowOperationId(true)
        agent = ScalarAgentOptions().apply { disabled = true }
    }

private val scalarHtml: String by lazy { ScalarHtmlRenderer.render(scalarProperties) }
private val scalarJavaScript: ByteArray by lazy { ScalarHtmlRenderer.getScalarJsContent() }

internal fun Route.scalarApiReferenceRoutes() {
    get("/docs", { hidden = true }) { call.respondText(scalarHtml, ContentType.Text.Html) }
    get("/docs/scalar.js", { hidden = true }) {
        call.respondBytes(scalarJavaScript, ContentType.Application.JavaScript)
    }
}
