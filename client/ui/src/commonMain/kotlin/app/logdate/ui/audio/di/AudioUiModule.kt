package app.logdate.ui.audio.di

import org.koin.core.module.Module

/**
 * Provides UI-layer audio helpers shared by everything that renders a waveform, rather than
 * only by the editor that records them: [app.logdate.ui.audio.WaveformStorage], the platform
 * [app.logdate.ui.audio.extraction.AmplitudeExtractor], and the
 * [app.logdate.ui.audio.AudioContextProcessor] that turns an audio file into amplitudes,
 * segments and a palette.
 */
expect val audioUiModule: Module
