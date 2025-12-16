package app.logdate.shared.model

import kotlinx.serialization.Serializable

/**
 * Represents storage quota usage information for a user.
 */
@Serializable
data class QuotaUsage(
    /**
     * Total storage quota allocated to the user in bytes.
     */
    val totalBytes: Long,
    
    /**
     * Total storage used by the user in bytes.
     */
    val usedBytes: Long,
    
    /**
     * Breakdown of usage by content category.
     */
    val categories: List<QuotaCategoryUsage>,
    
    /**
     * Whether the user has exceeded their quota.
     */
    val isOverQuota: Boolean = usedBytes > totalBytes,
    
    /**
     * Percentage of quota used (0.0 to 1.0+).
     */
    val usagePercentage: Double = if (totalBytes > 0) usedBytes.toDouble() / totalBytes.toDouble() else 0.0
)

/**
 * Storage usage for a specific content category.
 */
@Serializable
data class QuotaCategoryUsage(
    /**
     * The type of content this usage represents.
     */
    val category: QuotaContentType,
    
    /**
     * Total bytes used by this category.
     */
    val sizeBytes: Long,
    
    /**
     * Number of objects in this category.
     */
    val objectCount: Int
)

/**
 * Types of content that count toward storage quota.
 */
@Serializable
enum class QuotaContentType {
    TEXT_NOTES,
    IMAGE_NOTES,
    VIDEO_NOTES,
    VOICE_NOTES,
    JOURNAL_DATA,
    USER_PROFILE,
    ATTACHMENTS
}