package app.logdate.client.billing

import app.logdate.client.billing.model.LogDateBackupPlanOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// TODO: Move to debug build variant, remove from release
/**
 * A fake implementation of [SubscriptionBiller] for testing purposes.
 */
class StubSubscriptionBiller : SubscriptionBiller {

    private var currentPlan: LogDateBackupPlanOption = LogDateBackupPlanOption.BASIC

    override suspend fun purchasePlan(plan: LogDateBackupPlanOption) {
        currentPlan = plan
    }

    override suspend fun cancelPlan() {
        currentPlan = LogDateBackupPlanOption.BASIC
    }

    override val isSubscribed: Flow<Boolean>
        get() = flow { emit(currentPlan != LogDateBackupPlanOption.BASIC) }

    override val availablePlans: Flow<List<LogDateBackupPlanOption>> = flow {
        emit(
            listOf(
                LogDateBackupPlanOption.BASIC,
                LogDateBackupPlanOption.STANDARD,
            )
        )
    }
}