package app.logdate.client.media.storage

import java.io.File

/**
 * A percent-encoded `file:///` URI for the local file [reference] names, or `null` when it names
 * no local file. Built without `android.net.Uri`, so it also works in JVM unit tests.
 */
fun MediaFileResolver.fileUri(reference: String): String? = filePath(reference)?.let { path -> "file://${File(path).toURI().rawPath}" }

/**
 * A URI a player or decoder can open for [reference]: a `file:///` URI for local media, or
 * [reference] unchanged (a content provider or remote stream) when it names no local file.
 */
fun MediaFileResolver.playableUri(reference: String): String = fileUri(reference) ?: reference
