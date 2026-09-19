package app.logdate.client.domain.export.archive

import app.logdate.client.domain.export.archive.support.ArchiveExportFixture
import app.logdate.client.domain.export.archive.support.InMemoryArchiveContainer
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks a real export against the schemas it includes, using only files from inside the archive, which
 * is the position a person or program that receives it is in.
 */
class ExportArchiveConformanceTest : ArchiveExportFixture() {
    private val registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)

    private suspend fun exported(): InMemoryArchiveContainer {
        val container = InMemoryArchiveContainer()
        useCase().export(ArchiveExportOptions(), container).toList()
        return container
    }

    private fun errors(
        schemaText: String,
        instance: String,
    ): List<String> = registry.getSchema(schemaText).validate(instance, InputFormat.JSON).map { it.message }

    @Test
    fun `every data file validates against the schema the archive includes for it`() =
        runTest {
            val container = exported()
            val manifest = ArchiveJson.document.decodeFromString(ArchiveManifest.serializer(), container.text("manifest.json"))

            assertEquals(
                emptyList(),
                errors(container.text("schema/manifest.schema.json"), container.text("manifest.json")),
                "manifest.json",
            )
            val described = manifest.contents.filter { it.schema != null }
            assertTrue(described.size >= 5, "the export should describe its data files: ${described.map { it.path }}")
            described.forEach { content ->
                val schema = container.text(checkNotNull(content.schema).value)
                val text = container.text(content.path.value)
                val problems =
                    if (content.mediaType == ArchiveLayout.JSON_LINES_MEDIA_TYPE) {
                        text.lines().filter { it.isNotBlank() }.flatMap { errors(schema, it) }
                    } else {
                        errors(schema, text)
                    }
                assertEquals(emptyList(), problems, content.path.value)
            }
        }

    @Test
    fun `everything the manifest lists is in the archive`() =
        runTest {
            val container = exported()
            val manifest = ArchiveJson.document.decodeFromString(ArchiveManifest.serializer(), container.text("manifest.json"))

            manifest.contents.forEach { content ->
                assertTrue(container.has(content.path.value), "${content.path} is listed but missing")
                content.schema?.let { assertTrue(container.has(it.value), "$it is referenced but missing") }
            }
        }
}
