package app.logdate.server.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AADBuilderTest {
    @Test
    fun `aad builder generates deterministic media and backup context`() {
        val media = AADBuilder.forMedia(userId = "user-1", mediaId = "media-1", contentId = "content-1")
        val backup = AADBuilder.forBackup(userId = "user-1", backupId = "backup-1")

        val mediaText = media.decodeToString()
        val backupText = backup.decodeToString()
        assertTrue(mediaText.contains("type=MEDIA"))
        assertTrue(mediaText.contains("contentId=content-1"))
        assertEquals("type=BACKUP|v=1|userId=user-1|backupId=backup-1", backupText)
    }
}
