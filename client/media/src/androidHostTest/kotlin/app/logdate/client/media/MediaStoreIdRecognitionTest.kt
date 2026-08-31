package app.logdate.client.media

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaStoreIdRecognitionTest {
    @Test
    fun `row ids are recognised`() {
        assertTrue(looksLikeMediaStoreId("1000000042"))
        assertTrue(looksLikeMediaStoreId("7"))
    }

    @Test
    fun `file paths are not treated as row ids`() {
        assertFalse(
            looksLikeMediaStoreId("/data/user/0/co.reasonabletech.logdate/files/audio_notes/recording.m4a"),
            "appending a path to a MediaStore collection URI makes it throw rather than answer",
        )
        assertFalse(looksLikeMediaStoreId("recording.m4a"))
        assertFalse(looksLikeMediaStoreId(""))
    }
}
