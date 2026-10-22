package app.logdate.client.feature.widgets

import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class WidgetSetupRequestTest {
    @Test
    fun `saved pending choices retain each configuration without copying journal content`() {
        val requests =
            listOf(
                WidgetSetupRequest.pinned(7, Uuid.parse("00000000-0000-0000-0000-000000000123")),
                WidgetSetupRequest.memories(8, RecallMode.REDISCOVER, setOf(WidgetContentType.TEXT, WidgetContentType.AUDIO)),
                WidgetSetupRequest.photoPrompt(9, PhotoWidgetShape.CIRCLE),
            )
        for (request in requests) {
            assertEquals(request, WidgetSetupRequest.decodeFor(request.encode(), request.widgetId, request.target))
        }
    }

    @Test
    fun `restored choices cannot configure a different widget or provider`() {
        val request = WidgetSetupRequest.photoPrompt(7, PhotoWidgetShape.SOFT)
        assertNull(WidgetSetupRequest.decodeFor(request.encode(), 8, request.target))
        assertNull(WidgetSetupRequest.decodeFor(request.encode(), 7, WidgetSetupTarget.MEMORIES))
    }

    @Test
    fun `malformed unknown or incomplete saved choices cannot silently replace a selection`() {
        val requests =
            listOf(
                WidgetSetupRequest.pinned(0, Uuid.parse("00000000-0000-0000-0000-000000000123")),
                WidgetSetupRequest(7, WidgetSetupTarget.PINNED_MEMORY, WidgetSettingValues(strings = mapOf("chosenNoteId" to "invalid"))),
                WidgetSetupRequest.memories(7, RecallMode.ON_THIS_DAY, emptySet()),
                WidgetSetupRequest(7, WidgetSetupTarget.PHOTO_PROMPT, WidgetSettingValues(strings = mapOf("photoShape" to "unknown"))),
                WidgetSetupRequest.photoPrompt(7, PhotoWidgetShape.SOFT).copy(version = 99),
                WidgetSetupRequest.photoPrompt(7, PhotoWidgetShape.SOFT).copy(refreshEventId = "../invalid"),
            )
        for (request in requests) {
            assertNull(WidgetSetupRequest.decodeFor(request.encode(), request.widgetId, request.target))
        }
        assertNull(WidgetSetupRequest.decodeFor("{", 7, WidgetSetupTarget.PHOTO_PROMPT))
        assertNull(WidgetSetupRequest.decodeFor("x".repeat(4_097), 7, WidgetSetupTarget.PHOTO_PROMPT))
    }
}
