package app.logdate.client.domain.restore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArchiveRootTest {
    @Test
    fun markerAtTheTopLevelMeansNoPrefix() {
        val names = listOf("metadata.json", "journals.json", "media/a.jpg")

        assertEquals("", ArchiveRoot.find(names, "metadata.json"))
    }

    @Test
    fun markerInsideOneWrapperFolderReturnsThatFolder() {
        val names = listOf("My Export/", "My Export/metadata.json", "My Export/media/a.jpg")

        assertEquals("My Export/", ArchiveRoot.find(names, "metadata.json"))
    }

    @Test
    fun macOsResourceForkEntriesAreNotMistakenForAWrapper() {
        val names =
            listOf(
                "My Export/metadata.json",
                "My Export/journals.json",
                "__MACOSX/My Export/._metadata.json",
            )

        assertEquals("My Export/", ArchiveRoot.find(names, "metadata.json"))
    }

    @Test
    fun aTopLevelMarkerWinsOverAFolderThatAlsoHoldsOne() {
        val names = listOf("metadata.json", "older/metadata.json")

        assertEquals("", ArchiveRoot.find(names, "metadata.json"))
    }

    @Test
    fun twoFoldersHoldingTheMarkerIsAmbiguous() {
        val names = listOf("a/metadata.json", "b/metadata.json")

        assertNull(ArchiveRoot.find(names, "metadata.json"))
    }

    @Test
    fun aMarkerNestedTwoFoldersDeepIsNotFound() {
        val names = listOf("outer/inner/metadata.json")

        assertNull(ArchiveRoot.find(names, "metadata.json"))
    }

    @Test
    fun anArchiveWithoutTheMarkerHasNoRoot() {
        val names = listOf("notes.json", "media/a.jpg")

        assertNull(ArchiveRoot.find(names, "metadata.json"))
    }

    @Test
    fun aFileNamedLikeTheMarkerInsideMediaDoesNotMakeMediaTheRoot() {
        val names = listOf("journals.json", "media/metadata.json")

        assertNull(ArchiveRoot.find(names, "metadata.json"))
    }

    @Test
    fun aStrayTopLevelFileBesideTheWrapperFolderDisqualifiesIt() {
        val names = listOf("notes.txt", "My Export/metadata.json")

        assertNull(ArchiveRoot.find(names, "metadata.json"))
    }

    @Test
    fun finderMetadataBesideTheWrapperFolderIsIgnored() {
        val names = listOf(".DS_Store", "My Export/metadata.json", "My Export/.DS_Store")

        assertEquals("My Export/", ArchiveRoot.find(names, "metadata.json"))
    }
}
