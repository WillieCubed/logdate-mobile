package app.logdate.wear.e2e

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.streak.FirePhase
import app.logdate.wear.R
import app.logdate.wear.complication.CampfireComplicationContent
import app.logdate.wear.complication.StreakComplicationService
import app.logdate.wear.complication.campfireComplicationData
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Builds the campfire complication's data on a Wear OS emulator, where complication text can be read back. */
@RunWith(AndroidJUnit4::class)
class CampfireComplicationDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val tap = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
    private val burning = CampfireComplicationContent(iconRes = R.drawable.ic_campfire_flame, runDays = 12, phase = FirePhase.BURNING)

    @Test
    fun a_ranged_slot_shows_the_day_count_on_the_burning_step() {
        val data = campfireComplicationData(context, burning, ComplicationType.RANGED_VALUE, tap) as RangedValueComplicationData

        assertEquals(2f, data.value)
        assertEquals(0f, data.min)
        assertEquals(2f, data.max)
        assertEquals(listOf(0xFFA8A29C.toInt(), 0xFFFF8A3D.toInt(), 0xFFFFD166.toInt()), data.colorRamp?.colors?.toList())
        assertEquals(false, data.colorRamp?.interpolated)
        assertEquals("12", data.text?.read())
        assertEquals(tap, data.tapAction)
    }

    @Test
    fun a_ranged_slot_without_a_fire_is_empty_and_shows_a_dash() {
        val out = CampfireComplicationContent(iconRes = R.drawable.ic_campfire_logs, runDays = null, phase = FirePhase.OUT)

        val data = campfireComplicationData(context, out, ComplicationType.RANGED_VALUE, tap) as RangedValueComplicationData

        assertEquals(0f, data.value)
        assertEquals("–", data.text?.read())
        assertEquals("Fire went out", data.contentDescription?.read())
    }

    @Test
    fun short_and_long_slots_are_unchanged() {
        val short = campfireComplicationData(context, burning, ComplicationType.SHORT_TEXT, tap) as ShortTextComplicationData
        val long = campfireComplicationData(context, burning, ComplicationType.LONG_TEXT, tap) as LongTextComplicationData

        assertEquals("12", short.text.read())
        assertEquals(context.resources.getQuantityString(R.plurals.wear_complication_campfire_burning, 12, 12), long.text.read())
    }

    @Test
    fun the_streak_complication_offers_ranged_slots() {
        val info =
            context.packageManager.getServiceInfo(
                ComponentName(context, StreakComplicationService::class.java),
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )

        assertEquals("SHORT_TEXT,LONG_TEXT,RANGED_VALUE", info.metaData.getString("android.support.wearable.complications.SUPPORTED_TYPES"))
    }

    @Test
    fun a_ranged_slot_has_no_data_when_campfire_streaks_are_off() =
        runBlocking {
            val flags = KoinJavaComponent.getKoin().get<FeatureFlagStore>()
            val previous = flags.isEnabled(FeatureFlag.CAMPFIRE_STREAKS)
            try {
                flags.setEnabled(FeatureFlag.CAMPFIRE_STREAKS, false)
                val request = ComplicationRequest(1, ComplicationType.RANGED_VALUE, false)
                assertTrue(StreakComplicationService().onComplicationRequest(request) is NoDataComplicationData)
            } finally {
                flags.setEnabled(FeatureFlag.CAMPFIRE_STREAKS, previous)
            }
        }

    private fun ComplicationText.read(): String = getTextAt(context.resources, Instant.now()).toString()
}
