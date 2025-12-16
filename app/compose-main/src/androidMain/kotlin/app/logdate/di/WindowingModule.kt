package app.logdate.di

import android.content.Context
import app.logdate.navigation.EditorManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module for windowing-related dependencies.
 */
val windowingModule = module {
    // Editor manager for multi-window support
    single { EditorManager(androidContext()) }
}