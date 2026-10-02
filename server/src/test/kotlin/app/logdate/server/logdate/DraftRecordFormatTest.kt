package app.logdate.server.logdate

import app.logdate.shared.model.sync.DeviceId
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.test.Test
import kotlin.test.assertEquals

class DraftRecordFormatTest {
    @Test
    fun `repo record preserves rich ciphertext and legacy metadata`() {
        val draft =
            LogDateDraft(
                id = "draft-1",
                content = "legacy encrypted text",
                blockTypes = listOf("TEXT", "IMAGE", "AUDIO"),
                journalIds = listOf("journal-1", "journal-2"),
                createdAt = 10L,
                lastUpdated = 20L,
                version = 30L,
                deviceId = DeviceId("phone"),
                encryptedBlocksVersion = 1,
                encryptedBlocks = "LDSE2:encrypted",
            )

        val restored = draft.toRepoJson().toLogDateDraft(RecordKey.require(draft.id), draft.version)

        assertEquals(draft, restored)
    }
}
