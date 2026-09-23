package app.logdate.client.device.identity

import platform.UIKit.UIDevice

/**
 * The name iOS reports for this device. Without the user-assigned-name entitlement this is the
 * model ("iPhone", "iPad"), which still tells a person's phone apart from their other devices.
 */
fun userVisibleDeviceName(): String = UIDevice.currentDevice.name
