package app.logdate.client.data.journals

import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableTextBlock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PartialDraftStorageRecoveryTest {
    @Test
    fun `field names inside malformed values do not hide complete draft records`() {
        val draft =
            EditorDraft(
                blocks =
                    listOf(
                        SerializableTextBlock(Uuid.random(), Instant.fromEpochMilliseconds(1), content = "Quoted \"drafts\": [content]"),
                    ),
            )
        val record = Json.encodeToString(draft)
        val damaged = "{\"appliedRecoveries\":\"drafts\",\"drafts\":[$record],"
        assertEquals(listOf(draft), PartialDraftStorageRecovery.recover(Json, damaged).drafts)
        val receipts = "{\"appliedRecoveries\":[\"receipt\"],\"drafts\":[$record,{\"id\":"
        val salvaged = PartialDraftStorageRecovery.recover(Json, receipts)
        assertEquals(listOf(draft), salvaged.drafts)
        assertEquals(setOf("receipt"), salvaged.appliedRecoveries)
    }

    @Test
    fun `settled draft deletion receipts survive partial storage salvage`() {
        val damaged = "{\"drafts\":[],\"localDeletionIds\":[\"deleted-id\"],"
        assertEquals(setOf("deleted-id"), PartialDraftStorageRecovery.recover(Json, damaged).localDeletionIds)
    }
}
