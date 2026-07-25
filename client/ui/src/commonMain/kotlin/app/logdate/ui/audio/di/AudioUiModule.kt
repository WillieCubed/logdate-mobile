package app.logdate.ui.audio.di

import org.koin.core.module.Module

/**
 * Provides UI-layer audio helpers (currently [app.logdate.ui.audio.WaveformStorage])
 * that are shared across feature modules — anything that renders a waveform, not just
 * the editor that records them.
 */
expect val audioUiModule: Module
