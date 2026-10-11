package app.logdate.wear.e2e

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.LongTextComplicationData
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.complication.OnThisDayComplicationService
import app.logdate.wear.complication.onThisDayComplicationData
import app.logdate.wear.launch.WearLaunchIntents
import app.logdate.wear.v2.memories.OnThisDay
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Builds the On this day complication's data on a Wear OS emulator, where complication text can be read back. */
@RunWith(AndroidJUnit4::class)
class OnThisDayComplicationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val note = JournalNote.Text(creationTimestamp = NOON, lastUpdated = NOON, content = "Breakfast by the window")
    private val day = OnThisDay(date = LocalDate(2024, 10, 10), yearsAgo = 2, leadMoment = note, momentCount = 1, summary = "")

    @Test
    fun the_slot_shows_how_long_ago_and_opens_the_moment() {
        val tap = WearLaunchIntents.moment(context, note.uid)

        val data = onThisDayComplicationData(context, day, tap) as LongTextComplicationData

        assertEquals("2 years ago", data.title?.read())
        assertEquals("Breakfast by the window", data.text.read())
        assertEquals("On this day, 2 years ago: Breakfast by the window", data.contentDescription?.read())
        assertEquals(tap, data.tapAction)
    }

    @Test
    fun the_complication_is_off_until_the_new_app_turns_it_on() {
        val info =
            context.packageManager.getServiceInfo(
                ComponentName(context, OnThisDayComplicationService::class.java),
                PackageManager.ComponentInfoFlags.of((PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()),
            )

        assertEquals(false, info.enabled)
        assertEquals("LONG_TEXT", info.metaData.getString("android.support.wearable.complications.SUPPORTED_TYPES"))
    }

    private fun ComplicationText.read(): String = getTextAt(context.resources, java.time.Instant.now()).toString()

    private companion object {
        val NOON: Instant = Instant.fromEpochMilliseconds(1_760_097_600_000)
    }
}
