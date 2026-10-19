package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceMediaReference
import kotlin.uuid.Uuid

private const val SAMPLE_CID = "bafkreifzjut3te2nhyekklss27nh3k72ysco7y32koao5eei66wof36n5e"

/** A content identifier for an uploaded file in tests; each [marker] letter gives a different valid one. */
internal fun cidWith(marker: Char): String = SAMPLE_CID.replaceRange(20, 21, marker.toString())

/** The reference a journal or draft keeps for a file the transport has uploaded to the personal space. */
internal fun uploadedFile(
    contentId: Uuid,
    marker: Char,
    mimeType: String,
    size: Long? = 61_204,
): String = SpaceMediaReference(cidWith(marker), contentId, mimeType, storedSize = 65_536, size = size).format()
