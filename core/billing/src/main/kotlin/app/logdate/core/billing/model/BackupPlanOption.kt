package app.logdate.core.billing.model

enum class BackupPlanOption(
    val sku: String,
    val title: String,
    val description: String,
    val price: String,
) {
    BASIC(
        sku = "logdate_backup_plan_basic",
        title = "Basic",
        description = "Max 10 GB of text, photos, videos, and voice notes. Photos and videos will be compressed in high quality (1080p).",
        price = "Free",
    ),
    STANDARD(
        sku = "logdate_backup_plan_standard",
        title = "Standard",
        description = "Includes up to 1 TB of storage for text, photo, video, and voice notes.\n" +
                "Photos and videos will be stored in original quality.",
        price = "$5/month",
    ),
}