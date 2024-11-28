package app.logdate.client.billing

import app.logdate.client.billing.model.LogDateBackupPlanOption
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
    val availablePlans: Flow<List<LogDateBackupPlanOption>>

    /**
     * Purchase a backup plan.
     */
    suspend fun purchasePlan(plan: LogDateBackupPlanOption)

    /**
     * Cancels the currently subscribed subscription plan.
     *
     * If there is no subscription plan, this is a no-op.
     */
    suspend fun cancelPlan()
}
