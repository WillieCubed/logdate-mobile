package app.logdate.client.domain.moments

import app.logdate.client.media.MediaObject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal fun selectMarkPhotos(
    at: Instant,
    media: List<MediaObject>,
): List<MediaObject.Image> =
    media
        .filterIsInstance<MediaObject.Image>()
        .filter { (it.timestamp - at).absoluteValue <= 15.minutes }
        .distinctBy { it.uri }
        .sortedWith(compareBy<MediaObject.Image> { (it.timestamp - at).absoluteValue }.thenBy { it.timestamp })
