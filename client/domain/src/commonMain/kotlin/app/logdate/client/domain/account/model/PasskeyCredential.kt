package app.logdate.client.domain.account.model

import app.logdate.shared.model.PasskeyCredential as SharedPasskeyCredential
import app.logdate.shared.model.DeviceInfo as SharedDeviceInfo
import app.logdate.shared.model.DeviceType as SharedDeviceType

/**
 * Represents a passkey credential for authentication.
 *
 * @see app.logdate.shared.model.PasskeyCredential
 */
typealias PasskeyCredential = SharedPasskeyCredential

/**
 * Information about the device associated with a passkey.
 *
 * @see app.logdate.shared.model.DeviceInfo
 */
typealias DeviceInfo = SharedDeviceInfo

/**
 * The type of device a passkey is associated with.
 *
 * @see app.logdate.shared.model.DeviceType
 */
typealias DeviceType = SharedDeviceType