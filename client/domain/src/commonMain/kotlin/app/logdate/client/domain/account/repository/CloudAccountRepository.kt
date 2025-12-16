package app.logdate.client.domain.account.repository

import app.logdate.shared.model.CloudAccountRepository as SharedCloudAccountRepository

/**
 * Repository interface for managing cloud account operations.
 * 
 * This is a wrapper around the shared model interface to maintain backward compatibility.
 * All implementations should now use the shared model interface.
 *
 * @see app.logdate.shared.model.CloudAccountRepository
 */
typealias CloudAccountRepository = SharedCloudAccountRepository

/**
 * Result of beginning account creation.
 * 
 * @see app.logdate.shared.model.BeginAccountCreationResult
 */
typealias BeginAccountCreationResult = app.logdate.shared.model.BeginAccountCreationResult