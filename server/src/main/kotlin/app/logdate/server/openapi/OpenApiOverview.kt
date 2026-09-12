package app.logdate.server.openapi

/**
 * The guide that opens the API reference. Authored as Markdown in `resources/openapi/overview.md`
 * so it can be read and reviewed like any other document; numbers are filled in from
 * [ApiLimits] at load time.
 */
internal object OpenApiOverview {
    private const val RESOURCE = "openapi/overview.md"

    val markdown: String by lazy {
        val resource =
            OpenApiOverview::class.java.classLoader.getResource(RESOURCE)
                ?: error("$RESOURCE missing from server resources")
        renderApiText(resource.readText())
    }
}
