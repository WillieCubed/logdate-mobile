package app.logdate.client.data.di

import org.koin.core.module.Module

/**
 * Module for the data layer.
 *
 * This module exposes implementations for repository interfaces using platform-specific
 * implementations.
 */
expect val dataModule: Module