package app.logdate.client.domain.account.model

import app.logdate.shared.model.CloudAccount as SharedCloudAccount
import app.logdate.shared.model.AccountCredentials as SharedAccountCredentials
import app.logdate.shared.model.AuthenticationResult as SharedAuthenticationResult

/**
 * Represents a user's LogDate Cloud account.
 *
 * This is a wrapper around the shared model for backward compatibility.
 * 
 * @see app.logdate.shared.model.CloudAccount
 */
typealias CloudAccount = SharedCloudAccount

/**
 * Represents the authentication credentials for a cloud account.
 *
 * @see app.logdate.shared.model.AccountCredentials
 */
typealias AccountCredentials = SharedAccountCredentials

/**
 * Represents the result of an authentication operation.
 *
 * @see app.logdate.shared.model.AuthenticationResult
 */
typealias AuthenticationResult = SharedAuthenticationResult