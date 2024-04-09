package app.logdate.core.billing

import app.logdate.core.billing.model.BackupPlanOption
import kotlinx.coroutines.flow.Flow

/**
 * Interface for handling LogDate subscriptions and in-app billing.
 */
interface SubscriptionBiller {

    /**
     * True if the user is subscribed to a backup plan.
     */
    val isSubscribed: Flow<Boolean>

    /**
     * Get all subscription plans.
     */
    val availablePlans: Flow<List<BackupPlanOption>>

    /**
     * Purchase a backup plan.
     */
    suspend fun purchasePlan(plan: BackupPlanOption)

    /**
     * Cancels the currently subscribed subscription plan.
     *
     * If there is no subscription plan, this is a no-op.
     */
    suspend fun cancelPlan()
}
