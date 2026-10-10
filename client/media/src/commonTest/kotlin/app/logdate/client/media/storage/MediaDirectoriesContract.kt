package app.logdate.client.media.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rules every platform's [MediaDirectories] must follow, from `docs/reference/media-references.md`.
 *
 * Each platform's test source set subclasses this with its own implementation, so every OS is
 * held to the same spec.
 */
abstract class MediaDirectoriesContract {
    protected abstract fun createDirectories(): MediaDirectories

    @Test
    fun `every collection lives in an absolute directory written with forward slashes`() {
        val directories = createDirectories()

        MediaCollection.entries.forEach { collection ->
            val directory = directories.directory(collection)
            assertTrue(directory.startsWith("/") || directory.isDriveLetterPath(), "$collection: $directory")
            assertFalse('\\' in directory, "$collection uses backslashes: $directory")
            assertFalse(directory.endsWith("/"), "$collection: $directory")
        }
    }

    @Test
    fun `collection directories are already canonical`() {
        val directories = createDirectories()

        MediaCollection.entries.forEach { collection ->
            val directory = directories.directory(collection)
            assertEquals(directory, directories.canonicalPath(directory), "$collection")
        }
    }

    @Test
    fun `no collection directory holds another`() {
        val directories = createDirectories()
        val paths = MediaCollection.entries.associateWith(directories::directory)

        paths.forEach { (collection, directory) ->
            paths.filterKeys { it != collection }.forEach { (other, otherDirectory) ->
                assertFalse(
                    directory == otherDirectory || otherDirectory.startsWith("$directory/"),
                    "$collection ($directory) holds $other ($otherDirectory)",
                )
            }
        }
    }

    @Test
    fun `references round-trip through file paths`() {
        val resolver = MediaFileResolver(createDirectories()) { true }

        MediaCollection.entries.forEach { collection ->
            val reference = LocalMediaRef(collection, "nested/IMG 0001 café.jpg").toString()
            val path = resolver.filePath(reference)

            assertEquals(reference, path?.let(resolver::storedReference), "$collection via $path")
        }
    }

    @Test
    fun `paths in this install are never relocated elsewhere`() {
        val directories = createDirectories()

        MediaCollection.entries.forEach { collection ->
            val path = "${directories.directory(collection)}/a.jpg"
            val relocated = directories.pathInCurrentInstall(path)

            assertTrue(relocated == null || relocated == path, "$collection: $path became $relocated")
        }
    }

    private fun String.isDriveLetterPath(): Boolean = length > 2 && this[0].isLetter() && this[1] == ':' && this[2] == '/'
}

/** The contract applied to the fake used by the shared tests, so the suite itself is exercised everywhere. */
class FakeMediaDirectoriesContractTest : MediaDirectoriesContract() {
    override fun createDirectories(): MediaDirectories =
        object : MediaDirectories {
            override fun directory(collection: MediaCollection): String = "/install/now/${collection.id}"
        }
}
