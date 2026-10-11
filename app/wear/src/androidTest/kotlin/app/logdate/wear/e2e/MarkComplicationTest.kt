package app.logdate.wear.e2e

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.logdate.wear.complication.MarkComplicationService
import app.logdate.wear.complication.markComplicationData
import app.logdate.wear.v2.mark.MarkMomentReceiver
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Builds the Mark complication's data on a Wear OS emulator, where complication text can be read back. */
@RunWith(AndroidJUnit4::class)
class MarkComplicationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mark = MarkMomentReceiver.pendingIntent(context)

    @Test
    fun every_offered_slot_marks_through_the_receiver() {
        listOf(ComplicationType.SHORT_TEXT, ComplicationType.MONOCHROMATIC_IMAGE, ComplicationType.SMALL_IMAGE).forEach { type ->
            val data = requireNotNull(markComplicationData(context, type, mark)) { "No data for $type" }

            assertEquals(type, data.type)
            assertEquals(mark, data.tapAction)
        }
    }

    @Test
    fun the_short_slot_says_mark() {
        val data = markComplicationData(context, ComplicationType.SHORT_TEXT, mark) as ShortTextComplicationData

        assertEquals("Mark", data.text.read())
        assertEquals("Mark this moment", data.contentDescription?.read())
    }

    @Test
    fun a_slot_it_does_not_fill_gets_nothing() {
        assertNull(markComplicationData(context, ComplicationType.LONG_TEXT, mark))
    }

    @Test
    fun the_manifest_offers_those_slots_and_starts_off() {
        val info =
            context.packageManager.getServiceInfo(
                ComponentName(context, MarkComplicationService::class.java),
                PackageManager.ComponentInfoFlags.of((PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()),
            )

        assertEquals(false, info.enabled)
        assertEquals(
            "SHORT_TEXT,MONOCHROMATIC_IMAGE,SMALL_IMAGE",
            info.metaData.getString("android.support.wearable.complications.SUPPORTED_TYPES"),
        )
    }

    private fun ComplicationText.read(): String = getTextAt(context.resources, Instant.now()).toString()
}
