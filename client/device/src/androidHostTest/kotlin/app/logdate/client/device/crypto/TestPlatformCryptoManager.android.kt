package app.logdate.client.device.crypto

internal actual fun testPlatformCryptoManager(): CryptoManager = AndroidCryptoManager()
