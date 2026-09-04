package app.logdate.ui.audio

import androidx.compose.runtime.compositionLocalOf

/**
 * The [AudioContextProcessor] audio surfaces use to obtain a recording's amplitudes.
 *
 * Supplied as a composition local rather than injected inside each card. Audio cards render in
 * previews, screenshot tests and other contexts with no Koin graph, and an injection failure
 * there takes down the whole surrounding surface. Absent a processor the card still draws and
 * still plays; it simply has no waveform to show.
 */
val LocalAudioContextProcessor = compositionLocalOf<AudioContextProcessor?> { null }
