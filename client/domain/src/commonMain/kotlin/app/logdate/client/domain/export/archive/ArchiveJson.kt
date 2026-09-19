package app.logdate.client.domain.export.archive

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * The JSON settings for every file in a 2.0 archive.
 *
 * Nulls are left out instead of written as `null`, so a missing key always means "not known" and a
 * consumer never has to tell the two apart. Defaults are written, so lists are present even when
 * empty. Reading ignores unknown keys: a minor version only adds fields.
 */
@OptIn(ExperimentalSerializationApi::class)
object ArchiveJson {
    /** For files a person may open, indented so they read as text. */
    val document: Json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            explicitNulls = false
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    /** For files with one record per line. */
    val line: Json =
        Json {
            explicitNulls = false
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
}
