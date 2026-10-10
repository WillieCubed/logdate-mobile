package app.logdate.ui.media

import app.logdate.client.media.storage.MediaFileResolver
import coil3.map.Mapper
import coil3.request.Options
import okio.Path
import okio.Path.Companion.toPath

/**
 * Lets Coil load stored media strings that name local files.
 *
 * Coil runs an app's mappers before its own, so registering this one in the image loader makes
 * every image request accept `logdate-media://` references and file paths from an earlier install,
 * as well as `file:` URIs in any spelling. Anything that is not a local file (remote URLs, content
 * providers) is left to Coil's own handling.
 */
class LocalMediaImageMapper(
    private val mediaFiles: MediaFileResolver,
) : Mapper<String, Path> {
    override fun map(
        data: String,
        options: Options,
    ): Path? = mediaFiles.filePath(data)?.toPath()
}
