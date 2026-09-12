package app.logdate.server

import app.logdate.server.openapi.API_TITLE
import com.scalar.maven.core.ScalarHtmlRenderer
import com.scalar.maven.core.ScalarProperties
import com.scalar.maven.core.authentication.ScalarAuthenticationOptions
import com.scalar.maven.core.config.DefaultHttpClient
import com.scalar.maven.core.config.ScalarAgentOptions
import com.scalar.maven.core.enums.DocumentDownloadType
import com.scalar.maven.core.enums.ScalarClient
import com.scalar.maven.core.enums.ScalarLayout
import com.scalar.maven.core.enums.ScalarTarget
import com.scalar.maven.core.enums.ScalarTheme
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route

private const val DOCS_PATH = "/docs"
private const val FAVICON_PATH = "/favicon.svg"
private const val PAGE_DESCRIPTION =
    "Every LogDate Cloud endpoint with examples, error codes and a Try-it panel. " +
        "Sync entries, journals and media; manage accounts, passkeys and AT Protocol identity."

private val scalarProperties =
    ScalarProperties().apply {
        url = "/openapi.json"
        path = DOCS_PATH
        pageTitle = "$API_TITLE Reference"
        theme = ScalarTheme.DEFAULT
        layout = ScalarLayout.MODERN
        favicon = FAVICON_PATH
        metadata =
            mapOf(
                "title" to "$API_TITLE Reference",
                "description" to PAGE_DESCRIPTION,
                "ogTitle" to "$API_TITLE Reference",
                "ogDescription" to PAGE_DESCRIPTION,
            )
        // Bearer tokens are what almost every reader has; preselect that scheme in the Try-it
        // panel so the first request they send is not a 401.
        authentication = ScalarAuthenticationOptions().apply { setPreferredSecurityScheme("bearerAuth") }
        defaultHttpClient = DefaultHttpClient(ScalarTarget.SHELL, ScalarClient.CURL)
        documentDownloadType = DocumentDownloadType.BOTH
        setHideModels(false)
        setHideTestRequestButton(false)
        setExpandAllResponses(false)
        setPersistAuth(false)
        setTelemetry(false)
        setWithDefaultFonts(false)
        setShowOperationId(true)
        agent = ScalarAgentOptions().apply { disabled = true }
    }

private val scalarHtml: String by lazy { ScalarHtmlRenderer.render(scalarProperties) }
private val scalarJavaScript: ByteArray by lazy { ScalarHtmlRenderer.getScalarJsContent() }
private val faviconSvg: ByteArray by lazy {
    val resource =
        Application::class.java.classLoader.getResource("public/favicon.svg")
            ?: error("public/favicon.svg missing from server resources")
    resource.readBytes()
}

internal fun Route.scalarApiReferenceRoutes() {
    get(DOCS_PATH, { hidden = true }) { call.respondText(scalarHtml, ContentType.Text.Html) }
    get("$DOCS_PATH/scalar.js", { hidden = true }) {
        call.respondBytes(scalarJavaScript, ContentType.Application.JavaScript)
    }
    get(FAVICON_PATH, { hidden = true }) { call.respondBytes(faviconSvg, ContentType.Image.SVG) }
}
