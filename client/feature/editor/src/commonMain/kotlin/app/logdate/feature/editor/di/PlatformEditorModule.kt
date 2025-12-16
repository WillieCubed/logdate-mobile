package app.logdate.feature.editor.di

import org.koin.core.module.Module

/**
 * Platform-specific module definition for the editor feature.
 * 
 * This module is expected to provide implementations for:
 * - AudioRecordingManager
 * - ImagePickerService
 */
expect val platformEditorModule: Module