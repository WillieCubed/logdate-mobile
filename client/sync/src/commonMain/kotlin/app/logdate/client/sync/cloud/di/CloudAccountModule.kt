package app.logdate.client.sync.cloud.di

import app.logdate.shared.model.CloudAccountRepository
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.account.PlatformInfoProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for cloud account operations.
 * 
 * This module provides the CloudApiClient, CloudAccountRepository,
 * and platform-specific implementations through expect/actual declarations.
 */
expect val cloudAccountModule: Module