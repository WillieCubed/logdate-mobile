package app.logdate.client.media.storage

import platform.Foundation.NSURL

/** A file URL for the local file [reference] names, or `null` when it names no local file. */
fun MediaFileResolver.fileUrl(reference: String): NSURL? = filePath(reference)?.let { NSURL.fileURLWithPath(it) }

/**
 * A URL AVFoundation can open for [reference]: a file URL for local media, or [reference] itself
 * as a URL (a remote stream) when it names no local file.
 */
fun MediaFileResolver.playableUrl(reference: String): NSURL? = fileUrl(reference) ?: NSURL.URLWithString(reference)
