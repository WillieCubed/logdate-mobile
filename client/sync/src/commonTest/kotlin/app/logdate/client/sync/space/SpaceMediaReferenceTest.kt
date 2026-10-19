package app.logdate.client.sync.space

import app.logdate.lexicon.app.logdate.DefsAspectRatio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SpaceMediaReferenceTest {
    private val contentId = Uuid.parse("6f1c2c9e-4b1a-4c55-9a39-2f0d6c3b8e71")

    /** The raw-codec CID of the bytes "hello world", computed outside this codebase. */
    private val helloWorldCid = "bafkreifzjut3te2nhyekklss27nh3k72ysco7y32koao5eei66wof36n5e"

    @Test
    fun `the reference is pinned to the shape the sync engine treats as a remote file`() {
        val reference = SpaceMediaReference(helloWorldCid, contentId, "image/jpeg", storedSize = 4_131, size = 4_096)

        assertEquals(
            "https://space.logdate.invalid/media/$helloWorldCid" +
                "?contentId=6f1c2c9e-4b1a-4c55-9a39-2f0d6c3b8e71&type=image%2Fjpeg&storedSize=4131&size=4096",
            reference.format(),
        )
        // SyncMediaTransfer treats only http(s) references as remote, and reads the id after "/media/".
        assertTrue(reference.format().startsWith("https://"))
        assertEquals(helloWorldCid, reference.format().substringBefore('?').substringAfterLast("/media/"))
    }

    @Test
    fun `a reference round-trips through its text and through a record's media`() {
        val withSize = SpaceMediaReference(helloWorldCid, contentId, "video/mp4", storedSize = 4_131, size = 4_096)
        val withoutSize = withSize.copy(size = null)

        assertEquals(withSize, SpaceMediaReference.parse(withSize.format()))
        assertEquals(withoutSize, SpaceMediaReference.parse(withoutSize.format()))
        val media = withSize.toMedia("app.logdate.journal.entry/n1", alt = "A lake")
        assertEquals(SpaceMediaReference.STORED_MIME_TYPE, media.blob.mimeType)
        assertEquals(4_131, media.blob.size)
        assertEquals("video/mp4", media.mimeType)
        assertEquals(4_096, media.size)
        assertEquals("A lake", media.alt)
        assertEquals(withSize.copy(alt = "A lake"), SpaceMediaReference.fromMedia(contentId, media))
    }

    @Test
    fun `a file's text alternative and aspect ratio stay with its reference`() {
        val reference =
            SpaceMediaReference(
                helloWorldCid,
                contentId,
                "image/jpeg",
                storedSize = 4_131,
                size = 4_096,
                alt = "A lake & a pier, 100% calm; \"still\" 🌅",
                aspectRatio = DefsAspectRatio(height = 3, width = 4),
            )

        assertEquals(reference, SpaceMediaReference.parse(reference.format()))
        val media = reference.toMedia("app.logdate.journal.entry/n1")
        assertEquals(reference.alt, media.alt)
        assertEquals(DefsAspectRatio(height = 3, width = 4), media.aspectRatio)
        assertEquals(reference, SpaceMediaReference.fromMedia(contentId, media))
        assertNull(SpaceMediaReference.parse(reference.format().replace("ratio=4x3", "ratio=4x0")))
    }

    @Test
    fun `anything else is not a personal-space reference`() {
        val valid = SpaceMediaReference(helloWorldCid, contentId, "image/png", 10).format()

        assertNull(SpaceMediaReference.parse("https://cloud.test/api/v1/media/m1"))
        assertNull(SpaceMediaReference.parse(valid.replace("https://", "http://")))
        assertNull(SpaceMediaReference.parse("logdate-media://photos/a.jpg"))
        assertNull(SpaceMediaReference.parse(valid.replace(helloWorldCid, "QmNotABase32Cid")))
        assertNull(SpaceMediaReference.parse(valid.replace("storedSize=10", "storedSize=-1")))
        assertNull(SpaceMediaReference.parse(valid.substringBefore("&type")))
    }

    @Test
    fun `the resolver names uploaded files and asks to retry for files not yet uploaded`() {
        val uploaded = SpaceMediaReference(helloWorldCid, contentId, "audio/mp4", 20, 4).format()

        assertEquals(helloWorldCid, SpaceMediaResolver.blobCidFor(uploaded, "app.logdate.journal.entry/n1"))
        assertEquals(
            helloWorldCid,
            SpaceMediaResolver
                .mediaFor(uploaded, "app.logdate.journal.entry/n1")
                .blob.ref.cid.value,
        )
        val notYet =
            assertFailsWith<SpaceRecordMappingException> { SpaceMediaResolver.mediaFor("file:///a.m4a", "app.logdate.journal.entry/n1") }
        assertTrue(notYet.retryable)
        val media = SpaceMediaReference.parse(uploaded)!!.toMedia("o")
        assertEquals(uploaded, SpaceMediaResolver.downloaded(contentId, media))
    }
}
