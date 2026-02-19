package app.logdate.shared.model.backup

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Metadata stored in the clear (or just inner-encrypted) header of the backup file.
 * 
 * This manifest allows the app (or a standalone CLI tool) to validate the backup file's 
 * integrity and compatibility before attempting decryption.
 *
 * @property version The schema version of the backup format.
 * @property timestamp The creation time of the backup.
 * @property deviceId Identifies the device that generated this backup.
 * @property userId Identifies the owner of the backup.
 * @property encryption Cryptographic parameters required for the decryption session.
 */
@Serializable
data class BackupManifest(
    val version: Int = 1,
    val timestamp: Instant,
    val deviceId: String,
    val userId: String,
    val encryption: BackupEncryptionMetadata
)

/**
 * Technical parameters required to initialize the cryptographic session.
 *
 * @property algorithm The symmetric algorithm used (e.g., "AES/GCM/NoPadding").
 * @property kdf The function used to derive the key from the mnemonic (e.g., "PBKDF2").
 * @property salt Base64-encoded random salt for the KDF.
 * @property iv Base64-encoded initialization vector for the cipher.
 * @property iterations Cost parameter for the KDF to resist brute-force.
 */
@Serializable
data class BackupEncryptionMetadata(
    val algorithm: String = "AES/GCM/NoPadding",
    val kdf: String = "PBKDF2",
    val salt: String, 
    val iv: String,   
    val iterations: Int = 100_000
)
