package app.logdate.di

import app.logdate.client.editor.EditorManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module for windowing-related dependencies.
 */
val windowingModule =
    module {
        single { EditorManager(androidContext()) }
    }
