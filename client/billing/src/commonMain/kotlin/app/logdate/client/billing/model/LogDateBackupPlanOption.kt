package app.logdate.client.billing.model

/**
 * A LogDate Cloud backup plan, identified by its store product [sku].
 *
 * The plan's name, description and price are words for the screen, so the UI that offers plans
 * maps each option to its own string resources.
 */
enum class LogDateBackupPlanOption(
    val sku: String,
) {
    BASIC(sku = "logdate_backup_plan_basic"),
    STANDARD(sku = "logdate_backup_plan_standard"),
}
