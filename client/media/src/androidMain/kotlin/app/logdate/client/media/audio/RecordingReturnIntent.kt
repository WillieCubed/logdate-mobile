package app.logdate.client.media.audio

import android.content.Context
import android.content.Intent
import kotlin.uuid.Uuid

const val NAV_SOURCE_AUDIO_RECORDING = "audio_recording"
const val EXTRA_RECORDING_DRAFT_ID = "recording_draft_id"

/** Names the durable draft, so a tap also works after its original editor window closes. */
fun recordingReturnIntent(
    context: Context,
    draftId: Uuid?,
): Intent =
    (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent().setPackage(context.packageName)).apply {
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        putExtra(EXTRA_NAV_SOURCE, NAV_SOURCE_AUDIO_RECORDING)
        draftId?.let {
            putExtra(EXTRA_RECORDING_DRAFT_ID, it.toString())
        }
    }
