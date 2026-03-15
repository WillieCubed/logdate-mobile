package app.logdate.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.logdate.wear.R

/**
 * Complication showing a microphone icon for quick voice capture.
 *
 * Tapping opens the app directly to the voice recording screen.
 */
class QuickCaptureComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SMALL_IMAGE) return null
        return createComplicationData(
            contentDescription = getString(R.string.wear_complication_quick_capture_description),
        )
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        return createComplicationData(
            contentDescription = getString(R.string.wear_complication_quick_capture_description),
            tapIntent = createVoiceCaptureIntent(),
        )
    }

    private fun createVoiceCaptureIntent(): PendingIntent {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(packageName, "app.logdate.wear.presentation.MainActivity")
            putExtra("tile_route", "voice_note")
        }
        return PendingIntent.getActivity(
            this,
            QUICK_CAPTURE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createComplicationData(
        contentDescription: String,
        tapIntent: PendingIntent? = null,
    ): SmallImageComplicationData {
        val icon = Icon.createWithResource(this, R.drawable.ic_mic_complication)
        val builder = SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(icon, SmallImageType.ICON).build(),
            contentDescription = PlainComplicationText.Builder(contentDescription).build(),
        )
        if (tapIntent != null) {
            builder.setTapAction(tapIntent)
        }
        return builder.build()
    }

    companion object {
        private const val QUICK_CAPTURE_REQUEST_CODE = 1003
    }
}
