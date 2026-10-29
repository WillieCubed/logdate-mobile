package app.logdate.client.data.restore

import app.logdate.client.device.restore.NativeIncomingJournal
import java.io.File

/** Wake-up only: source receipts, inventory, current references and CAS still authorize each import. */
internal fun shouldReconcileRetainedMedia(
    candidates: List<Map<String, File>>,
    verifiedCustody: List<NativeIncomingJournal.Payload>,
): Boolean =
    candidates.any { files ->
        files.keys.any { path ->
            listOf("files/media/", "files/user_media/", "files/audio_notes/").any(path::startsWith)
        }
    } ||
        verifiedCustody.any { payload ->
            payload.complete &&
                listOf("files/audio_notes/", "files/user_media/", "files/media/staging/").any(payload.path::startsWith)
        }
