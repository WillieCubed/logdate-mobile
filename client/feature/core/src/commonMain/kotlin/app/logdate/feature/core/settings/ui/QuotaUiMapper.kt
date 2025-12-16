package app.logdate.feature.core.settings.ui

import androidx.compose.ui.graphics.Color
import app.logdate.shared.model.CloudStorageQuota
import app.logdate.shared.model.CloudStorageCategoryUsage

/**
 * UI state representation of quota usage for display in settings.
 */
data class StorageQuotaUi(
    val totalBytes: Long,
    val usedBytes: Long,
    val usagePercentage: Float,
    val categories: List<StorageCategory> = emptyList(),
    val formattedTotal: String,
    val formattedUsed: String,
    val isOverQuota: Boolean = false,
    // Derived properties for easier use in UI
    val usedGB: Float = usedBytes / (1024f * 1024f * 1024f),
    val totalGB: Float = totalBytes / (1024f * 1024f * 1024f)
)

/**
 * UI state representation of a storage category (e.g., images, text, etc.)
 */
data class StorageCategory(
    val name: String,
    val usedBytes: Long,
    val usagePercentage: Float,
    val color: Color,
    val formattedUsed: String,
    // Derived properties for easier use in UI
    val sizeInMB: Float = usedBytes / (1024f * 1024f)
)

/**
 * Converts domain [CloudStorageQuota] data to UI state for display.
 */
fun CloudStorageQuota.toStorageQuotaUi(): StorageQuotaUi {
    val usagePercentage = if (totalBytes > 0) {
        (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    
    return StorageQuotaUi(
        totalBytes = totalBytes,
        usedBytes = usedBytes,
        usagePercentage = usagePercentage,
        categories = categories.map { category ->
            StorageCategory(
                name = category.category.name,
                usedBytes = category.sizeBytes,
                usagePercentage = if (usedBytes > 0) category.sizeBytes.toFloat() / usedBytes.toFloat() else 0f,
                color = getCategoryColor(category.category.name),
                formattedUsed = formatByteSize(category.sizeBytes)
            )
        },
        formattedTotal = formatByteSize(totalBytes),
        formattedUsed = formatByteSize(usedBytes),
        isOverQuota = usedBytes > totalBytes
    )
}

// This function has been replaced by CloudStorageQuota.toStorageQuotaUi()

/**
 * Returns a color for the given quota category name.
 */
private fun getCategoryColor(categoryName: String): Color {
    return when (categoryName.uppercase()) {
        "IMAGE_NOTES" -> Color(0xFF4CAF50) // Green
        "TEXT_NOTES" -> Color(0xFF2196F3)   // Blue
        "VIDEO_NOTES" -> Color(0xFFF44336) // Red
        "VOICE_NOTES" -> Color(0xFFFF9800) // Orange
        "JOURNAL_DATA" -> Color(0xFF9C27B0) // Purple
        "ATTACHMENTS" -> Color(0xFF607D8B) // Blue Gray
        "USER_PROFILE" -> Color(0xFF795548) // Brown
        else -> Color(0xFF9E9E9E)    // Gray
    }
}

// Removed duplicate method: CloudStorageQuota?.orDefault() is defined in SettingsUiState.kt

/**
 * Maps a CloudStorageQuota to a QuotaUsageUi model for the DataSettingsScreen.
 */
fun CloudStorageQuota.toQuotaUsageUi(): StorageQuotaUi {
    return orDefault().toStorageQuotaUi()
}

/**
 * Formats byte size to human-readable string (e.g., "1.5 GB").
 */
fun formatByteSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    
    return when {
        unitIndex == 0 -> "$bytes ${units[unitIndex]}"
        size < 10 -> "%.1f ${units[unitIndex]}".format(size)
        else -> "%.0f ${units[unitIndex]}".format(size)
    }
}