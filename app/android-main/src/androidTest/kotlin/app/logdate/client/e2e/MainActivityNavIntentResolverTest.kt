package app.logdate.client.e2e

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_DRAFT
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_EVENT_DETAIL
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_MEMORY_RECALL
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_NEW_ENTRY
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_DRAFT_ID
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_EVENT_ID
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_RECALL_DATE
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_TARGET
import app.logdate.client.location.tracking.EXTRA_NAV_SOURCE as EXTRA_LOCATION_NAV_SOURCE
import app.logdate.client.location.tracking.NAV_SOURCE_LOCATION_HISTORY
import app.logdate.client.media.audio.EXTRA_NAV_SOURCE as EXTRA_AUDIO_NAV_SOURCE
import app.logdate.client.media.audio.EXTRA_NOTE_ID
import app.logdate.client.media.audio.NAV_SOURCE_AUDIO_PLAYBACK
import app.logdate.client.rewind.EXTRA_REWIND_NOTIFICATION_ID
import app.logdate.client.rewind.EXTRA_REWIND_NOTIFICATION_TARGET
import app.logdate.client.rewind.REWIND_NOTIFICATION_TARGET_DETAIL
import app.logdate.client.resolveMainActivityNavKey
import app.logdate.feature.core.notifications.EXTRA_NAV_SOURCE as EXTRA_DATA_TRANSFER_NAV_SOURCE
import app.logdate.feature.core.notifications.NAV_SOURCE_DATA_TRANSFER
import app.logdate.feature.core.export.ExportNotificationHelper
import app.logdate.feature.core.settings.navigation.ExportSettingsRoute
import app.logdate.feature.core.restore.RestoreNotificationHelper
import app.logdate.feature.core.restore.RestoreStage
import app.logdate.client.ui.navigation.LocationTimelineRoute
import app.logdate.client.ui.navigation.SearchRoute
import app.logdate.feature.events.navigation.EventDetailRoute
import app.logdate.feature.journals.navigation.JournalDetailsRoute
import app.logdate.feature.journals.navigation.NoteDetailRoute
import app.logdate.feature.rewind.navigation.RewindDetailRoute
import app.logdate.navigation.TimelineDetailRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNavIntentResolverTest {
    @Test
    fun customSchemeJournalIntent_resolvesJournalDetailsRoute() {
        val journalId = Uuid.random()

        assertEquals(
            JournalDetailsRoute(journalId),
            resolveMainActivityNavKey(
                Intent(Intent.ACTION_VIEW, Uri.parse("logdate://journal/$journalId")),
            ),
        )
    }

    @Test
    fun customSchemeSearchIntent_resolvesSearchRoute() {
        assertEquals(
            SearchRoute(
                query = "lanterns",
                typeFtsValues = listOf("text_note", "media_caption"),
                dateRangeName = "Today",
            ),
            resolveMainActivityNavKey(
                Intent(Intent.ACTION_VIEW, Uri.parse("logdate://search?q=lanterns&type=text_note,media_caption&date=Today")),
            ),
        )
    }

    @Test
    fun webDayIntent_resolvesTimelineDetailRoute() {
        val date = "2026-06-15"

        assertEquals(
            TimelineDetailRoute(date),
            resolveMainActivityNavKey(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://logdate.app/day/$date")),
            ),
        )
    }

    @Test
    fun customSchemeLocationIntent_resolvesLocationTimelineRoute() {
        assertEquals(
            LocationTimelineRoute,
            resolveMainActivityNavKey(
                Intent(Intent.ACTION_VIEW, Uri.parse("logdate://location")),
            ),
        )
    }

    @Test
    fun ambientPromptNewEntryIntent_resolvesEntryEditorRoute() {
        assertEquals(
            app.logdate.feature.editor.navigation.EntryEditorRoute(),
            resolveMainActivityNavKey(
                Intent().apply {
                    putExtra(EXTRA_AMBIENT_PROMPT_TARGET, AMBIENT_PROMPT_TARGET_NEW_ENTRY)
                },
            ),
        )
    }

    @Test
    fun ambientPromptDraftIntent_resolvesEntryEditorRouteWithDraftId() {
        val draftId = Uuid.random().toString()

        assertEquals(
            app.logdate.feature.editor.navigation.EntryEditorRoute(draftId = draftId),
            resolveMainActivityNavKey(
                Intent().apply {
                    putExtra(EXTRA_AMBIENT_PROMPT_TARGET, AMBIENT_PROMPT_TARGET_DRAFT)
                    putExtra(EXTRA_AMBIENT_PROMPT_DRAFT_ID, draftId)
                },
            ),
        )
    }

    @Test
    fun ambientPromptMemoryRecallIntent_resolvesTimelineDetailRoute() {
        val date = "2026-06-15"

        assertEquals(
            TimelineDetailRoute(date),
            resolveMainActivityNavKey(
                Intent().apply {
                    putExtra(EXTRA_AMBIENT_PROMPT_TARGET, AMBIENT_PROMPT_TARGET_MEMORY_RECALL)
                    putExtra(EXTRA_AMBIENT_PROMPT_RECALL_DATE, date)
                },
            ),
        )
    }

    @Test
    fun ambientPromptEventIntent_resolvesEventDetailRoute() {
        val eventId = "event-123"

        assertEquals(
            EventDetailRoute(eventId),
            resolveMainActivityNavKey(
                Intent().apply {
                    putExtra(EXTRA_AMBIENT_PROMPT_TARGET, AMBIENT_PROMPT_TARGET_EVENT_DETAIL)
                    putExtra(EXTRA_AMBIENT_PROMPT_EVENT_ID, eventId)
                },
            ),
        )
    }

    @Test
    fun audioPlaybackNotificationIntent_resolvesNoteDetailRoute() {
        val noteId = Uuid.random().toString()

        assertEquals(
            NoteDetailRoute(Uuid.parse(noteId)),
            resolveMainActivityNavKey(
                Intent().apply {
                    putExtra(EXTRA_AUDIO_NAV_SOURCE, NAV_SOURCE_AUDIO_PLAYBACK)
                    putExtra(EXTRA_NOTE_ID, noteId)
                },
            ),
        )
    }

    @Test
    fun locationHistoryNotificationIntent_resolvesLocationTimelineRoute() {
        assertEquals(
            LocationTimelineRoute,
            resolveMainActivityNavKey(
                Intent().apply {
                    putExtra(EXTRA_LOCATION_NAV_SOURCE, NAV_SOURCE_LOCATION_HISTORY)
                },
            ),
        )
    }

    @Test
    fun rewindNotificationIntent_resolvesRewindDetailRoute() {
        val rewindId = Uuid.random()

        assertEquals(
            RewindDetailRoute(rewindId),
            resolveMainActivityNavKey(
                Intent().apply {
                    putExtra(EXTRA_REWIND_NOTIFICATION_TARGET, REWIND_NOTIFICATION_TARGET_DETAIL)
                    putExtra(EXTRA_REWIND_NOTIFICATION_ID, rewindId.toString())
                },
            ),
        )
    }

    @Test
    fun dataTransferNotificationIntent_resolvesExportSettingsRoute() {
        assertEquals(
            ExportSettingsRoute,
            resolveMainActivityNavKey(
                Intent().apply {
                    putExtra(EXTRA_DATA_TRANSFER_NAV_SOURCE, NAV_SOURCE_DATA_TRANSFER)
                },
            ),
        )
    }

    @Test
    fun dataTransferNotificationHelpers_includeLaunchIntents() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertNotNull(
            ExportNotificationHelper(context, Uuid.random().toJavaUuid())
                .createForegroundInfo(10, "Exporting")
                .notification
                .contentIntent,
        )
        assertNotNull(
            RestoreNotificationHelper(context, Uuid.random().toJavaUuid())
                .createForegroundInfo(RestoreStage.PREPARING)
                .notification
                .contentIntent,
        )
    }

    @Test
    fun unrelatedIntent_returnsNull() {
        assertNull(resolveMainActivityNavKey(Intent()))
        assertNull(resolveMainActivityNavKey(Intent(Intent.ACTION_MAIN)))
        assertNull(resolveMainActivityNavKey(Intent().apply { putExtra("something", "else") }))
        assertNull(resolveMainActivityNavKey(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))))
        assertNull(resolveMainActivityNavKey(Intent(Intent.ACTION_VIEW, Uri.parse("logdate://unknown/path"))))
        assertNull(resolveMainActivityNavKey(Intent().apply { putExtra(EXTRA_REWIND_NOTIFICATION_TARGET, "not_rewind_detail") }))
    }
}
