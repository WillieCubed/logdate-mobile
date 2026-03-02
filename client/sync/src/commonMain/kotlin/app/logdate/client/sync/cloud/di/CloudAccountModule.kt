package app.logdate.client.sync.cloud.di

import org.koin.core.module.Module

/**
 * Koin module for cloud account operations.
 *
 * This module provides the CloudApiClient, CloudAccountRepository,
 * and platform-specific implementations through expect/actual declarations.
 */
expect val cloudAccountModule: Module
